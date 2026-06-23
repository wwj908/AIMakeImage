# MakeImage AI

Vue3 + Spring Boot + MySQL 8.0 + Redis 的 AI 生图网站基础版。

## 功能

- 用户注册、登录、JWT 鉴权
- 文生图、上传原图改图
- 生成图片下载
- 我的作品管理、公开/取消公开
- 公开作品广场，查看并复制他人公开提示词
- Redis 简单限流

当前后端使用本地 Java 渲染生成 PNG，便于完整跑通流程。后续接 OpenAI、Stable Diffusion、ComfyUI 等真实生图服务时，替换 `backend/src/main/java/com/makeimage/api/service/ImageRenderService.java` 即可。

## OpenAI 配置

项目已经预留 OpenAI 配置项，默认从环境变量 `OPENAI_API_KEY` 读取密钥，不会把真实密钥写入代码仓库。

当前配置位置：

- [application.yml](backend/src/main/resources/application.yml)
- [AppProperties.java](backend/src/main/java/com/makeimage/api/config/AppProperties.java)

已预置配置：

- `app.openai.base-url`: `https://ai.wwjin.site/`
- `app.openai.model`: `gpt-5.5`
- `app.openai.review-model`: `gpt-5.5`
- `app.openai.reasoning-effort`: `xhigh`
- `app.openai.disable-response-storage`: `true`
- `app.openai.api-key`: `${OPENAI_API_KEY:}`

Windows PowerShell 可这样设置当前终端环境变量后再启动后端：

```powershell
$env:OPENAI_API_KEY="your_api_key"
cd backend
mvn spring-boot:run
```

如果希望系统长期可用，可以把 `OPENAI_API_KEY` 配到系统环境变量中。

当 `app.openai.enabled=true` 且 `OPENAI_API_KEY` 已配置时，`ImageRenderService` 会调用 OpenAI 兼容的图片生成/编辑接口；如果配置缺失或接口调用失败，后端会直接返回明确错误提示，方便联调定位问题。

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

也可以执行 [docs/schema.sql](docs/schema.sql) 创建库表。Spring Boot 默认 `ddl-auto: update`，库存在即可自动建表。

默认数据库配置在 [application.yml](backend/src/main/resources/application.yml)：

- MySQL: `localhost:3306/make_image`
- 用户名: `root`
- 密码: `root`
- Redis: `localhost:6379`

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
- `POST /api/artworks/generate`
- `POST /api/artworks/edit`
- `GET /api/artworks/me`
- `PATCH /api/artworks/{id}/publish`
- `GET /api/public/artworks`
- `POST /api/public/artworks/{id}/download`
