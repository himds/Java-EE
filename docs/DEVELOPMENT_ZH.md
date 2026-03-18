# newEAU — 开发文档（中文）

## 1. 项目简介

newEAU 是一个**水质可视化 Web 应用**，参考 [dansmoneau.fr](https://dansmoneau.fr)，提供法国本土与海外省（DROM）的行政区划地图，按市镇展示饮用水合规状态（绿/黄/橙/红），并支持搜索、详情与色盲模式。

- **后端**：Java + Jakarta Servlet + MySQL，提供 REST 风格 API。
- **前端**：HTML/CSS/JavaScript + Leaflet 地图，单页式交互。
- **数据**：市镇与部门边界来自 GeoJSON；合规颜色由数据库中的 prélèvement 四个合规字段计算。

---

## 2. 技术栈

| 层级     | 技术 |
|----------|------|
| 后端     | Java 8+、Jakarta Servlet API 6、Maven、MySQL 8 (JDBC) |
| 前端     | 原生 HTML5 / CSS3 / JavaScript、Leaflet 1.9.4 |
| 构建     | Maven（packaging: war） |
| 部署     | Tomcat 或兼容 Servlet 的容器 |

---

## 3. 项目结构

```
newEAU/
├── pom.xml
├── database/
│   └── schema.sql              # 建表脚本（communes, prelevements, resultats_analyses）
├── datas/                       # 原始数据（CSV/TXT 导入用）
│   ├── Table communes/
│   ├── Table prelevements/
│   └── Table resultats/
├── docs/
│   ├── DEVELOPMENT_ZH.md       # 本文档（中文）
│   └── DEVELOPMENT_FR.md      # 开发文档（法语）
├── src/main/
│   ├── java/com/waterquality/
│   │   ├── controller/         # Servlet（API 入口）
│   │   │   ├── DetailsServlet.java   # GET /api/details/{id}
│   │   │   ├── LatestServlet.java    # GET /api/latest/{codeInsee}
│   │   │   ├── MapDataServlet.java   # GET /api/map-data
│   │   │   ├── SearchServlet.java    # GET /api/search?q=
│   │   │   └── TestServlet.java     # GET /api/test
│   │   ├── dao/
│   │   │   └── DatabaseConnection.java
│   │   ├── model/
│   │   │   ├── Commune.java
│   │   │   ├── Prelevement.java
│   │   │   └── ResultatAnalyse.java
│   │   ├── service/
│   │   │   ├── DetailsService.java
│   │   │   ├── LatestService.java
│   │   │   ├── MapDataService.java
│   │   │   └── SearchService.java
│   │   └── util/
│   │       ├── ConformityColor.java  # 四字段 → 颜色/状态
│   │       └── ImportWaterData.java  # 数据导入（main）
│   └── webapp/
│       ├── WEB-INF/web.xml
│       ├── data/
│       │   ├── README.md
│       │   ├── departements-drom.geojson
│       │   └── communes-drom.geojson
│       ├── embed.html          # 地图页（iframe 或直接打开）
│       ├── index.html          # 落地页（含 iframe）
│       ├── app.js              # 地图、搜索、图例、色盲模式
│       └── style.css
└── target/                     # Maven 构建输出（war）
```

---

## 4. 数据库

### 4.1 表结构

- **communes**：市镇（code_insee, nom_commune, departement）
- **prelevements**：水样检测记录（code_insee, referenceprel, dateprel，及四个合规字段）
- **resultats_analyses**：每次检测的指标（prelevement_id, parametre, valeur_mesuree, limite_legale）

详见 `database/schema.sql`。

### 4.2 连接配置

`DatabaseConnection.java` 默认连接：

- URL: `jdbc:mysql://localhost:3307/waterdb?...`
- 用户/密码：可通过环境变量 `DB_URL`、`DB_USER`、`DB_PASSWORD` 覆盖。

---

## 5. API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/test` | 连通性测试 |
| GET | `/api/search?q=xxx` | 按市镇名模糊搜索，返回 JSON 数组 |
| GET | `/api/map-data` | 所有市镇的 id、name、departement、color、status（用于地图着色） |
| GET | `/api/latest/{codeInsee}` | 该市镇最新 prélèvement + commune 信息（含 color） |
| GET | `/api/details/{prelevementId}` | 该 prélèvement 的 referenceprel 及 analyses 列表（parametre, valeurMesuree, limiteLegale） |

API 根路径由前端通过 URL 参数 `api` 指定，例如：`embed.html?api=http://localhost:8081/newEAU/api`。

---

## 6. 颜色逻辑（合规 → 地图颜色）

由 `ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim)` 根据四个字段（C/N）计算：

| 条件 | 颜色 | 含义 |
|------|------|------|
| 四字段均为 C | 绿 | 全部合格 |
| 参考合格 + 指标异常 | 黄 | 健康合格、指标异常 |
| 细菌合格 + 化学不合格 | 橙 | 细菌 OK、化学不 OK |
| 无数据 | 灰 | 无 prélèvement 或四字段全空 |
| 其他 | 红 | 非合格 |

地图仅在使用「市镇」视图时按该颜色着色；部门层初始为灰色，点击部门后展开的市镇层才显示上述颜色。

---

## 7. 前端功能概览

- **地图**：Leaflet，底图 OSM；先加载部门层（灰），点击部门后加载该部门市镇层并按 map-data 着色。
- **领土**：侧栏左侧图标切换 Métropole / Guadeloupe / Martinique / Guyane / La Réunion / Mayotte，fitBounds 定位。
- **搜索**：防抖请求 `/api/search`，下拉补全，选中后定位并打开右侧详情。
- **右侧面板**：市镇/部门信息、最新 prélèvement、Analyses détaillées（referenceprel + parametre / valeurMesuree / limiteLegale）。
- **图例**：左下角 Légende，含「Mode daltonien」色盲模式开关；开启后使用色盲友好配色并刷新地图与徽章。

---

## 8. GeoJSON 数据

- **可选**：`data/departements.geojson`、`data/communes.geojson`（本土，可从 [Contours administratifs](https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/latest/geojson/) 下载）。
- **内置**：`data/departements-drom.geojson`、`data/communes-drom.geojson`（海外省简化几何）。

前端会合并主文件与 DROM 文件的 features，统一用于部门层与市镇层。属性需含 `code`（或 `code_insee`）、`nom`。

---

## 9. 运行与部署

### 9.1 本地运行

1. MySQL：创建数据库 `waterdb`，执行 `database/schema.sql`；可选运行 `ImportWaterData` 导入数据。
2. 构建：`mvn clean package`，生成 `target/newEAU-1.0-SNAPSHOT.war`。
3. 部署：将 war 放入 Tomcat 的 `webapps/`，或使用 IDE 配置 Tomcat 运行。
4. 前端：浏览器打开 `http://localhost:8080/newEAU/embed.html`（端口与上下文路径以实际为准）；若 API 在不同端口，使用 `?api=http://...` 指定。

### 9.2 上下文路径

默认访问路径为 `/newEAU/`（与 artifactId 一致），API 基址为 `http://<host>:<port>/newEAU/api`。

---

## 10. 色盲模式

- 开关位于左下角图例「Légende — Qualité de l'eau」内，标签为「Mode daltonien」。
- 开启后：地图填充色、图例色块、右侧状态徽章统一切换为色盲友好配色（蓝/黄/橙/红橙等），并立即刷新当前部门层与市镇层。

---

## 11. 版本与依赖

- 项目版本：1.0-SNAPSHOT（见 `pom.xml`）。
- 主要依赖：jakarta.servlet-api 6.0.0、mysql-connector-j 8.2.0、Leaflet 1.9.4（前端 CDN）。
