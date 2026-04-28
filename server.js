const express = require('express');
const { Pool } = require('pg');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`, req.body);
  next();
});

const DB_URL = process.env.DATABASE_URL ||
  'postgresql://carvix:7o8t8yAFx4Ts2sPTSf8MTgBvLqERAnM9@dpg-d7ocin2qqhas73c2i93g-a/carvix';

console.log('Connecting to DB:', DB_URL.replace(/:([^@]+)@/, ':***@'));

const pool = new Pool({
  connectionString: DB_URL,
  ssl: { rejectUnauthorized: false }
});

async function initDB() {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        full_name VARCHAR(255) NOT NULL,
        login VARCHAR(100) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    console.log('Database initialized');
  } catch (e) {
    console.error('DB init error:', e.message);
  }
}
initDB();

app.post('/api/register', async (req, res) => {
  const { fullName, login, password } = req.body;
  if (!fullName || !login || !password) {
    return res.status(400).json({ error: 'All fields required' });
  }
  try {
    const existing = await pool.query('SELECT id FROM sotrudnik WHERE login = $1', [login]);
    if (existing.rows.length > 0) {
      return res.status(400).json({ error: 'Login already taken' });
    }
    const hash = await bcrypt.hash(password, 10);
    const insertRes = await pool.query(
      'INSERT INTO sotrudnik (fio, login, parol_hash, rol_id, podrazdelenie_id) VALUES ($1, $2, $3, 6, 1) RETURNING id',
      [fullName, login, hash]
    );
    console.log('User registered id:', insertRes.rows[0]?.id);
    res.json({ success: true, message: 'User registered', id: insertRes.rows[0]?.id });
  } catch (e) {
    console.error('Register error:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

app.post('/api/login', async (req, res) => {
  const { login, password } = req.body;
  if (!login || !password) {
    return res.status(400).json({ error: 'Login and password required' });
  }
  try {
    const result = await pool.query('SELECT * FROM sotrudnik WHERE login = $1', [login]);
    console.log('Login query rows:', result.rows.length);
    if (result.rows.length === 0) {
      return res.status(400).json({ error: 'Invalid credentials (user not found)' });
    }
    const user = result.rows[0];
    let valid = await bcrypt.compare(password, user.parol_hash);
    if (!valid && password === user.parol_hash) {
      valid = true; // fallback for old plaintext passwords
    }
    console.log('Password check valid:', valid, 'hash starts with:', user.parol_hash?.substring(0,7));
    if (!valid) {
      return res.status(400).json({ error: 'Invalid credentials (wrong password)' });
    }
    const token = jwt.sign(
      { userId: user.id, login: user.login },
      'carvix_secret_key_2024',
      { expiresIn: '7d' }
    );
    res.json({
      success: true,
      token,
      user: { fullName: user.fio, login: user.login, rolId: user.rol_id, podrazdelenieId: user.podrazdelenie_id }
    });
  } catch (e) {
    console.error('Login error:', e);
    res.status(500).json({ error: 'Server error' });
  }
});

app.get('/api/me', async (req, res) => {
  const auth = req.headers.authorization;
  if (!auth || !auth.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  try {
    const token = auth.replace('Bearer ', '');
    const decoded = jwt.verify(token, 'carvix_secret_key_2024');
    const result = await pool.query(
      'SELECT full_name, login FROM users WHERE id = $1',
      [decoded.userId]
    );
    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'User not found' });
    }
    res.json({ user: result.rows[0] });
  } catch (e) {
    res.status(401).json({ error: 'Invalid token' });
  }
});

app.get('/api/debug/tables', async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = 'public'
      ORDER BY table_name
    `);
    res.json({ tables: result.rows.map(r => r.table_name) });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/debug/schema', async (req, res) => {
  try {
    const usersSchema = await pool.query(`
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_name = 'users' AND table_schema = 'public'
      ORDER BY ordinal_position
    `);
    const sotrudnikSchema = await pool.query(`
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_name = 'sotrudnik' AND table_schema = 'public'
      ORDER BY ordinal_position
    `);
    res.json({
      users: usersSchema.rows,
      sotrudnik: sotrudnikSchema.rows
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/debug/sotrudnik', async (req, res) => {
  try {
    const result = await pool.query('SELECT id, fio, login, rol_id FROM sotrudnik ORDER BY id DESC LIMIT 10');
    res.json({ count: result.rows.length, users: result.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/debug/users', async (req, res) => {
  try {
    const result = await pool.query('SELECT id, login, full_name, created_at FROM users ORDER BY id DESC LIMIT 10');
    res.json({ count: result.rows.length, users: result.rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/health', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ status: 'OK', db: 'connected' });
  } catch (e) {
    res.status(500).json({ status: 'ERROR', db: e.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Carvix API running on port ${PORT}`));
