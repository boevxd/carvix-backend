const express = require('express');
const { Pool } = require('pg');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const app = express();

// Безопасность HTTP-заголовков
app.use(helmet({
  // отключаем CSP — API не отдаёт HTML
  contentSecurityPolicy: false,
  crossOriginResourcePolicy: { policy: 'cross-origin' }
}));

app.use(cors());
app.use(express.json({ limit: '1mb' }));

// Глобальный rate-limit
const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 300,
  standardHeaders: true,
  legacyHeaders: false
});
app.use(globalLimiter);

// Жёсткий лимит для логина/регистрации (защита от brute-force)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Слишком много попыток, попробуйте позже' }
});

app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
  next();
});

const DB_URL = process.env.DATABASE_URL;
if (!DB_URL) {
  console.error('FATAL: DATABASE_URL environment variable is not set.');
  process.exit(1);
}

console.log('Connecting to DB:', DB_URL.replace(/:([^@]+)@/, ':***@'));

const pool = new Pool({
  connectionString: DB_URL,
  ssl: { rejectUnauthorized: false },
  max: 5,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000,
  keepAlive: true,
  keepAliveInitialDelayMillis: 10000
});

// Прогрев: держим соединение с БД горячим, чтобы первый запрос не тормозил
setInterval(async () => {
  try { await pool.query('SELECT 1'); } catch (_) {}
}, 25 * 60 * 1000); // каждые 25 мин (Render free tier засыпает через 30 мин)

const JWT_SECRET = process.env.JWT_SECRET || 'carvix_secret_key_2024';
if (!process.env.JWT_SECRET) {
  console.warn('[WARN] JWT_SECRET не задан в env — используется значение по умолчанию (не безопасно для прода)');
}

// Roles
const ROLE_MECHANIC = 3;
const ROLE_HEAD_MECHANIC = 4;
const ROLE_ADMIN = 5;

// Statuses
const STATUS_NEW = 1;
const STATUS_IN_PROGRESS = 2;
const STATUS_DONE = 3;
const STATUS_REJECTED = 4;
const STATUS_WAIT_PARTS = 5;

// FCM configuration: читаем Service Account из Secret File (приоритет) или env-переменной.
// Render монтирует Secret Files в /etc/secrets/<filename>.
const fs = require('fs');
const path = require('path');

const FCM_SERVICE_ACCOUNT_PATH = process.env.FCM_SERVICE_ACCOUNT_PATH || '/etc/secrets/service-account.json';
const FCM_SERVICE_ACCOUNT_JSON = process.env.FCM_SERVICE_ACCOUNT_JSON || null;

let cachedServiceAccount = null;
let serviceAccountLoadAttempted = false;
let fcmAccessToken = null;
let fcmTokenExpiry = 0;

function loadServiceAccount() {
  // 1. Пробуем secret file
  try {
    if (fs.existsSync(FCM_SERVICE_ACCOUNT_PATH)) {
      const raw = fs.readFileSync(FCM_SERVICE_ACCOUNT_PATH, 'utf8');
      const sa = JSON.parse(raw);
      console.log(`[FCM] Service account loaded from ${FCM_SERVICE_ACCOUNT_PATH} (project: ${sa.project_id})`);
      return sa;
    }
  } catch (e) {
    console.error(`[FCM] Failed to read ${FCM_SERVICE_ACCOUNT_PATH}:`, e.message);
  }

  // 2. Fallback на env-переменную
  if (FCM_SERVICE_ACCOUNT_JSON) {
    try {
      const sa = JSON.parse(FCM_SERVICE_ACCOUNT_JSON);
      console.log(`[FCM] Service account loaded from env (project: ${sa.project_id})`);
      return sa;
    } catch (e) {
      console.error('[FCM] Invalid FCM_SERVICE_ACCOUNT_JSON env:', e.message);
    }
  }

  console.warn('[FCM] Service account not configured — push отключён');
  return null;
}

function getServiceAccount() {
  if (!serviceAccountLoadAttempted) {
    cachedServiceAccount = loadServiceAccount();
    serviceAccountLoadAttempted = true;
  }
  return cachedServiceAccount;
}

// Прогрев при старте, чтобы сразу увидеть в логах статус
getServiceAccount();

