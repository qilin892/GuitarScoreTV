# 部署到服务器（子路径 `/guitar/`）

把服务端挂在已有站点的子路径下，不占用整台服务器、也不影响同域其他服务：

```text
https://你的域名/guitar/  →  Nginx（/guitar/ 前缀反代）
                          →  127.0.0.1:8098  Node 进程（systemd）
                          →  /srv/guitartv/data
```

## 前置条件

- Node.js 18+；
- Nginx（已有站点与 HTTPS 证书更省事）；
- 一个可写入的数据目录，例如 `/srv/guitartv/data`；
- 运行服务的普通用户，例如 `www`。

## 1. 放代码

```bash
mkdir -p /srv/guitartv
# 把仓库里的 server/ 内容拷进去（server.js、package.json、public/）
cp -r server/* /srv/guitartv/
cd /srv/guitartv
npm install --omit=dev
mkdir -p data/uploads
chown -R www:www /srv/guitartv
```

## 2. 常驻进程

`deploy/guitartv.service` 已按 `BASE_PATH=/guitar` 配好，按实际路径改一下 `WorkingDirectory`、
`DATA_DIR`、`ExecStart` 里的目录即可：

```bash
cp deploy/guitartv.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now guitartv
systemctl status guitartv --no-pager
```

日志：`journalctl -u guitartv -n 50 --no-pager`。

## 3. Nginx 反代

把 `deploy/nginx-guitar.conf` 里的内容放进站点 `server { }` 内。用宝塔面板时，
建议放到 `vhost/nginx/extension/<域名>/guitar.conf`，避免面板重写站点主配置时被覆盖。

```bash
nginx -t && nginx -s reload
```

**两个关键点**：

1. `proxy_pass http://127.0.0.1:8098;` 末尾**不要**加斜杠，否则 Nginx 会剥掉 `/guitar` 前缀，
   Node 端路由就对不上了；
2. Node 进程必须带 `BASE_PATH=/guitar` 启动，页面里的接口、图片地址才会带同一前缀。

## 4. 验证

```bash
curl -s http://127.0.0.1:8098/guitar/api/health        # 服务本身
curl -s https://你的域名/guitar/api/health             # 经过 Nginx
curl -s https://你的域名/guitar/api/songs
```

浏览器打开 `https://你的域名/guitar/`，能看到后台、能上传一张琴谱图片即部署成功。
电视端服务器地址填 `https://你的域名/guitar`（不要带结尾斜杠）。

## 5. 更新

```bash
# 覆盖 server.js / public/ 后
systemctl restart guitartv
```

改了 Nginx 片段记得 `nginx -t && nginx -s reload`。

## 6. 备份

```bash
tar czf guitartv-$(date +%F).tar.gz -C /srv/guitartv data
```

`songs.json` 与 `uploads/` 就是全部内容；恢复时解回原位置并重新 `chown`。
不要在服务运行中手工编辑 `songs.json`。

## 排错

| 现象 | 原因 |
|---|---|
| 访问 `/guitar/` 一直 308 / 301 循环 | Nginx 把前缀剥掉了，`proxy_pass` 末尾多了 `/`；或 Node 启动时 `BASE_PATH` 与反代前缀不一致 |
| 页面能开但列表报错 | 前端请求落到了站点根路径的其他服务上，检查是否用仓库自带的 `public/`（内部走相对路径） |
| 图片 404 | 返回地址带了 `/guitar` 前缀但静态目录没挂到该前缀，确认 `BASE_PATH` 与 `DATA_DIR/uploads` 都存在 |
| 上传报 413 | Nginx `client_max_body_size` 太小，见片段里的 `100m` |
