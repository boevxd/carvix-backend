const express = require('express');
const { Pool } = require('pg');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
  next();
});

const DB_URL = process.env.DATABASE_URL ||
  'postgresql://carvix:7o8t8yAFx4Ts2sPTSf8MTgBvLqERAnM9@dpg-d7ocin2qqhas73c2i93g-a/carvix';

console.log('Connecting to DB:', DB_URL.replace(/:([^@]+)@/, ':***@'));

const pool = new Pool({
  connectionString: DB_URL,
  ssl: { rejectUnauthorized: false }
});

const JWT_SECRET = 'carvix_secret_key_2024';

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

async function initDB() {
  try {
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
  } catch (e) {
    console.error('DB init error:', e.message);
  }
}
initDB();

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

// ============ AUTH ============
app.post('/api/register', async (req, res) => {
  const { fullName, login, password } = req.body;
  if (!fullName || !login || !password) return res.status(400).json({ error: 'All fields required' });
  try {
    const ex = await pool.query('SELECT id FROM sotrudnik WHERE login = $1', [login]);
    if (ex.rows.length) return res.status(400).json({ error: 'Login already taken' });
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

app.post('/api/login', async (req, res) => {
  const { login, password } = req.body;
  if (!login || !password) return res.status(400).json({ error: 'Login and password required' });
  try {
    const r = await pool.query('SELECT * FROM sotrudnik WHERE login = $1', [login]);
    if (!r.rows.length) return res.status(400).json({ error: 'Invalid credentials' });
    const u = r.rows[0];
    let valid = false;
    try { valid = await bcrypt.compare(password, u.parol_hash || ''); } catch {}
    if (!valid && password === u.parol_hash) valid = true;
    if (!valid) return res.status(400).json({ error: 'Invalid credentials' });
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
      pool.query('SELECT * FROM rol ORDER BY id'),
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
    const { status, mine } = req.query;
    let where = [];
    let params = [];
    if (status) { params.push(parseInt(status)); where.push(`z.status_id = $${params.length}`); }
    if (mine === '1') {
      // только заявки в которых текущий механик участвует
      params.push(req.user.userId);
      where.push(`EXISTS (SELECT 1 FROM remont rm WHERE rm.zayavka_id = z.id AND rm.mekhanik_id = $${params.length})`);
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
  const { ts_id, tip_remonta_id, opisanie, prioritet } = req.body;
  if (!ts_id || !tip_remonta_id || !opisanie) return res.status(400).json({ error: 'ts_id, tip_remonta_id, opisanie required' });
  try {
    const r = await pool.query(`
      INSERT INTO zayavka (data_sozdaniya, sozdatel_id, ts_id, tip_remonta_id, opisanie, status_id, prioritet, data_rezhima)
      VALUES (NOW(), $1, $2, $3, $4, $5, $6, NOW()) RETURNING id
    `, [req.user.userId, ts_id, tip_remonta_id, opisanie, STATUS_NEW, prioritet || 3]);
    res.json({ success: true, id: r.rows[0].id });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
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

// Механик берёт заявку: создаёт remont, статус -> В работе
app.post('/api/zayavki/:id/take', auth, requireRole(ROLE_MECHANIC, ROLE_HEAD_MECHANIC), async (req, res) => {
  try {
    const zr = await pool.query('SELECT * FROM zayavka WHERE id = $1', [req.params.id]);
    if (!zr.rows.length) return res.status(404).json({ error: 'Not found' });
    if (zr.rows[0].status_id !== STATUS_NEW) return res.status(400).json({ error: 'Заявка уже взята или закрыта' });
    // ищем главмеха в том же подразделении (или любого)
    const gm = await pool.query('SELECT id FROM sotrudnik WHERE rol_id = $1 LIMIT 1', [ROLE_HEAD_MECHANIC]);
    const gmId = gm.rows[0]?.id || null;
    await pool.query(`
      INSERT INTO remont (zayavka_id, data_nachala, mekhanik_id, glavniy_mekhanik_id, stoimost_rabot, stoimost_zapchastey)
      VALUES ($1, NOW(), $2, $3, 0, 0)
    `, [req.params.id, req.user.userId, gmId]);
    await pool.query('UPDATE zayavka SET status_id = $1 WHERE id = $2', [STATUS_IN_PROGRESS, req.params.id]);
    res.json({ success: true });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

// Изменить статус заявки (механик)
app.post('/api/zayavki/:id/status', auth, async (req, res) => {
  const { status_id, kommentariy, itog, stoimost_rabot, stoimost_zapchastey } = req.body;
  if (!status_id) return res.status(400).json({ error: 'status_id required' });
  try {
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

app.put('/api/ts/:id', auth, requireRole(ROLE_HEAD_MECHANIC, ROLE_ADMIN), async (req, res) => {
  const { tekuschee_sostoyanie, probeg } = req.body;
  try {
    await pool.query(
      'UPDATE transportnoe_sredstvo SET tekuschee_sostoyanie = COALESCE($1, tekuschee_sostoyanie), probeg = COALESCE($2, probeg) WHERE id = $3',
      [tekuschee_sostoyanie, probeg, req.params.id]
    );
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
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
        (SELECT COUNT(*) FROM remont rm
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
  try {
    await pool.query('DELETE FROM sotrudnik WHERE id = $1', [req.params.id]);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
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
      WHERE f.komu_id = $1 OR f.ot_sotrudnika_id = $1 OR f.komu_id IS NULL
      ORDER BY f.data_sozdaniya DESC LIMIT 100
    `, [req.user.userId]);
    res.json({ messages: r.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/feedback', auth, async (req, res) => {
  const { soobshenie, komu_id, zayavka_id } = req.body;
  if (!soobshenie) return res.status(400).json({ error: 'soobshenie required' });
  try {
    let recipientId = komu_id;
    if (!recipientId) {
      // механик пишет — отправляем главмеху
      const gm = await pool.query('SELECT id FROM sotrudnik WHERE rol_id = $1 LIMIT 1', [ROLE_HEAD_MECHANIC]);
      recipientId = gm.rows[0]?.id || null;
    }
    await pool.query(
      'INSERT INTO mekhanik_feedback (ot_sotrudnika_id, komu_id, zayavka_id, soobshenie) VALUES ($1,$2,$3,$4)',
      [req.user.userId, recipientId, zayavka_id || null, soobshenie]
    );
    res.json({ success: true });
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

// ============ DEBUG ============
app.get('/api/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ status: 'OK' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Carvix API running on port ${PORT}`));