async function initDB() {
  try {
    // ---- Base tables (must exist before dependent tables) ----
    await pool.query(`
      CREATE TABLE IF NOT EXISTS rol (
        id SERIAL PRIMARY KEY,
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS podrazdelenie (
        id SERIAL PRIMARY KEY,
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS status (
        id SERIAL PRIMARY KEY,
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS tip_remonta (
        id SERIAL PRIMARY KEY,
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS marka (
        id SERIAL PRIMARY KEY,
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS model (
        id SERIAL PRIMARY KEY,
        marka_id INTEGER REFERENCES marka(id),
        nazvanie TEXT NOT NULL
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS transportnoe_sredstvo (
        id SERIAL PRIMARY KEY,
        model_id INTEGER REFERENCES model(id),
        podrazdelenie_id INTEGER REFERENCES podrazdelenie(id),
        gos_nomer TEXT,
        invent_nomer TEXT,
        probeg INTEGER,
        tekuschee_sostoyanie TEXT
      )
    `);
    // Migration: add podrazdelenie_id if table exists from old schema
    try {
      await pool.query(`ALTER TABLE transportnoe_sredstvo ADD COLUMN IF NOT EXISTS podrazdelenie_id INTEGER REFERENCES podrazdelenie(id)`);
      await pool.query(`UPDATE transportnoe_sredstvo SET podrazdelenie_id = 1 WHERE podrazdelenie_id IS NULL`);
    } catch (e) { /* ignore if already exists */ }
    await pool.query(`
      CREATE TABLE IF NOT EXISTS sotrudnik (
        id SERIAL PRIMARY KEY,
        fio TEXT NOT NULL,
        login TEXT UNIQUE NOT NULL,
        parol_hash TEXT,
        rol_id INTEGER REFERENCES rol(id),
        podrazdelenie_id INTEGER REFERENCES podrazdelenie(id)
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS zayavka (
        id SERIAL PRIMARY KEY,
        data_sozdaniya TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        sozdatel_id INTEGER REFERENCES sotrudnik(id),
        ts_id INTEGER REFERENCES transportnoe_sredstvo(id),
        tip_remonta_id INTEGER REFERENCES tip_remonta(id),
        opisanie TEXT,
        status_id INTEGER REFERENCES status(id),
        prioritet INTEGER,
        data_rezhima TIMESTAMP
      )
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS remont (
        id SERIAL PRIMARY KEY,
        zayavka_id INTEGER REFERENCES zayavka(id),
        data_nachala TIMESTAMP,
        data_okonchaniya TIMESTAMP,
        mekhanik_id INTEGER REFERENCES sotrudnik(id),
        glavniy_mekhanik_id INTEGER REFERENCES sotrudnik(id),
        stoimost_rabot NUMERIC DEFAULT 0,
        stoimost_zapchastey NUMERIC DEFAULT 0
      )
    `);
    console.log('base tables ready');

    // ---- Seed reference data ----
    await pool.query(`
      INSERT INTO rol (id, nazvanie) VALUES
      (1,'Администратор'), (2,'Диспетчер'), (3,'Механик'), (4,'Главный механик'), (5,'Директор')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO status (id, nazvanie) VALUES
      (1,'Новая'), (2,'В работе'), (3,'Завершено'), (4,'Отклонено'), (5,'Ожидание запчастей')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO podrazdelenie (id, nazvanie) VALUES
      (1,'Основное')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO tip_remonta (id, nazvanie) VALUES
      (1,'ТО'), (2,'Ремонт двигателя'), (3,'Ремонт ходовой'), (4,'Диагностика'), (5,'Покраска'),
      (6,'Замена масла'), (7,'Ремонт электрики'), (8,'Шиномонтаж'), (9,'Ремонт кузова'), (10,'Ремонт КПП')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO marka (id, nazvanie) VALUES
      (1,'Toyota'), (2,'KamAZ'), (3,'BMW'), (4,'Mercedes-Benz'), (5,'Volvo'),
      (6,'MAN'), (7,'Scania'), (8,'Hyundai'), (9,'Ford'), (10,'Mazda'),
      (11,'Nissan'), (12,'Volkswagen'), (13,'Kia'), (14,'Lada'), (15,'GAZ'),
      (16,'Skoda'), (17,'Renault'), (18,'Peugeot'), (19,'Citroen'), (20,'Mitsubishi')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO model (id, marka_id, nazvanie) VALUES
      (1,1,'Camry'), (2,1,'Land Cruiser'), (3,1,'Hilux'), (4,1,'Corolla'),
      (5,2,'43118'), (6,2,'6520'), (7,2,'65115'), (8,2,'5490'),
      (9,3,'X5'), (10,3,'X3'), (11,3,'320i'), (12,3,'530d'),
      (13,4,'Sprinter'), (14,4,'Actros'), (15,4,'Atego'),
      (16,5,'FH16'), (17,5,'FM'), (18,5,'FL'),
      (19,6,'TGX'), (20,6,'TGS'), (21,6,'TGL'),
      (22,7,'R500'), (23,7,'P440'), (24,7,'G410')
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO transportnoe_sredstvo (id, model_id, podrazdelenie_id, gos_nomer, invent_nomer, probeg, tekuschee_sostoyanie) VALUES
      (1,1,1,'А123БВ777','INV-001', 45200, 'Исправно'),
      (2,3,1,'А234ВГ888','INV-002', 128500, 'Требует ремонта'),
      (3,5,1,'К456НО199','INV-003', 67300, 'Исправно'),
      (4,9,1,'М789РС777','INV-004', 89100, 'Исправно'),
      (5,13,1,'О012ТУ444','INV-005', 234000, 'В ремонте'),
      (6,16,1,'А345БВ555','INV-006', 156000, 'Исправно'),
      (7,5,1,'К567НО888','INV-007', 342000, 'Требует ремонта'),
      (8,9,1,'М890РС999','INV-008', 78000, 'Исправно'),
      (9,1,1,'А111БВ222','INV-009', 12000, 'Исправно'),
      (10,3,1,'А222ВГ333','INV-010', 56700, 'Требует ремонта'),
      (11,5,1,'К333НО444','INV-011', 189000, 'Исправно'),
      (12,9,1,'М444РС555','INV-012', 23400, 'Исправно'),
      (13,13,1,'О555ТУ666','INV-013', 312000, 'В ремонте'),
      (14,16,1,'А666БВ777','INV-014', 89000, 'Исправно'),
      (15,5,1,'К777НО888','INV-015', 445000, 'Требует ремонта')
      ON CONFLICT (id) DO NOTHING
    `);
    // Seed test users with plaintext passwords (login handler auto-migrates them to bcrypt on first login)
    await pool.query(`
      INSERT INTO sotrudnik (id, fio, login, parol_hash, rol_id, podrazdelenie_id) VALUES
      (1,'Иванов А.А.','admin','admin123',5,1),
      (2,'Петров В.В.','mechanic1','mech123',3,1),
      (3,'Сидоров Г.Г.','head1','head123',4,1),
      (4,'Кузнецов Д.Д.','mechanic2','mech123',3,1),
      (5,'Смирнов Е.Е.','mechanic3','mech123',3,1),
      (6,'Васильев Ж.Ж.','mechanic4','mech123',3,1),
      (7,'Попов З.З.','mechanic5','mech123',3,1),
      (8,'Новиков И.И.','mechanic6','mech123',3,1),
      (9,'Морозов К.К.','mechanic7','mech123',3,1),
      (10,'Лебедев Л.Л.','mechanic8','mech123',3,1)
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO zayavka (id, data_sozdaniya, sozdatel_id, ts_id, tip_remonta_id, opisanie, status_id, prioritet, data_rezhima) VALUES
      (1,NOW()-INTERVAL '14 days',3,2,2,'Течь масла из двигателя, нужна диагностика',1,2,NOW()),
      (2,NOW()-INTERVAL '13 days',3,5,3,'Стук в передней подвеске',2,1,NOW()-INTERVAL '12 days'),
      (3,NOW()-INTERVAL '12 days',3,7,1,'Плановое ТО 150000 км',3,1,NOW()-INTERVAL '10 days'),
      (4,NOW()-INTERVAL '11 days',3,10,4,'Проверка электропроводки',1,3,NOW()),
      (5,NOW()-INTERVAL '10 days',3,13,2,'Перегрев двигателя на трассе',2,2,NOW()-INTERVAL '9 days'),
      (6,NOW()-INTERVAL '9 days',3,15,5,'Вмятина на правой двери',1,1,NOW()),
      (7,NOW()-INTERVAL '8 days',3,1,6,'Замена масла и фильтров',3,1,NOW()-INTERVAL '7 days'),
      (8,NOW()-INTERVAL '7 days',3,3,7,'Не работает стартер',2,3,NOW()-INTERVAL '6 days'),
      (9,NOW()-INTERVAL '6 days',3,6,8,'Замена резины на зимнюю',3,1,NOW()-INTERVAL '5 days'),
      (10,NOW()-INTERVAL '5 days',3,8,9,'Царапина на бампере',1,1,NOW()),
      (11,NOW()-INTERVAL '4 days',3,11,10,'Проблемы с переключением передач',2,2,NOW()-INTERVAL '3 days'),
      (12,NOW()-INTERVAL '3 days',3,4,2,'Дым из выхлопной трубы',1,3,NOW()),
      (13,NOW()-INTERVAL '2 days',3,9,3,'Скрип в задней подвеске',1,2,NOW()),
      (14,NOW()-INTERVAL '1 day',3,12,4,'Проверка АКБ и генератора',1,1,NOW()),
      (15,NOW()-INTERVAL '1 day',3,14,1,'ТО 90000 км',1,1,NOW())
      ON CONFLICT (id) DO NOTHING
    `);
    await pool.query(`
      INSERT INTO remont (id, zayavka_id, data_nachala, data_okonchaniya, mekhanik_id, glavniy_mekhanik_id, stoimost_rabot, stoimost_zapchastey) VALUES
      (1,2,NOW()-INTERVAL '12 days',NOW()-INTERVAL '11 days',2,3,15000,8500),
      (2,3,NOW()-INTERVAL '10 days',NOW()-INTERVAL '9 days',4,3,8000,12000),
      (3,5,NOW()-INTERVAL '9 days',NULL,5,3,25000,18000),
      (4,7,NOW()-INTERVAL '7 days',NOW()-INTERVAL '6 days',6,3,5000,3500),
      (5,8,NOW()-INTERVAL '6 days',NULL,7,3,12000,4500),
      (6,9,NOW()-INTERVAL '5 days',NOW()-INTERVAL '4 days',8,3,3000,28000),
      (7,11,NOW()-INTERVAL '3 days',NULL,9,3,20000,15000),
      (8,1,NOW()-INTERVAL '1 day',NULL,10,3,18000,12000),
      (9,4,NOW()-INTERVAL '1 day',NULL,2,3,5000,0),
      (10,6,NOW()-INTERVAL '1 day',NULL,4,3,8000,22000)
      ON CONFLICT (id) DO NOTHING
    `);
    console.log('seed data ready');

    await pool.query(`
      CREATE TABLE IF NOT EXISTS mekhanik_feedback (
        id SERIAL PRIMARY KEY,
        ot_sotrudnika_id INTEGER NOT NULL REFERENCES sotrudnik(id),
        komu_id INTEGER REFERENCES sotrudnik(id),
        zayavka_id INTEGER REFERENCES zayavka(id),
        soobshenie TEXT NOT NULL,
        prochitano BOOLEAN DEFAULT FALSE,
        data_sozdaniya TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    console.log('mekhanik_feedback table ready');

    // ---- Migrations system ----
    await pool.query('CREATE TABLE IF NOT EXISTS _migrations (name TEXT PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)');

    // One-time: wipe all feedback messages (privacy leak fix 2025-04-28)
    const reset = await pool.query("SELECT 1 FROM _migrations WHERE name = 'reset_feedback_v2'");
    if (reset.rowCount === 0) {
      await pool.query('TRUNCATE TABLE mekhanik_feedback RESTART IDENTITY');
      await pool.query("INSERT INTO _migrations(name) VALUES ('reset_feedback_v2')");
      console.log('[migration] mekhanik_feedback wiped clean');
    }

    // Таблица уведомлений
    await pool.query(`
      CREATE TABLE IF NOT EXISTS uvedomleniya (
        id SERIAL PRIMARY KEY,
        poluchatel_id INTEGER NOT NULL REFERENCES sotrudnik(id) ON DELETE CASCADE,
        tip TEXT NOT NULL,
        soobshenie TEXT NOT NULL,
        zayavka_id INTEGER REFERENCES zayavka(id) ON DELETE SET NULL,
        prochitano BOOLEAN DEFAULT FALSE,
        data_sozdaniya TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    console.log('uvedomleniya table ready');

    // Таблица FCM токенов
    await pool.query(`
      CREATE TABLE IF NOT EXISTS fcm_tokens (
        id SERIAL PRIMARY KEY,
        sotrudnik_id INTEGER NOT NULL REFERENCES sotrudnik(id) ON DELETE CASCADE,
        fcm_token TEXT NOT NULL,
        data_obnovleniya TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(sotrudnik_id, fcm_token)
      )
    `);
    console.log('fcm_tokens table ready');

    // Индексы для часто-используемых запросов
    await pool.query(`
      CREATE INDEX IF NOT EXISTS idx_uvedomleniya_poluchatel ON uvedomleniya(poluchatel_id, prochitano);
      CREATE INDEX IF NOT EXISTS idx_uvedomleniya_data ON uvedomleniya(data_sozdaniya DESC);
      CREATE INDEX IF NOT EXISTS idx_fcm_tokens_user ON fcm_tokens(sotrudnik_id);
      CREATE INDEX IF NOT EXISTS idx_feedback_komu ON mekhanik_feedback(komu_id, prochitano);
      CREATE INDEX IF NOT EXISTS idx_feedback_pair ON mekhanik_feedback(ot_sotrudnika_id, komu_id, data_sozdaniya DESC);
      CREATE INDEX IF NOT EXISTS idx_zayavka_status ON zayavka(status_id);
    `);
    console.log('indexes ready');
  } catch (e) {
    console.error('DB init error:', e.message);
  }
}
initDB();

// ============ NOTIFICATION HELPER ============
async function notify(recipientIds, tip, soobshenie, zayavkaId = null) {
  console.log(`[NOTIFY] Sending to ${recipientIds.length} recipients, tip: ${tip}`);
  for (const rid of recipientIds) {
    try {
      // Save to database
      const result = await pool.query(
        'INSERT INTO uvedomleniya (poluchatel_id, tip, soobshenie, zayavka_id) VALUES ($1,$2,$3,$4) RETURNING id',
        [rid, tip, soobshenie, zayavkaId]
      );
      console.log(`[NOTIFY] Created notification ${result.rows[0].id} for user ${rid}`);
      // Send push notification
      await sendPushNotification(rid, soobshenie, zayavkaId);
    } catch (e) {
      console.error(`[NOTIFY] Error for user ${rid}:`, e.message);
    }
  }
}

async function getUsersByRole(...roles) {
  const r = await pool.query('SELECT id FROM sotrudnik WHERE rol_id = ANY($1)', [roles]);
  return r.rows.map(row => row.id);
}

// ============ PUSH NOTIFICATIONS ============
// Get OAuth2 access token from service account
async function getFcmAccessToken() {
  const sa = getServiceAccount();
  if (!sa) return null;
  
  // Return cached token if still valid
  if (fcmAccessToken && Date.now() < fcmTokenExpiry - 60000) {
    return fcmAccessToken;
  }

  try {
    const jwt = require('jsonwebtoken');
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: sa.client_email,
      sub: sa.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600
    };
    
    const signedJwt = jwt.sign(payload, sa.private_key, { algorithm: 'RS256' });
    
    const response = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJwt}`
    });
    
    const data = await response.json();
    if (data.access_token) {
      fcmAccessToken = data.access_token;
      fcmTokenExpiry = Date.now() + (data.expires_in * 1000);
      return fcmAccessToken;
    }
  } catch (e) {
    console.error('FCM token error:', e.message);
  }
  return null;
}

async function sendPushNotification(userId, body, zayavkaId = null) {
  const sa = getServiceAccount();
  if (!sa) return;

  try {
    const accessToken = await getFcmAccessToken();
    if (!accessToken) {
      console.log('[PUSH] No FCM access token');
      return;
    }

    const tokensResult = await pool.query(
      'SELECT fcm_token FROM fcm_tokens WHERE sotrudnik_id = $1',
      [userId]
    );
    const tokens = tokensResult.rows.map(r => r.fcm_token);
    if (tokens.length === 0) return;

    const title = 'CarVix';
    const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

    // Параллельная отправка всем токенам
    await Promise.all(tokens.map(async (token) => {
      const message = {
        message: {
          token,
          notification: { title, body },
          data: {
            zayavka_id: zayavkaId != null ? String(zayavkaId) : '',
            title,
            body
          },
          android: {
            priority: 'high',
            notification: {
              channel_id: 'carvix_notifications',
              sound: 'default'
            }
          }
        }
      };

      try {
        const resp = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`
          },
          body: JSON.stringify(message)
        });

        if (!resp.ok) {
          const errText = await resp.text();
          console.error(`[PUSH] HTTP ${resp.status}: ${errText.substring(0, 200)}`);
          // Невалидный/устаревший токен — удаляем из БД
          if (resp.status === 404 || resp.status === 400) {
            try {
              const errJson = JSON.parse(errText);
              const status = errJson?.error?.status;
              if (status === 'NOT_FOUND' || status === 'INVALID_ARGUMENT' ||
                  status === 'UNREGISTERED') {
                await pool.query('DELETE FROM fcm_tokens WHERE fcm_token = $1', [token]);
                console.log(`[PUSH] Removed invalid token: ${token.substring(0, 20)}…`);
              }
            } catch (_) {}
          }
        }
      } catch (e) {
        console.error('[PUSH] send error:', e.message);
      }
    }));
  } catch (e) {
    console.error('sendPushNotification error:', e.message);
  }
}

