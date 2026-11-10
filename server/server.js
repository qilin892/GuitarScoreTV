const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const app = express();
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';
const ROOT = __dirname;
const DATA_DIR = process.env.DATA_DIR ? path.resolve(process.env.DATA_DIR) : path.join(ROOT, 'data');
const UPLOAD_DIR = path.join(DATA_DIR, 'uploads');
const DB_FILE = path.join(DATA_DIR, 'songs.json');
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || '';

// 反向代理到域名子路径（例如 /guitar）时通过 BASE_PATH 指定，站点内部路由与图片地址都会带上该前缀。
const BASE_PATH = (() => {
  const raw = String(process.env.BASE_PATH || '').trim();
  if (!raw || raw === '/') return '';
  const trimmed = raw.replace(/\/+$/, '');
  return trimmed.startsWith('/') ? trimmed : '/' + trimmed;
})();

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
if (!fs.existsSync(DB_FILE)) fs.writeFileSync(DB_FILE, '[]\n');

function readSongs() {
  try {
    const data = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('读取歌曲数据库失败：', error);
    return [];
  }
}

function writeSongs(songs) {
  const temp = DB_FILE + '.tmp';
  fs.writeFileSync(temp, JSON.stringify(songs, null, 2) + '\n');
  fs.renameSync(temp, DB_FILE);
}

function cleanText(value, maxLength = 100) {
  return String(value || '').trim().slice(0, maxLength);
}

function decodeUploadName(name) {
  if (!name || /[^\x00-\xff]/.test(name)) return name;
  try {
    const decoded = Buffer.from(name, 'latin1').toString('utf8');
    return decoded.includes('\ufffd') ? name : decoded;
  } catch {
    return name;
  }
}

function publicSong(song) {
  return {
    id: song.id,
    title: song.title,
    artist: song.artist,
    createdAt: song.createdAt,
    pages: song.pages.map((page, index) => ({
      number: index + 1,
      url: BASE_PATH + '/uploads/' + encodeURIComponent(page.file),
      originalName: decodeUploadName(page.originalName)
    }))
  };
}

function removeUploaded(files) {
  for (const file of files || []) {
    fs.promises.unlink(file.path).catch(() => {});
  }
}

const imageExtensions = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp'
};

const storage = multer.diskStorage({
  destination: UPLOAD_DIR,
  filename(req, file, callback) {
    callback(null, crypto.randomUUID() + imageExtensions[file.mimetype]);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 20 * 1024 * 1024, files: 60 },
  fileFilter(req, file, callback) {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    callback(null, allowed.includes(file.mimetype));
  }
});

const site = express.Router();

site.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'same-origin');
  res.setHeader('X-Frame-Options', 'DENY');
  if (req.path.startsWith('/api/')) res.setHeader('Cache-Control', 'no-store');
  if (!req.path.startsWith('/api/') && !req.path.startsWith('/uploads/')) {
    res.setHeader('Content-Security-Policy',
      "default-src 'self'; img-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; script-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
  }
  next();
});
site.use(express.json({ limit: '64kb' }));
site.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '7d', immutable: true }));
site.use(express.static(path.join(ROOT, 'public')));

site.get('/api/health', (req, res) => {
  res.json({ ok: true, service: 'GuitarScoreTV', basePath: BASE_PATH || '/', time: new Date().toISOString() });
});

site.get('/api/server-info', (req, res) => {
  const addresses = [];
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries || []) {
      if (entry.family === 'IPv4' && !entry.internal) addresses.push(entry.address);
    }
  }
  res.json({ port: PORT, basePath: BASE_PATH || '/', addresses });
});

site.get('/api/songs', (req, res) => {
  const songs = readSongs().sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  res.json(songs.map(publicSong));
});

site.get('/api/songs/:id', (req, res) => {
  const song = readSongs().find(item => item.id === req.params.id);
  if (!song) return res.status(404).json({ error: '歌曲不存在' });
  res.json(publicSong(song));
});

function requireAdmin(req, res, next) {
  if (!ADMIN_TOKEN) return next();
  if (req.get('X-Admin-Token') !== ADMIN_TOKEN) {
    return res.status(401).json({ error: '管理密码错误' });
  }
  next();
}

site.post('/api/songs', requireAdmin, upload.array('images', 60), (req, res) => {
  const title = cleanText(req.body.title);
  const artist = cleanText(req.body.artist);
  if (!title || !req.files || req.files.length === 0) {
    removeUploaded(req.files);
    return res.status(400).json({ error: '请填写歌曲名称，并至少上传一张琴谱图片' });
  }
  const songs = readSongs();
  const song = {
    id: crypto.randomUUID(),
    title,
    artist,
    createdAt: new Date().toISOString(),
    pages: req.files.map(file => ({ file: file.filename, originalName: decodeUploadName(file.originalname) }))
  };
  songs.push(song);
  writeSongs(songs);
  res.status(201).json(publicSong(song));
});

site.post('/api/songs/:id/images', requireAdmin, upload.array('images', 60), (req, res) => {
  const songs = readSongs();
  const song = songs.find(item => item.id === req.params.id);
  if (!song || !req.files || req.files.length === 0) {
    removeUploaded(req.files);
    return res.status(400).json({ error: '歌曲不存在或未选择图片' });
  }
  song.pages.push(...req.files.map(file => ({ file: file.filename, originalName: decodeUploadName(file.originalname) })));
  writeSongs(songs);
  res.json(publicSong(song));
});

site.delete('/api/songs/:id', requireAdmin, (req, res) => {
  const songs = readSongs();
  const index = songs.findIndex(item => item.id === req.params.id);
  if (index < 0) return res.status(404).json({ error: '歌曲不存在' });
  const [song] = songs.splice(index, 1);
  writeSongs(songs);
  for (const page of song.pages) {
    fs.promises.unlink(path.join(UPLOAD_DIR, path.basename(page.file))).catch(() => {});
  }
  res.json({ ok: true });
});

site.use('/api', (req, res) => res.status(404).json({ error: '接口不存在' }));
site.use((error, req, res, next) => {
  console.error(error);
  removeUploaded(req.files);
  const message = error instanceof multer.MulterError ? '上传失败：单张图片不能超过 20MB，最多 60 张' : '服务器处理失败';
  res.status(500).json({ error: message });
});

app.disable('x-powered-by');

if (BASE_PATH) {
  // 子路径部署：/guitar 精确跳到 /guitar/，站内相对路径才成立。
  // 注意不能写成 app.get(BASE_PATH)，Express 默认非严格路由会连 /guitar/ 一起匹配，造成重定向死循环。
  app.use((req, res, next) => {
    if (req.path === BASE_PATH) return res.redirect(308, BASE_PATH + '/');
    next();
  });
  app.use(BASE_PATH, site);
} else {
  app.use('/', site);
}

app.listen(PORT, HOST, () => {
  console.log(`GuitarScoreTV 服务已启动：http://${HOST}:${PORT}${BASE_PATH || ''}/`);
  console.log(`数据目录：${DATA_DIR}`);
  if (!BASE_PATH) console.log('局域网使用时，电视需填写电脑局域网 IP，不能使用 localhost。');
});
