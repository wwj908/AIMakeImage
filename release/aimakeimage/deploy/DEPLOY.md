# AIMakeImage 部署说明

目标域名：`https://image.wwjin.site`

推荐后端端口：`18080`

部署目录：

- 前端：`/opt/aimakeimage/frontend`
- 后端：`/opt/aimakeimage/backend`
- 存储：`/opt/aimakeimage/storage`
- 环境变量：`/etc/aimakeimage/aimakeimage.env`

## 服务器侧准备

```bash
sudo useradd --system --create-home --home-dir /opt/aimakeimage --shell /usr/sbin/nologin aimakeimage || true
sudo mkdir -p /opt/aimakeimage/frontend /opt/aimakeimage/backend /opt/aimakeimage/storage /etc/aimakeimage
sudo chown -R aimakeimage:aimakeimage /opt/aimakeimage
sudo cp /tmp/aimakeimage.env /etc/aimakeimage/aimakeimage.env
sudo chmod 600 /etc/aimakeimage/aimakeimage.env
```

## 数据库

```sql
CREATE DATABASE IF NOT EXISTS make_image DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'make_image'@'localhost' IDENTIFIED BY 'change-this-password';
GRANT ALL PRIVILEGES ON make_image.* TO 'make_image'@'localhost';
FLUSH PRIVILEGES;
```

## systemd

```bash
sudo cp /tmp/aimakeimage.service /etc/systemd/system/aimakeimage.service
sudo systemctl daemon-reload
sudo systemctl enable --now aimakeimage
sudo systemctl status aimakeimage
```

## Nginx

```bash
sudo cp /tmp/nginx-image.wwjin.site.conf /etc/nginx/sites-available/image.wwjin.site
sudo ln -sf /etc/nginx/sites-available/image.wwjin.site /etc/nginx/sites-enabled/image.wwjin.site
sudo nginx -t
sudo systemctl reload nginx
```

如果服务器使用的是 `/etc/nginx/conf.d`，则放到：

```bash
sudo cp /tmp/nginx-image.wwjin.site.conf /etc/nginx/conf.d/image.wwjin.site.conf
sudo nginx -t
sudo systemctl reload nginx
```

## HTTPS

```bash
sudo certbot --nginx -d image.wwjin.site
```

## 健康检查

```bash
curl -I http://127.0.0.1:18080/api/public/system
curl -I http://image.wwjin.site
```