// ============ AUTH MIDDLEWARE ============
function auth(req, res, next) {
  const h = req.headers.authorization;
  if (!h || !h.startsWith('Bearer ')) return res.status(401).json({ error: 'Unauthorized' });
  try {
    req.user = jwt.verify(h.replace('Bearer ', ''), JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'Invalid token' });
  }
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!roles.includes(req.user.rolId)) return res.status(403).json({ error: 'Forbidden' });
    next();
  };
}

// Безопасный парсер положительных целых ID из params/query/body
function parseId(value) {
  const n = parseInt(value, 10);
  return Number.isInteger(n) && n > 0 ? n : null;
}

// Middleware для валидации :id
function validateIdParam(req, res, next) {
  const id = parseId(req.params.id);
  if (id === null) return res.status(400).json({ error: 'Invalid id' });
  req.params.id = id;
  next();
}

// ============ AUTH ============
const LOGIN_REGEX = /^[a-zA-Z0-9_.-]{3,32}$/;

app.post('/api/register', authLimiter, async (req, res) => {
  const fullName = (req.body?.fullName || '').toString().trim();
  const login = (req.body?.login || '').toString().trim();
  const password = (req.body?.password || '').toString();

  if (!fullName || !login || !password) {
    return res.status(400).json({ error: 'Все поля обязательны' });
  }
  if (fullName.length < 2 || fullName.length > 100) {
    return res.status(400).json({ error: 'ФИО должно быть от 2 до 100 символов' });
  }
  if (!LOGIN_REGEX.test(login)) {
    return res.status(400).json({ error: 'Логин: 3-32 символа, буквы/цифры/._-' });
  }
  if (password.length < 6 || password.length > 128) {
    return res.status(400).json({ error: 'Пароль должен быть от 6 до 128 символов' });
  }

  try {
    const ex = await pool.query('SELECT id FROM sotrudnik WHERE login = $1', [login]);
    if (ex.rows.length) return res.status(400).json({ error: 'Логин уже занят' });
    const hash = await bcrypt.hash(password, 10);
    const r = await pool.query(
      'INSERT INTO sotrudnik (fio, login, parol_hash, rol_id, podrazdelenie_id) VALUES ($1,$2,$3,$4,$5) RETURNING id',
      [fullName, login, hash, ROLE_MECHANIC, 1]
    );
    res.json({ success: true, message: 'User registered', id: r.rows[0].id });
  } catch (e) {
    console.error('Register:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

app.post('/api/login', authLimiter, async (req, res) => {
  const login = (req.body?.login || '').toString().trim();
  const password = (req.body?.password || '').toString();
  if (!login || !password) return res.status(400).json({ error: 'Логин и пароль обязательны' });
  if (login.length > 64 || password.length > 128) {
    return res.status(400).json({ error: 'Слишком длинные значения' });
  }
  try {
    const r = await pool.query('SELECT * FROM sotrudnik WHERE login = $1', [login]);
    if (!r.rows.length) return res.status(400).json({ error: 'Неверный логин или пароль' });
    const u = r.rows[0];
    let valid = false;
    try { valid = await bcrypt.compare(password, u.parol_hash || ''); } catch {}
    // Поддержка legacy незахешированных паролей — мигрируем при удачном совпадении
    if (!valid && u.parol_hash && password === u.parol_hash) {
      valid = true;
      try {
        const newHash = await bcrypt.hash(password, 10);
        await pool.query('UPDATE sotrudnik SET parol_hash = $1 WHERE id = $2', [newHash, u.id]);
      } catch (e) { console.error('Password migration failed:', e.message); }
    }
    if (!valid) return res.status(400).json({ error: 'Неверный логин или пароль' });
    const token = jwt.sign(
      { userId: u.id, login: u.login, rolId: u.rol_id, fio: u.fio },
      JWT_SECRET,
      { expiresIn: '7d' }
    );
    res.json({
      success: true,
      token,
      user: {
        id: u.id,
        fullName: u.fio,
        login: u.login,
        rolId: u.rol_id,
        podrazdelenieId: u.podrazdelenie_id
      }
    });
  } catch (e) {
    console.error('Login:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

app.get('/api/me', auth, async (req, res) => {
  try {
    const r = await pool.query(
      `SELECT s.id, s.fio, s.login, s.rol_id, s.podrazdelenie_id, r.nazvanie AS rol_name
       FROM sotrudnik s LEFT JOIN rol r ON r.id = s.rol_id WHERE s.id = $1`,
      [req.user.userId]
    );
    if (!r.rows.length) return res.status(404).json({ error: 'Not found' });
    res.json({ user: r.rows[0] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============ REFERENCES ============
app.get('/api/refs', auth, async (req, res) => {
  try {
    const [statuses, roles, types, marks, models, divisions] = await Promise.all([
      pool.query('SELECT * FROM status ORDER BY id'),
      pool.query('SELECT * FROM rol WHERE id IN (3,4,5) ORDER BY id'),
      pool.query('SELECT * FROM tip_remonta ORDER BY id'),
      pool.query('SELECT * FROM marka ORDER BY id'),
      pool.query('SELECT * FROM model ORDER BY id'),
      pool.query('SELECT * FROM podrazdelenie ORDER BY id'),
    ]);
    res.json({
      statuses: statuses.rows,
      roles: roles.rows,
      tipy_remonta: types.rows,
      marki: marks.rows,
      modeli: models.rows,
      podrazdeleniya: divisions.rows
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============ ZAYAVKI ============
// Список заявок с фильтрами
app.get('/api/zayavki', auth, async (req, res) => {
  try {
    const { status, mine, available } = req.query;
    let where = [];
    let params = [];
    if (status) { params.push(parseInt(status)); where.push(`z.status_id = $${params.length}`); }
    if (mine === '1') {
      // только заявки в которых текущий механик участвует
      params.push(req.user.userId);
      where.push(`EXISTS (SELECT 1 FROM remont rm WHERE rm.zayavka_id = z.id AND rm.mekhanik_id = $${params.length})`);
    }
    if (available === '1') {
      // Свободные новые заявки + те, в которых текущий механик уже участвует
      params.push(req.user.userId);
      where.push(`(z.status_id = ${STATUS_NEW}
        OR EXISTS (SELECT 1 FROM remont rm WHERE rm.zayavka_id = z.id AND rm.mekhanik_id = $${params.length}))`);
    }
    const sql = `
      SELECT z.*, st.nazvanie AS status_name, tr.nazvanie AS tip_remonta_name,
             ts.gos_nomer, ts.invent_nomer, ts.tekuschee_sostoyanie,
             m.nazvanie AS model_name, mk.nazvanie AS marka_name,
             s.fio AS sozdatel_fio
      FROM zayavka z
      LEFT JOIN status st ON st.id = z.status_id
      LEFT JOIN tip_remonta tr ON tr.id = z.tip_remonta_id
      LEFT JOIN transportnoe_sredstvo ts ON ts.id = z.ts_id
      LEFT JOIN model m ON m.id = ts.model_id
      LEFT JOIN marka mk ON mk.id = m.marka_id
      LEFT JOIN sotrudnik s ON s.id = z.sozdatel_id
      ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY z.data_sozdaniya DESC
    `;
    const r = await pool.query(sql, params);
    res.json({ zayavki: r.rows });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

// Детали заявки + ремонт
app.get('/api/zayavki/:id', auth, async (req, res) => {
  try {
    const zr = await pool.query(`
      SELECT z.*, st.nazvanie AS status_name, tr.nazvanie AS tip_remonta_name,
             ts.gos_nomer, ts.invent_nomer, ts.probeg, ts.tekuschee_sostoyanie,
             m.nazvanie AS model_name, mk.nazvanie AS marka_name,
             s.fio AS sozdatel_fio
      FROM zayavka z
      LEFT JOIN status st ON st.id = z.status_id
      LEFT JOIN tip_remonta tr ON tr.id = z.tip_remonta_id
      LEFT JOIN transportnoe_sredstvo ts ON ts.id = z.ts_id
      LEFT JOIN model m ON m.id = ts.model_id
      LEFT JOIN marka mk ON mk.id = m.marka_id
      LEFT JOIN sotrudnik s ON s.id = z.sozdatel_id
      WHERE z.id = $1
    `, [req.params.id]);
    if (!zr.rows.length) return res.status(404).json({ error: 'Not found' });
    const rr = await pool.query(`
      SELECT r.*, m.fio AS mekhanik_fio, gm.fio AS glavniy_mekhanik_fio
      FROM remont r
      LEFT JOIN sotrudnik m ON m.id = r.mekhanik_id
      LEFT JOIN sotrudnik gm ON gm.id = r.glavniy_mekhanik_id
      WHERE r.zayavka_id = $1
      ORDER BY r.id DESC LIMIT 1
    `, [req.params.id]);
    res.json({ zayavka: zr.rows[0], remont: rr.rows[0] || null });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Создать заявку (главмех/админ)
app.post('/api/zayavki', auth, requireRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN), async (req, res) => {
  const tsId = parseId(req.body?.ts_id);
  const tipRemontaId = parseId(req.body?.tip_remonta_id);
  const opisanie = (req.body?.opisanie || '').toString().trim();
  const prioritetRaw = parseInt(req.body?.prioritet, 10);
  const prioritet = Number.isInteger(prioritetRaw) && prioritetRaw >= 1 && prioritetRaw <= 5
    ? prioritetRaw : 3;

  if (!tsId || !tipRemontaId) {
    return res.status(400).json({ error: 'Некорректный ts_id или tip_remonta_id' });
  }
  if (opisanie.length < 3 || opisanie.length > 2000) {
    return res.status(400).json({ error: 'Описание: от 3 до 2000 символов' });
  }

  try {
    const r = await pool.query(`
      INSERT INTO zayavka (data_sozdaniya, sozdatel_id, ts_id, tip_remonta_id, opisanie, status_id, prioritet, data_rezhima)
      VALUES (NOW(), $1, $2, $3, $4, $5, $6, NOW()) RETURNING id
    `, [req.user.userId, tsId, tipRemontaId, opisanie, STATUS_NEW, prioritet]);
    const newId = r.rows[0].id;
    // Уведомляем всех механиков о новой заявке
    const mechanics = await getUsersByRole(ROLE_MECHANIC);
    await notify(mechanics, 'new_zayavka', `Новая заявка #${newId}: ${opisanie.substring(0, 80)}`, newId);
    res.json({ success: true, id: newId });
  } catch (e) {
    console.error('Create zayavka:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// Обновить заявку (админ)
app.put('/api/zayavki/:id', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  const { ts_id, tip_remonta_id, opisanie, prioritet, status_id } = req.body;
  try {
    await pool.query(`
      UPDATE zayavka SET ts_id = COALESCE($1, ts_id), tip_remonta_id = COALESCE($2, tip_remonta_id),
        opisanie = COALESCE($3, opisanie), prioritet = COALESCE($4, prioritet), status_id = COALESCE($5, status_id)
      WHERE id = $6
    `, [ts_id, tip_remonta_id, opisanie, prioritet, status_id, req.params.id]);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Удалить заявку (админ)
app.delete('/api/zayavki/:id', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  try {
    await pool.query('DELETE FROM remont WHERE zayavka_id = $1', [req.params.id]);
    await pool.query('DELETE FROM zayavka WHERE id = $1', [req.params.id]);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Механик берёт заявку: создаёт remont, статус -> В работе. С транзакцией от race-condition.
app.post('/api/zayavki/:id/take', auth, requireRole(ROLE_MECHANIC, ROLE_HEAD_MECHANIC), async (req, res) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    // Блокируем строку чтобы два механика не взяли одновременно
    const zr = await client.query('SELECT * FROM zayavka WHERE id = $1 FOR UPDATE', [req.params.id]);
    if (!zr.rows.length) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Not found' });
    }
    if (zr.rows[0].status_id !== STATUS_NEW) {
      await client.query('ROLLBACK');
      return res.status(400).json({ error: 'Заявка уже взята или закрыта' });
    }
    // На всякий случай: проверка что нет открытого remont
    const existing = await client.query(
      'SELECT 1 FROM remont WHERE zayavka_id = $1 AND data_okonchaniya IS NULL',
      [req.params.id]
    );
    if (existing.rows.length) {
      await client.query('ROLLBACK');
      return res.status(400).json({ error: 'Заявка уже находится в ремонте' });
    }
    const gm = await client.query('SELECT id FROM sotrudnik WHERE rol_id = $1 LIMIT 1', [ROLE_HEAD_MECHANIC]);
    const gmId = gm.rows[0]?.id || null;
    await client.query(`
      INSERT INTO remont (zayavka_id, data_nachala, mekhanik_id, glavniy_mekhanik_id, stoimost_rabot, stoimost_zapchastey)
      VALUES ($1, NOW(), $2, $3, 0, 0)
    `, [req.params.id, req.user.userId, gmId]);
    await client.query('UPDATE zayavka SET status_id = $1 WHERE id = $2', [STATUS_IN_PROGRESS, req.params.id]);
    // Авто-обновление состояния ТС: "В ремонте"
    await client.query(
      `UPDATE transportnoe_sredstvo SET tekuschee_sostoyanie = 'В ремонте'
       WHERE id = (SELECT ts_id FROM zayavka WHERE id = $1)`,
      [req.params.id]
    );
    await client.query('COMMIT');
    // Уведомляем главмехов и создателя заявки
    const zayavkaId = parseInt(req.params.id);
    const heads = await getUsersByRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN);
    const recipients = [...new Set([...heads, zr.rows[0].sozdatel_id].filter(id => id && id !== req.user.userId))];
    await notify(recipients, 'zayavka_taken', `${req.user.fio} взял заявку #${zayavkaId} в работу`, zayavkaId);
    res.json({ success: true });
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    console.error(e);
    res.status(500).json({ error: e.message });
  } finally {
    client.release();
  }
});

// Изменить статус заявки (механик-исполнитель / главмех / админ)
app.post('/api/zayavki/:id/status', auth, requireRole(ROLE_MECHANIC, ROLE_HEAD_MECHANIC, ROLE_ADMIN), async (req, res) => {
  const { status_id, kommentariy, itog, stoimost_rabot, stoimost_zapchastey } = req.body;
  if (!status_id) return res.status(400).json({ error: 'status_id required' });
  try {
    // Проверяем — если обычный механик, то он должен быть исполнителем
    if (req.user.rolId === ROLE_MECHANIC) {
      const rm = await pool.query(
        'SELECT mekhanik_id FROM remont WHERE zayavka_id = $1 AND data_okonchaniya IS NULL ORDER BY id DESC LIMIT 1',
        [req.params.id]
      );
      if (!rm.rows.length || rm.rows[0].mekhanik_id !== req.user.userId) {
        return res.status(403).json({ error: 'Только исполнитель может менять статус' });
      }
    }
    await pool.query('UPDATE zayavka SET status_id = $1 WHERE id = $2', [status_id, req.params.id]);
    if (status_id === STATUS_DONE || status_id === STATUS_REJECTED) {
      await pool.query(`
        UPDATE remont SET data_okonchaniya = NOW(),
          kommentariy = COALESCE($1, kommentariy),
          itog = COALESCE($2, itog),
          stoimost_rabot = COALESCE($3, stoimost_rabot),
          stoimost_zapchastey = COALESCE($4, stoimost_zapchastey)
        WHERE zayavka_id = $5 AND data_okonchaniya IS NULL
      `, [kommentariy, itog, stoimost_rabot, stoimost_zapchastey, req.params.id]);
    } else if (kommentariy) {
      await pool.query('UPDATE remont SET kommentariy = $1 WHERE zayavka_id = $2 AND data_okonchaniya IS NULL', [kommentariy, req.params.id]);
    }
    // Авто-обновление состояния ТС
    if (status_id === STATUS_DONE) {
      await pool.query(
        `UPDATE transportnoe_sredstvo SET tekuschee_sostoyanie = 'Исправно'
         WHERE id = (SELECT ts_id FROM zayavka WHERE id = $1)`,
        [req.params.id]
      );
    } else if (status_id === STATUS_REJECTED) {
      await pool.query(
        `UPDATE transportnoe_sredstvo SET tekuschee_sostoyanie = 'Требует ремонта'
         WHERE id = (SELECT ts_id FROM zayavka WHERE id = $1)`,
        [req.params.id]
      );
    } else if (status_id === STATUS_WAIT_PARTS) {
      await pool.query(
        `UPDATE transportnoe_sredstvo SET tekuschee_sostoyanie = 'Ожидание запчастей'
         WHERE id = (SELECT ts_id FROM zayavka WHERE id = $1)`,
        [req.params.id]
      );
    }
    // Уведомления о смене статуса
    const zId = parseInt(req.params.id);
    const statusNames = { [STATUS_DONE]: 'завершена', [STATUS_REJECTED]: 'отклонена', [STATUS_WAIT_PARTS]: 'ожидает запчасти', [STATUS_IN_PROGRESS]: 'возобновлена' };
    const statusTips = { [STATUS_DONE]: 'status_done', [STATUS_REJECTED]: 'status_rejected', [STATUS_WAIT_PARTS]: 'status_wait_parts', [STATUS_IN_PROGRESS]: 'status_in_progress' };
    const sName = statusNames[status_id];
    const sTip = statusTips[status_id];
    if (sName && sTip) {
      const msg = `Заявка #${zId} ${sName} (${req.user.fio})`;
      // Определяем кого уведомить
      const heads = await getUsersByRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN);
      // Также уведомляем механика-исполнителя если статус меняет не он
      const rm = await pool.query('SELECT mekhanik_id FROM remont WHERE zayavka_id = $1 ORDER BY id DESC LIMIT 1', [zId]);
      const mekId = rm.rows[0]?.mekhanik_id;
      const allIds = [...new Set([...heads, mekId].filter(id => id && id !== req.user.userId))];
      await notify(allIds, sTip, msg, zId);
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============ ТРАНСПОРТНЫЕ СРЕДСТВА ============
app.get('/api/ts', auth, async (req, res) => {
  try {
    const r = await pool.query(`
      SELECT ts.*, m.nazvanie AS model_name, mk.nazvanie AS marka_name, p.nazvanie AS podrazdelenie_name
      FROM transportnoe_sredstvo ts
      LEFT JOIN model m ON m.id = ts.model_id
      LEFT JOIN marka mk ON mk.id = m.marka_id
      LEFT JOIN podrazdelenie p ON p.id = ts.podrazdelenie_id
      ORDER BY ts.id
    `);
    res.json({ ts: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/ts/:id', auth, async (req, res) => {
  try {
    const r = await pool.query(`
      SELECT ts.*, m.nazvanie AS model_name, mk.nazvanie AS marka_name, p.nazvanie AS podrazdelenie_name
      FROM transportnoe_sredstvo ts
      LEFT JOIN model m ON m.id = ts.model_id
      LEFT JOIN marka mk ON mk.id = m.marka_id
      LEFT JOIN podrazdelenie p ON p.id = ts.podrazdelenie_id
      WHERE ts.id = $1
    `, [req.params.id]);
    if (!r.rows.length) return res.status(404).json({ error: 'Not found' });
    res.json({ ts: r.rows[0] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.put('/api/ts/:id', auth, requireRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN), validateIdParam, async (req, res) => {
  const tekuschee = req.body?.tekuschee_sostoyanie != null
    ? req.body.tekuschee_sostoyanie.toString().trim() : null;
  const probegRaw = req.body?.probeg;
  const probeg = probegRaw != null ? parseInt(probegRaw, 10) : null;
  if (probeg !== null && (!Number.isInteger(probeg) || probeg < 0)) {
    return res.status(400).json({ error: 'Некорректный пробег' });
  }
  const gos = req.body?.gos_nomer != null ? req.body.gos_nomer.toString().trim() : null;
  const invent = req.body?.invent_nomer != null ? req.body.invent_nomer.toString().trim() : null;
  const modelId = req.body?.model_id != null ? parseId(req.body.model_id) : null;
  const podrId = req.body?.podrazdelenie_id != null ? parseId(req.body.podrazdelenie_id) : null;

  try {
    await pool.query(
      `UPDATE transportnoe_sredstvo SET
         tekuschee_sostoyanie = COALESCE($1, tekuschee_sostoyanie),
         probeg = COALESCE($2, probeg),
         gos_nomer = COALESCE($3, gos_nomer),
         invent_nomer = COALESCE($4, invent_nomer),
         model_id = COALESCE($5, model_id),
         podrazdelenie_id = COALESCE($6, podrazdelenie_id)
       WHERE id = $7`,
      [tekuschee, probeg, gos, invent, modelId, podrId, req.params.id]
    );
    res.json({ success: true });
  } catch (e) {
    console.error('Update TS:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// Создать ТС (главмех/админ)
app.post('/api/ts', auth, requireRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN), async (req, res) => {
  const gos = (req.body?.gos_nomer || '').toString().trim();
  const invent = (req.body?.invent_nomer || '').toString().trim();
  const modelId = parseId(req.body?.model_id);
  const podrId = parseId(req.body?.podrazdelenie_id);
  const probegRaw = req.body?.probeg;
  const probeg = probegRaw != null && probegRaw !== '' ? parseInt(probegRaw, 10) : 0;
  const tekuschee = (req.body?.tekuschee_sostoyanie || 'Исправно').toString().trim();

  if (!gos) return res.status(400).json({ error: 'Госномер обязателен' });
  if (gos.length > 20) return res.status(400).json({ error: 'Госномер слишком длинный' });
  if (!modelId) return res.status(400).json({ error: 'Модель обязательна' });
  if (!podrId) return res.status(400).json({ error: 'Подразделение обязательно' });
  if (!Number.isInteger(probeg) || probeg < 0) {
    return res.status(400).json({ error: 'Некорректный пробег' });
  }

  try {
    const r = await pool.query(
      `INSERT INTO transportnoe_sredstvo (gos_nomer, invent_nomer, model_id, podrazdelenie_id, probeg, tekuschee_sostoyanie)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,
      [gos, invent || null, modelId, podrId, probeg, tekuschee]
    );
    res.json({ success: true, id: r.rows[0].id });
  } catch (e) {
    console.error('Create TS:', e);
    if (e.code === '23505') {
      return res.status(400).json({ error: 'ТС с таким номером уже существует' });
    }
    res.status(500).json({ error: 'Server error' });
  }
});

// Удалить ТС (только админ)
app.delete('/api/ts/:id', auth, requireRole(ROLE_ADMIN), validateIdParam, async (req, res) => {
  try {
    // Проверяем, есть ли заявки на это ТС
    const usage = await pool.query(
      'SELECT COUNT(*)::int AS cnt FROM zayavka WHERE ts_id = $1',
      [req.params.id]
    );
    if (usage.rows[0].cnt > 0) {
      return res.status(400).json({
        error: `Нельзя удалить: есть ${usage.rows[0].cnt} связанных заявок`
      });
    }
    await pool.query('DELETE FROM transportnoe_sredstvo WHERE id = $1', [req.params.id]);
    res.json({ success: true });
  } catch (e) {
    console.error('Delete TS:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// ============ СОТРУДНИКИ ============
app.get('/api/sotrudniki', auth, async (req, res) => {
  try {
    const { rol_id } = req.query;
    let sql = `
      SELECT s.id, s.fio, s.login, s.rol_id, s.podrazdelenie_id,
             r.nazvanie AS rol_name, p.nazvanie AS podrazdelenie_name
      FROM sotrudnik s
      LEFT JOIN rol r ON r.id = s.rol_id
      LEFT JOIN podrazdelenie p ON p.id = s.podrazdelenie_id
    `;
    const params = [];
    if (rol_id) { params.push(parseInt(rol_id)); sql += ` WHERE s.rol_id = $1`; }
    sql += ' ORDER BY s.id';
    const r = await pool.query(sql, params);
    res.json({ sotrudniki: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Загрузка механиков с активной работой
app.get('/api/sotrudniki/mekhaniki/active', auth, requireRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN), async (req, res) => {
  try {
    const r = await pool.query(`
      SELECT s.id, s.fio, s.login,
        (SELECT COUNT(*)::int FROM remont rm
          JOIN zayavka z ON z.id = rm.zayavka_id
          WHERE rm.mekhanik_id = s.id AND z.status_id = $1) AS active_remonts,
        (SELECT json_agg(json_build_object('zayavka_id', z.id, 'opisanie', z.opisanie, 'gos_nomer', ts.gos_nomer))
          FROM remont rm
          JOIN zayavka z ON z.id = rm.zayavka_id
          LEFT JOIN transportnoe_sredstvo ts ON ts.id = z.ts_id
          WHERE rm.mekhanik_id = s.id AND z.status_id = $1) AS active_zayavki
      FROM sotrudnik s WHERE s.rol_id = $2
    `, [STATUS_IN_PROGRESS, ROLE_MECHANIC]);
    res.json({ mekhaniki: r.rows });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/sotrudniki', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  const { fio, login, password, rol_id, podrazdelenie_id } = req.body;
  if (!fio || !login || !password || !rol_id) return res.status(400).json({ error: 'Missing fields' });
  try {
    const ex = await pool.query('SELECT id FROM sotrudnik WHERE login = $1', [login]);
    if (ex.rows.length) return res.status(400).json({ error: 'Login taken' });
    const hash = await bcrypt.hash(password, 10);
    const r = await pool.query(
      'INSERT INTO sotrudnik (fio, login, parol_hash, rol_id, podrazdelenie_id) VALUES ($1,$2,$3,$4,$5) RETURNING id',
      [fio, login, hash, rol_id, podrazdelenie_id || 1]
    );
    res.json({ success: true, id: r.rows[0].id });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.put('/api/sotrudniki/:id', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  const { fio, login, password, rol_id, podrazdelenie_id } = req.body;
  try {
    const updates = [];
    const params = [];
    if (fio) { params.push(fio); updates.push(`fio = $${params.length}`); }
    if (login) { params.push(login); updates.push(`login = $${params.length}`); }
    if (rol_id) { params.push(rol_id); updates.push(`rol_id = $${params.length}`); }
    if (podrazdelenie_id) { params.push(podrazdelenie_id); updates.push(`podrazdelenie_id = $${params.length}`); }
    if (password) {
      const hash = await bcrypt.hash(password, 10);
      params.push(hash); updates.push(`parol_hash = $${params.length}`);
    }
    if (!updates.length) return res.json({ success: true });
    params.push(req.params.id);
    await pool.query(`UPDATE sotrudnik SET ${updates.join(', ')} WHERE id = $${params.length}`, params);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.delete('/api/sotrudniki/:id', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  const client = await pool.connect();
  try {
    const id = parseInt(req.params.id);
    if (id === req.user.userId) {
      return res.status(400).json({ error: 'Нельзя удалить самого себя' });
    }
    await client.query('BEGIN');
    // Каскадно удаляем зависимости (в БД нет ON DELETE CASCADE на sotrudnik FK)
    await client.query('DELETE FROM mekhanik_feedback WHERE ot_sotrudnika_id = $1 OR komu_id = $1', [id]);
    await client.query('UPDATE remont SET mekhanik_id = NULL WHERE mekhanik_id = $1', [id]);
    await client.query('UPDATE remont SET glavniy_mekhanik_id = NULL WHERE glavniy_mekhanik_id = $1', [id]);
    // Заявки, созданные этим сотрудником, оставляем (sozdatel_id допускает NULL? если нет — тоже NULLify)
    await client.query('UPDATE zayavka SET sozdatel_id = NULL WHERE sozdatel_id = $1', [id]).catch(() => {});
    await client.query('DELETE FROM sotrudnik WHERE id = $1', [id]);
    await client.query('COMMIT');
    res.json({ success: true });
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('Delete sotrudnik:', e);
    res.status(500).json({ error: e.message });
  } finally {
    client.release();
  }
});

// ============ FEEDBACK ============
app.get('/api/feedback', auth, async (req, res) => {
  try {
    const r = await pool.query(`
      SELECT f.*, ot.fio AS ot_fio, komu.fio AS komu_fio
      FROM mekhanik_feedback f
      LEFT JOIN sotrudnik ot ON ot.id = f.ot_sotrudnika_id
      LEFT JOIN sotrudnik komu ON komu.id = f.komu_id
      WHERE f.komu_id = $1 OR f.ot_sotrudnika_id = $1
      ORDER BY f.data_sozdaniya DESC LIMIT 100
    `, [req.user.userId]);
    res.json({ messages: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/feedback', auth, async (req, res) => {
  const soobshenie = (req.body?.soobshenie || '').toString().trim();
  const komuId = parseId(req.body?.komu_id);
  const zayavkaId = req.body?.zayavka_id != null ? parseId(req.body.zayavka_id) : null;

  if (!soobshenie) return res.status(400).json({ error: 'Сообщение не может быть пустым' });
  if (soobshenie.length > 4000) return res.status(400).json({ error: 'Сообщение слишком длинное (макс. 4000)' });

  try {
    let recipientId = komuId;
    if (!recipientId) {
      const gm = await pool.query('SELECT id FROM sotrudnik WHERE rol_id = $1 LIMIT 1', [ROLE_HEAD_MECHANIC]);
      recipientId = gm.rows[0]?.id || null;
    }
    if (!recipientId) {
      return res.status(400).json({ error: 'Получатель не определён' });
    }
    if (recipientId === req.user.userId) {
      return res.status(400).json({ error: 'Нельзя отправить сообщение самому себе' });
    }
    await pool.query(
      'INSERT INTO mekhanik_feedback (ot_sotrudnika_id, komu_id, zayavka_id, soobshenie) VALUES ($1,$2,$3,$4)',
      [req.user.userId, recipientId, zayavkaId, soobshenie]
    );
    // Push о новом сообщении
    const preview = soobshenie.length > 80 ? soobshenie.substring(0, 80) + '…' : soobshenie;
    sendPushNotification(recipientId, `${req.user.fio}: ${preview}`, zayavkaId).catch(() => {});
    res.json({ success: true });
  } catch (e) {
    console.error('Feedback create:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// Удалить broadcast-сообщения с пустым получателем (старая утечка)
app.post('/api/feedback/cleanup-orphans', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  try {
    const r = await pool.query('DELETE FROM mekhanik_feedback WHERE komu_id IS NULL RETURNING id');
    res.json({ deleted: r.rowCount });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/feedback/unread', auth, async (req, res) => {
  try {
    const r = await pool.query(
      'SELECT COUNT(*)::int AS count FROM mekhanik_feedback WHERE komu_id = $1 AND prochitano = FALSE',
      [req.user.userId]
    );
    res.json({ count: r.rows[0].count });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/feedback/conversations', auth, async (req, res) => {
  try {
    const me = req.user.userId;
    // Все люди с которыми были диалоги
    const r = await pool.query(`
      WITH partners AS (
        SELECT DISTINCT
          CASE WHEN ot_sotrudnika_id = $1 THEN komu_id ELSE ot_sotrudnika_id END AS uid
        FROM mekhanik_feedback
        WHERE (ot_sotrudnika_id = $1 OR komu_id = $1)
      )
      SELECT s.id, s.fio, s.login, s.rol_id, r.nazvanie AS rol_name,
        (SELECT soobshenie FROM mekhanik_feedback m
          WHERE (m.ot_sotrudnika_id = $1 AND m.komu_id = s.id) OR (m.ot_sotrudnika_id = s.id AND m.komu_id = $1)
          ORDER BY m.data_sozdaniya DESC LIMIT 1) AS last_message,
        (SELECT data_sozdaniya FROM mekhanik_feedback m
          WHERE (m.ot_sotrudnika_id = $1 AND m.komu_id = s.id) OR (m.ot_sotrudnika_id = s.id AND m.komu_id = $1)
          ORDER BY m.data_sozdaniya DESC LIMIT 1) AS last_time,
        (SELECT COUNT(*)::int FROM mekhanik_feedback m
          WHERE m.ot_sotrudnika_id = s.id AND m.komu_id = $1 AND m.prochitano = FALSE) AS unread
      FROM partners p
      JOIN sotrudnik s ON s.id = p.uid
      LEFT JOIN rol r ON r.id = s.rol_id
      WHERE s.id IS NOT NULL AND s.id <> $1
      ORDER BY last_time DESC NULLS LAST
    `, [me]);
    res.json({ conversations: r.rows });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

// Удалить весь диалог с конкретным пользователем (только для главмеха/админа)
app.delete('/api/feedback/with/:userId', auth, async (req, res) => {
  try {
    const role = req.user.rolId;
    if (role !== 4 && role !== 5) {
      return res.status(403).json({ error: 'Forbidden' });
    }
    const me = req.user.userId;
    const other = parseInt(req.params.userId);
    const r = await pool.query(
      `DELETE FROM mekhanik_feedback
       WHERE (ot_sotrudnika_id = $1 AND komu_id = $2)
          OR (ot_sotrudnika_id = $2 AND komu_id = $1)`,
      [me, other]
    );
    res.json({ success: true, deleted: r.rowCount });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/feedback/with/:userId', auth, async (req, res) => {
  try {
    const me = req.user.userId;
    const other = parseInt(req.params.userId);
    const r = await pool.query(`
      SELECT f.*, ot.fio AS ot_fio, komu.fio AS komu_fio
      FROM mekhanik_feedback f
      LEFT JOIN sotrudnik ot ON ot.id = f.ot_sotrudnika_id
      LEFT JOIN sotrudnik komu ON komu.id = f.komu_id
      WHERE (f.ot_sotrudnika_id = $1 AND f.komu_id = $2)
         OR (f.ot_sotrudnika_id = $2 AND f.komu_id = $1)
      ORDER BY f.data_sozdaniya ASC
    `, [me, other]);
    // отметить как прочитанные входящие
    await pool.query('UPDATE mekhanik_feedback SET prochitano = TRUE WHERE komu_id = $1 AND ot_sotrudnika_id = $2', [me, other]);
    res.json({ messages: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/feedback/:id/read', auth, async (req, res) => {
  try {
    await pool.query('UPDATE mekhanik_feedback SET prochitano = TRUE WHERE id = $1 AND komu_id = $2', [req.params.id, req.user.userId]);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============ NOTIFICATIONS ============
app.get('/api/notifications', auth, async (req, res) => {
  try {
    const r = await pool.query(`
      SELECT * FROM uvedomleniya
      WHERE poluchatel_id = $1
      ORDER BY data_sozdaniya DESC
      LIMIT 100
    `, [req.user.userId]);
    res.json({ notifications: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/notifications/unread', auth, async (req, res) => {
  try {
    const r = await pool.query(
      'SELECT COUNT(*)::int AS count FROM uvedomleniya WHERE poluchatel_id = $1 AND prochitano = FALSE',
      [req.user.userId]
    );
    res.json({ count: r.rows[0].count });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/notifications/:id/read', auth, async (req, res) => {
  try {
    await pool.query(
      'UPDATE uvedomleniya SET prochitano = TRUE WHERE id = $1 AND poluchatel_id = $2',
      [req.params.id, req.user.userId]
    );
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/notifications/read-all', auth, async (req, res) => {
  try {
    await pool.query(
      'UPDATE uvedomleniya SET prochitano = TRUE WHERE poluchatel_id = $1 AND prochitano = FALSE',
      [req.user.userId]
    );
    res.json({ success: true });
  } catch (e) {
    console.error('Mark-all-read:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// Удалить одно уведомление
app.delete('/api/notifications/:id', auth, validateIdParam, async (req, res) => {
  try {
    const r = await pool.query(
      'DELETE FROM uvedomleniya WHERE id = $1 AND poluchatel_id = $2',
      [req.params.id, req.user.userId]
    );
    res.json({ success: true, deleted: r.rowCount });
  } catch (e) {
    console.error('Delete notification:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// Очистить все уведомления текущего пользователя
app.delete('/api/notifications', auth, async (req, res) => {
  try {
    const r = await pool.query(
      'DELETE FROM uvedomleniya WHERE poluchatel_id = $1',
      [req.user.userId]
    );
    res.json({ success: true, deleted: r.rowCount });
  } catch (e) {
    console.error('Clear notifications:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// ============ FCM TOKEN ============
app.post('/api/fcm-token', auth, async (req, res) => {
  const fcmToken = (req.body?.fcm_token || '').toString().trim();
  if (!fcmToken || fcmToken.length < 20 || fcmToken.length > 4096) {
    return res.status(400).json({ error: 'fcm_token required' });
  }
  console.log(`[FCM-TOKEN] user=${req.user.userId} token=${fcmToken.substring(0, 20)}…`);
  try {
    // Если этот же токен висит на другом пользователе (смена аккаунта на устройстве) — переносим
    await pool.query(
      'DELETE FROM fcm_tokens WHERE fcm_token = $1 AND sotrudnik_id <> $2',
      [fcmToken, req.user.userId]
    );
    await pool.query(
      `INSERT INTO fcm_tokens (sotrudnik_id, fcm_token) VALUES ($1, $2)
       ON CONFLICT (sotrudnik_id, fcm_token) DO UPDATE SET data_obnovleniya = CURRENT_TIMESTAMP`,
      [req.user.userId, fcmToken]
    );
    res.json({ success: true });
  } catch (e) {
    console.error('[FCM-TOKEN] Error:', e.message);
    res.status(500).json({ error: 'Server error' });
  }
});

app.delete('/api/fcm-token', auth, async (req, res) => {
  const fcmToken = (req.body?.fcm_token || '').toString().trim();
  if (!fcmToken) return res.status(400).json({ error: 'fcm_token required' });
  try {
    await pool.query(
      'DELETE FROM fcm_tokens WHERE sotrudnik_id = $1 AND fcm_token = $2',
      [req.user.userId, fcmToken]
    );
    res.json({ success: true });
  } catch (e) {
    console.error('[FCM-TOKEN] Delete error:', e.message);
    res.status(500).json({ error: 'Server error' });
  }
});

// ============ СТАТИСТИКА (только админ) ============
app.get('/api/stats', auth, requireRole(ROLE_ADMIN), async (req, res) => {
  try {
    const [totals, byStatus, byTip, costs, monthly, topTs] = await Promise.all([
      // Общие счётчики
      pool.query(`
        SELECT
          (SELECT COUNT(*)::int FROM zayavka) AS total_zayavki,
          (SELECT COUNT(*)::int FROM transportnoe_sredstvo) AS total_ts,
          (SELECT COUNT(*)::int FROM sotrudnik) AS total_sotrudnikov,
          (SELECT COUNT(*)::int FROM remont WHERE data_okonchaniya IS NULL) AS active_remonts,
          (SELECT COUNT(*)::int FROM zayavka WHERE status_id = 1) AS new_zayavki
      `),
      // По статусам
      pool.query(`
        SELECT s.id, s.nazvanie AS name, COUNT(z.id)::int AS count
        FROM status s
        LEFT JOIN zayavka z ON z.status_id = s.id
        GROUP BY s.id, s.nazvanie
        ORDER BY s.id
      `),
      // По типам поломок (топ-10)
      pool.query(`
        SELECT tr.id, tr.nazvanie AS name, COUNT(z.id)::int AS count
        FROM tip_remonta tr
        LEFT JOIN zayavka z ON z.tip_remonta_id = tr.id
        GROUP BY tr.id, tr.nazvanie
        HAVING COUNT(z.id) > 0
        ORDER BY count DESC
        LIMIT 10
      `),
      // Суммы трат
      pool.query(`
        SELECT
          COALESCE(SUM(stoimost_rabot), 0)::float AS total_works,
          COALESCE(SUM(stoimost_zapchastey), 0)::float AS total_parts,
          COALESCE(SUM(COALESCE(stoimost_rabot,0) + COALESCE(stoimost_zapchastey,0)), 0)::float AS total_sum,
          COUNT(*)::int AS remonts_count
        FROM remont
      `),
      // Траты по месяцам (последние 6 мес)
      pool.query(`
        SELECT
          TO_CHAR(DATE_TRUNC('month', COALESCE(r.data_okonchaniya, r.data_nachala)), 'YYYY-MM') AS month,
          COALESCE(SUM(COALESCE(r.stoimost_rabot,0) + COALESCE(r.stoimost_zapchastey,0)), 0)::float AS sum,
          COUNT(*)::int AS count
        FROM remont r
        WHERE COALESCE(r.data_okonchaniya, r.data_nachala) >= NOW() - INTERVAL '6 months'
        GROUP BY DATE_TRUNC('month', COALESCE(r.data_okonchaniya, r.data_nachala))
        ORDER BY month
      `),
      // Топ-5 ТС по числу ремонтов
      pool.query(`
        SELECT ts.id, ts.gos_nomer AS gos_nomer,
               mk.nazvanie AS marka, m.nazvanie AS model,
               COUNT(z.id)::int AS repairs_count,
               COALESCE(SUM(COALESCE(r.stoimost_rabot,0) + COALESCE(r.stoimost_zapchastey,0)), 0)::float AS total_cost
        FROM transportnoe_sredstvo ts
        LEFT JOIN model m ON m.id = ts.model_id
        LEFT JOIN marka mk ON mk.id = m.marka_id
        LEFT JOIN zayavka z ON z.ts_id = ts.id
        LEFT JOIN remont r ON r.zayavka_id = z.id
        GROUP BY ts.id, ts.gos_nomer, mk.nazvanie, m.nazvanie
        HAVING COUNT(z.id) > 0
        ORDER BY repairs_count DESC, total_cost DESC
        LIMIT 5
      `)
    ]);

    res.json({
      totals: totals.rows[0],
      by_status: byStatus.rows,
      by_tip_remonta: byTip.rows,
      costs: costs.rows[0],
      monthly: monthly.rows,
      top_ts: topTs.rows
    });
  } catch (e) {
    console.error('Stats:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

// ============ DEBUG ============
app.get('/api/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    const sa = getServiceAccount();
    const fileExists = fs.existsSync(FCM_SERVICE_ACCOUNT_PATH);
    res.json({
      status: 'OK',
      fcm: sa ? 'configured' : 'not configured',
      project: sa?.project_id || null,
      service_account_source: fileExists
        ? `file:${FCM_SERVICE_ACCOUNT_PATH}`
        : (FCM_SERVICE_ACCOUNT_JSON ? 'env' : 'none')
    });
  } catch (e) {
    res.status(500).json({ error: 'Server error' });
  }
});

// Test notification (for debugging)
app.post('/api/test-notification', auth, async (req, res) => {
  try {
    const userId = req.user.userId;
    console.log(`[TEST] Creating test notification for user ${userId}`);
    
    // Create test notification
    await pool.query(
      'INSERT INTO uvedomleniya (poluchatel_id, tip, soobshenie, zayavka_id) VALUES ($1,$2,$3,$4)',
      [userId, 'test', 'Тестовое уведомление', null]
    );
    
    // Try to send push
    await sendPushNotification(userId, 'Тестовое push-уведомление', null);
    
    res.json({ success: true, message: 'Notification created' });
  } catch (e) {
    console.error('[TEST] Error:', e);
    res.status(500).json({ error: e.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Carvix API running on port ${PORT}`));
