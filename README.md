# MakeImage AI

Vue3 + Spring Boot + MySQL 8.0 + Redis 的 AI 生图网站基础版。

## 功能

- 用户注册、登录、JWT 鉴权
- 文生图、上传原图改图
- 生成图片下载
- 我的作品管理、公开/取消公开
- 公开作品广场，查看并复制他人公开提示词
- Redis 简单限流

当前后端使用 OpenAI 兼容图片接口生成图片。系统名称、Logo、QQ 邮箱配置、OpenAI 渠道等业务配置均由数据库管理后台维护。

## 启动依赖

- JDK 21
- Maven 3.9+
- Node.js 20+
- MySQL 8.0
- Redis

## 数据库

```sql
CREATE DATABASE IF NOT EXISTS make_image DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
```

也可以执行 [docs/schema.sql](docs/schema.sql) 创建库表。Spring Boot 默认 `ddl-auto: update`，数据库存在即可自动建表。

默认数据库配置位于 [application.yml](backend/src/main/resources/application.yml)：

- MySQL: `localhost:3306/make_image`
- 用户名: `root`
- 密码: `123456`
- Redis: `localhost:6379`

## 配置说明

`application.yml` 现在只保留真正必须的启动配置：

- 数据库连接
- Redis 连接
- JWT 密钥与过期时间
- 文件存储目录
- 对外访问基础地址

以下业务配置不再从代码读取，统一改为数据库管理：

- 系统名、系统 Logo
- QQ 邮箱账号、授权码
- OpenAI 渠道列表、启用状态、排序、模型

如果数据库中没有配置对应业务项，系统会直接提示未配置，需要先在管理后台完成设置。

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端地址：`http://localhost:8080`

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：`http://localhost:5173`

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/email-code`
- `POST /api/artworks/generate`
- `POST /api/artworks/edit`
- `GET /api/artworks/me`
- `PATCH /api/artworks/{id}/publish`
- `GET /api/public/artworks`
- `POST /api/public/artworks/{id}/download`
- `GET /api/admin/openai-providers`
- `PUT /api/admin/openai-providers`
