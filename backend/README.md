## 水质项目后端（water-quality-backend）

本模块是一个 **Jakarta EE + Jersey 的 REST 后端**，打成 WAR 部署到应用服务器（如 Tomcat）。前端通过 HTTP 调用它的 API 获取水质数据并绘制地图。

下面说明：

- 数据从哪里来（ETL → 数据库）
- 后端从数据库到 API 的数据流
- 前端如何调用这些 API
- 如何在本地启动这个后端

---

### 1. 数据来源与导入（ETL）

#### 1.1 原始数据文件

- 采样数据文本：`DIS_PLV_2025.txt`
  - 建议放在：`backend/data/` 或项目 `data/` 目录
- 行政边界 GeoJSON：
  - `frontend/data/communes.geojson`（市镇边界）

#### 1.2 导入程序

- 类：`com.waterquality.etl.ImportPLV`
- 入口：`public static void main(String[] args)`

运行逻辑（简化版）：

1. 读取采样文件路径（默认 `data/DIS_PLV_2025.txt`，也可以通过 `args[0]` 传入）。
2. 可选：读取 `frontend/data/communes.geojson`，为每个 INSEE 代码计算一个经纬度质心。
3. 逐行解析 `DIS_PLV_2025.txt`，按 CSV 列位取出：
   - 部门、网络代码、INSEE 代码、commune 名称
   - 采样日期/时间、结论、运营方等元数据
4. 使用 JDBC（`Database.getConnection()`）写入数据库：
   - 如果 `communes` 中不存在该 `code_insee`，插入一条带经纬度的市镇记录。
   - 在 `prelevements` 中插入一条采样记录（`dateprel`、`conclusionprel` 等）。
5. 批量提交（每 1000 行提交一次）。

**导入后应至少包含以下表：**

- `communes`：市镇基本信息（INSEE 代码、名称、经纬度、部门）
- `prelevements`：每次水质采样记录
- `resultats_analyses`：单次采样的详细指标（由其他导入逻辑填充）

---

### 2. 从数据库到 REST API 的数据流

#### 2.1 DAO 层（访问数据库）

- `CommuneDao`
  - `findAll(int limit)`：从 `communes` 表读取市镇列表（代码、名称、经纬度、部门）。
  - `search(String query, int limit)`：按名称搜索市镇。
  - `findByCodeInsee(String codeInsee)`：按 INSEE 代码查单个市镇。
- `PrelevementDao`
  - `findLatestByCommune(String codeInsee)`：某市镇最新采样。
  - `findLatestByCommuneAndFilters(String codeInsee, Integer year, String pollutant)`：按年份和污染物过滤后的最新采样。
  - `findLatestByCommuneAndFilters(Integer year, String pollutant)`：一次性返回所有市镇的最新采样映射 `code_insee → Prelevement`。
- `AnalysisResultDao`
  - `findByPrelevementId(int prelevementId)`：从 `resultats_analyses` 表读取一条采样对应的全部分析结果。

#### 2.2 Service 层（组装颜色与业务字段）

- 类：`CommuneService`

核心职责：

- 基于 DAO 取到 `Commune` 和对应的最新 `Prelevement`。
- 使用 `WaterQualityColorService` 计算：
  - 地图/标记颜色：`color`
  - 文本状态：`status`（如“Conforme”、“Alerte chimique”等）。
- 暴露两个主要数据视图：
  - **点视图（markers）**：直接返回 `List<Commune>`，每个对象带经纬度+颜色+状态。
  - **区域视图（choropleth）**：返回 `MapResponse`，其中 `features` 是一组 `MapFeature`。

主要方法：

- `getAll(Integer year, String pollutant)`：
  - 调用 `CommuneDao.findAll(5000)`。
  - 对每个 `Commune` 调用 `enrich(commune, year, pollutant)` 填充 `color` + `status`。
- `search(String query, Integer year, String pollutant)`：
  - 用 `CommuneDao.search` 搜索，再通过 `enrich` 填充颜色和状态。
- `getByCodeInsee(String codeInsee, Integer year, String pollutant)`：
  - 用 `CommuneDao.findByCodeInsee` 查单个市镇，并通过 `enrich` 填充。
- `getMapData(String mode, Integer year, String pollutant)`：
  - 默认 `mode = "regions"`。
  - 调用 `CommuneDao.findAll(20000)` 获取所有市镇。
  - 调用 `PrelevementDao.findLatestByCommuneAndFilters(year, pollutant)` 获取每个市镇的最新采样。
  - 把二者组合为 `List<MapFeature>`，填入 `MapResponse.features` 并返回。

辅助方法：

- `enrich(Commune commune, Integer year, String pollutant)`：
  - 查找该 commune 最新的 `Prelevement`。
  - 若存在：
    - `commune.color = colorService.getColor(prelevement)`
    - `commune.status =` 结论或 tone 名称。
  - 若不存在：
    - `color = "#9ca3af"`（灰色）
    - `status = "Aucune donnée"`。

---

### 3. REST API 层（对前端暴露的 HTTP 接口）

- 类：`com.waterquality.api.CommuneResource`
- 顶层路径：`@Path("/")`，在 `web.xml` 中由 Jersey 映射到 `/api/*`。

主要端点：

1. `GET /water-quality/api/communes`
   - 方法：`getAll(Integer year, String pollutant)`
   - 调用：`communeService.getAll(year, pollutant)`
   - 返回：`List<Commune>`（用于前端点模式的 marker）。
