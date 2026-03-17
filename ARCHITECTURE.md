# 系统架构（Architecture）

## 架构图

```
浏览器 (Frontend)
   │
   │  HTTP / JSON  (API_BASE = http://localhost:8081/water-quality/api)
   ▼
Docker
 ├── Tomcat 11 (容器内 8080 → 宿主机 8081)
 │     ├── REST API (Jakarta EE / JAX-RS)
 │     ├── Java Business Logic (Service / DAO)
 │     └── ETL Import Module (ImportCommunesTable, ImportPLV, ImportWaterData 等)
 │
 └── MySQL (容器内 3306 → 宿主机 3307)
        └── waterdb (communes, prelevements, resultats_analyses)
```

## 端口约定（统一）

| 用途           | 宿主机端口 | 容器内端口 | 说明 |
|----------------|------------|------------|------|
| **REST API**   | **8081**   | 8080       | 前端调用 `http://localhost:8081/water-quality/api` |
| **MySQL**      | **3307**   | 3306       | 本机直连用 `localhost:3307`；容器内应用用 `mysql:3306` |

- **前端** `frontend/app.js`：`API_BASE = 'http://localhost:8081/water-quality/api'`（固定 8081）
- **后端本地运行**（RunServer / Cargo）：监听 **8081**
- **Docker**：`docker-compose.yml` 映射 `8081:8080`、`3307:3306`

## 数据库连接

- **后端在 Docker 内运行**：通过环境变量 `DB_URL` 连接 `mysql:3306`（由 docker-compose 注入）
- **后端在本机运行**（RunServer / IDE）：未设置 `DB_URL` 时默认 `localhost:3307`

## 前端技术

- HTML, CSS, JavaScript, Leaflet.js
- 功能：地图、搜索、Pop-up、调用 API

## 后端技术

- Java, Jakarta EE (JAX-RS), Tomcat 11
- 功能：REST API、业务逻辑、查询数据库
- 示例 API：`GET /api/communes`、`GET /api/search`、`GET /api/details`、`GET /api/health` 等

## 数据导入（ETL）

- Java 程序读取 OpenData TXT/CSV，清洗后写入 MySQL
- 模块：ETL Import（如 `ImportCommunesTable`、`ImportPLV`、`ImportWaterData`）

## 项目目录（保留）

- `frontend/`：前端页面与静态资源
- `backend/`：Java 后端（API、Service、DAO、ETL）
- `database/`：建表脚本（schema.sql、reset_schema.sql）
- `data/`：原始数据（Table communes, prelevements, resultats 等）
- `docker/`：docker-compose 编排（Tomcat + MySQL）
