const form = document.querySelector('#song-form');
const list = document.querySelector('#song-list');
const count = document.querySelector('#song-count');
const status = document.querySelector('#form-status');
const tokenInput = document.querySelector('#admin-token');

// 同一套后台既能根路径部署，也能挂在子路径（如 /guitar/）下反代，接口一律按当前页面目录取址。
const BASE = (() => {
  const path = location.pathname;
  return path.endsWith('/') ? path : path.slice(0, path.lastIndexOf('/') + 1);
})();

async function api(pathname, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (tokenInput.value) headers['X-Admin-Token'] = tokenInput.value;
  const response = await fetch(BASE + pathname, { ...options, headers });
  const data = await response.json();
  if (!response.ok) {
    if (response.status === 401) tokenInput.hidden = false;
    throw new Error(data.error || '操作失败');
  }
  return data;
}

function songElement(song) {
  const article = document.createElement('article');
  article.className = 'song';
  const image = document.createElement('img');
  image.className = 'song-thumb';
  image.src = song.pages[0].url;
  image.alt = `${song.title}第一页琴谱`;
  const body = document.createElement('div');
  const title = document.createElement('h3');
  title.textContent = song.title;
  const meta = document.createElement('p');
  meta.className = 'song-meta';
  meta.textContent = `${song.artist || '未填写作者'} · ${song.pages.length} 页 · ${Math.ceil(song.pages.length / 2)} 屏`;
  body.append(title, meta);
  const actions = document.createElement('div');
  actions.className = 'song-actions';
  const add = document.createElement('label');
  add.textContent = '追加琴谱';
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/jpeg,image/png,image/webp';
  input.multiple = true;
  input.addEventListener('change', async () => {
    if (!input.files.length) return;
    const data = new FormData();
    [...input.files].forEach(file => data.append('images', file));
    add.textContent = '上传中…';
    try { await api(`api/songs/${song.id}/images`, { method: 'POST', body: data }); await loadSongs(); }
    catch (error) { alert(error.message); add.textContent = '追加琴谱'; }
  });
  add.append(input);
  const remove = document.createElement('button');
  remove.className = 'text-button';
  remove.textContent = '删除歌曲';
  remove.addEventListener('click', async () => {
    if (!confirm(`确定删除《${song.title}》及全部琴谱图片？`)) return;
    try { await api(`api/songs/${song.id}`, { method: 'DELETE' }); await loadSongs(); }
    catch (error) { alert(error.message); }
  });
  actions.append(add, remove);
  article.append(image, body, actions);
  return article;
}

async function loadSongs() {
  try {
    const songs = await api('api/songs');
    count.textContent = `${songs.length} 首`;
    list.replaceChildren();
    if (!songs.length) {
      const empty = document.createElement('p');
      empty.className = 'empty';
      empty.textContent = '曲库还是空的。从左侧创建第一首歌曲。';
      list.append(empty);
      return;
    }
    songs.forEach(song => list.append(songElement(song)));
  } catch (error) {
    list.innerHTML = '<p class="empty error">曲库读取失败，请检查服务器是否运行。</p>';
  }
}

document.querySelector('#images').addEventListener('change', event => {
  const files = [...event.target.files];
  document.querySelector('#file-summary').textContent = files.length ? `已选 ${files.length} 张：${files.map(file => file.name).join('、')}` : 'JPG、PNG、WebP；按选择顺序排页';
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  const button = form.querySelector('button');
  button.disabled = true;
  status.textContent = '正在上传，请勿关闭页面…';
  try {
    await api('api/songs', { method: 'POST', body: new FormData(form) });
    form.reset();
    document.querySelector('#file-summary').textContent = 'JPG、PNG、WebP；按选择顺序排页';
    status.textContent = '歌曲已创建，电视端刷新后即可看到。';
    await loadSongs();
  } catch (error) { status.textContent = error.message; }
  finally { button.disabled = false; }
});

function showServerAddress() {
  const label = document.querySelector('#server-address');
  // 电视端要填的地址就是当前后台地址去掉结尾斜杠，局域网与线上域名通用。
  label.textContent = location.origin + BASE.replace(/\/$/, '');
}

showServerAddress();
loadSongs();