2. `GET /water-quality/api/map-data`
   - 方法：`getMapData(String mode, Integer year, String pollutant)`
   - 调用：`communeService.getMapData(mode, year, pollutant)`
   - 返回：`MapResponse`（`features` 为 `List<MapFeature>`，用于前端 choropleth）。
3. `GET /water-quality/api/search`
   - 方法：`search(String q, Integer year, String pollutant)`
   - 调用：`communeService.search(q, year, pollutant)`
   - 返回：`List<Commune>`（前端搜索结果列表）。
4. `GET /water-quality/api/communes/{id}`
   - 方法：`getCommune(String codeInsee, Integer year, String pollutant)`
   - 返回：单个 `Commune`。
5. `GET /water-quality/api/details/{prelevementId}`
   - 方法：`getDetails(int prelevementId)`
   - 调用：`analysisResultDao.findByPrelevementId(prelevementId)`
   - 返回：`List<AnalysisResult>`（某次采样的全部分析项）。
6. `GET /water-quality/api/latest/{communeId}`
   - 方法：`getLatestPrelevement(String codeInsee, Integer year, String pollutant)`
   - 调用：`prelevementDao.findLatestByCommuneAndFilters(codeInsee, year, pollutant)`
   - 返回：`{"prelevement": Prelevement, "tone": "<texte>"}`。

---

### 4. 前端如何消费这些 API

前端代码位于项目根目录下的 `frontend/`：

- 入口页面：`frontend/index.html`（通过 `<iframe src="embed.html">` 加载地图）
- 地图逻辑：`frontend/app.js`

关键常量：

```js
const API_BASE = 'http://localhost:8081/water-quality/api';
```

主要交互：

- 加载地图区域视图（默认模式）：
  - `GET ${API_BASE}/map-data?mode=regions&year=...&pollutant=...`
  - 使用 `MapResponse.features` 与本地 `communes.geojson` / `departements.geojson` 对齐，渲染 choropleth。
- 点模式（markers）：
  - `GET ${API_BASE}/communes?year=...&pollutant=...`
  - 每个 `Commune` 在地图上画一个圆点，颜色和文本来自 `color`/`status`。
- 搜索：
  - `GET ${API_BASE}/search?q=<texte>&year=...&pollutant=...`
  - 返回的 `Commune` 列表用于自动完成；点击后触发详情视图。
- 详情面板：
  - 最新 prélèvement：
    - `GET ${API_BASE}/latest/{codeInsee}?year=...&pollutant=...`
  - 同一次 prélèvement 的分析结果：
    - `GET ${API_BASE}/details/{prelevementId}`

---

### 5. 为什么没有 `main` 函数？如何启动后端

这个后端是一个 **标准的 Java Web 应用（WAR）**，不是 `public static void main` 那种独立可执行程序。  
入口由 **应用服务器容器** 管理，而不是你自己写的 `main`。

关键点：

- `pom.xml` 中：
  - `<packaging>war</packaging>`
  - `<finalName>water-quality</finalName>` → 生成 `water-quality.war`
- `src/main/webapp/WEB-INF/web.xml` 中：
  - 配置了 Jersey 的 `ServletContainer`，并映射到 `/api/*`。
  - 把 `com.waterquality.api` 包里的 JAX-RS 资源（如 `CommuneResource`）暴露成 REST API。

#### 5.1 使用 Tomcat 启动（推荐）

1. **打包 WAR**

   在 `backend/` 目录下执行：

   ```bash
   mvn clean package
   ```

   结束后在：

   - `backend/target/water-quality.war`

2. **部署到 Tomcat**

   - 把 `water-quality.war` 复制到 Tomcat 的 `webapps/` 目录。
   - 启动 Tomcat（Docker 映射宿主机端口 8081）。

3. **访问 API**

   - 健康检查（示例）：
     - `http://localhost:8081/water-quality/api/communes`
     - `http://localhost:8081/water-quality/api/map-data`

   确认能返回 JSON 即表示后端 REST 已经工作。

#### 5.2 使用其他 Jakarta EE 服务器

也可以部署到 Payara, WildFly 等任意支持 Jakarta EE + Servlet/JAX-RS 的服务器方式相同：

1. `mvn package` 生成 WAR。
2. 将 `water-quality.war` 部署到对应服务器。
3. 确认 context path 为 `/water-quality` 或相应配置，然后调整前端 `API_BASE`。

---

### 6. 本地开发常用步骤（后端 + 前端）

1. **准备数据库**：创建 schema，确保有必要的表（`communes`、`prelevements`、`resultats_analyses` 等），并在 `Database` 工具类中配置正确的 JDBC URL/用户/密码。
2. **导入数据**：
   - 把 `DIS_PLV_2025.txt` 放到 `backend/data/`。
   - 通过 IDE 或命令行运行 `com.waterquality.etl.ImportPLV.main()`。
3. **打包并启动后端**：
   - 在 `backend/` 执行 `mvn clean package`，将 `water-quality.war` 部署到 Tomcat。
4. **打开前端**：
   - 用任意静态服务器或直接浏览器打开 `frontend/index.html`。
   - 保证 `API_BASE` 和后端部署地址一致（默认是 `http://localhost:8081/water-quality/api`）。
5. **使用界面**：
   - 选择年份和污染物过滤器（会影响后端查询逻辑和地图颜色）。
   - 通过搜索或点击地图查看市镇详情及分析结果。

