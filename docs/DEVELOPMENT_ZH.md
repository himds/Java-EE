# newEAU — 开发文档（中文，数据流版）

## 1. 文档目标

本文重点回答四件事：

1. 项目代码结构如何组织；
2. 每个模块各自做什么；
3. 后端数据如何流向前端；
4. 地图颜色如何由后端结果驱动显示。

---

## 2. 整体架构（一句话）

`MySQL -> Service -> Servlet(JSON API) -> app.js(fetch) -> Leaflet GeoJSON 图层着色`

前端拿到的是“每个市镇的状态与颜色”，然后按 `code_insee` 与 GeoJSON 多边形做关联，最终上色。

---

## 3. 目录与模块职责

### 3.1 后端（`src/main/java/com/waterquality`）

- `controller/`：HTTP 入口（Servlet）
  - `MapDataServlet`：返回地图着色数据
  - `SearchServlet`：搜索城市
  - `LatestServlet`：取某城市最新检测
  - `DetailsServlet`：取某次 prélèvement 的详细指标
- `service/`：业务查询与组装
  - `MapDataService`：主地图数据
  - `LatestService`：详情面板头部数据
  - `DetailsService`：详情面板指标数据
  - `SearchService`：城市名搜索
- `util/ConformityColor`：四个合规字段 -> 颜色/状态文本
- `dao/DatabaseConnection`：数据库连接

### 3.2 前端（`src/main/webapp`）

- `embed.html`：地图页主体
- `app.js`：地图渲染、搜索、弹窗、侧栏详情、色盲模式
- `style.css`：界面样式
- `data/*.geojson`：市镇/部门边界数据（本土 + DROM）

---

## 4. API 设计（前端会直接用到）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/map-data` | 地图着色主数据（市镇级） |
| GET | `/api/search?q=...` | 搜索市镇 |
| GET | `/api/latest/{codeInsee}` | 某市镇最新 prélèvement |
| GET | `/api/details/{prelevementId}` | 某次 prélèvement 的指标详情 |

前端可通过 URL 参数 `api` 指定 API 根地址，例如：  
`embed.html?api=http://localhost:8082/newEAU/api`

---

## 5. 核心数据流（重点）

## 5.1 首屏地图着色流程

1. `loadMap()` 启动；
2. 先调用 `renderCommunesChoropleth([])`：立即画出全市镇轮廓（无数据时浅灰）；
3. 请求 `GET /api/map-data`；
4. 得到 `features`（每个市镇含 `id/name/departement/color/status`）；
5. 再调用 `renderCommunesChoropleth(features)` 做真实上色；
6. 用户点击市镇后，进入详情流（`/latest` + `/details`）。

这保证了“页面一开就有全图轮廓”，API 慢时不会白屏。

## 5.2 搜索后跳转与弹窗流程

1. `runSearch()` 调 `/api/search` 返回候选城市；
2. 选中某条结果后：
   - `focusSearchResultOnMap(pick)`：计算坐标并 `setView`
   - `openSearchResultMapPopup(pick, latlng)`：在目标点打开 popup
   - `showCommuneDetails(pick)`：右侧面板显示详细信息
3. popup 和面板都可继续触发 `latest/details` 拉取。

## 5.3 详情面板数据流

点击市镇（地图或搜索）后：

1. `showCommuneDetails(city)` 请求 `/api/latest/{codeInsee}`；
2. 拿到 `prelevement.id` 后调用 `loadAnalyses(prelevementId)`；
3. `loadAnalyses` 请求 `/api/details/{id}`；
4. 渲染参数、实测值、法定限值，并标记超限项。

---

## 6. 关键代码讲解（按函数）

### 6.1 `MapDataService#getMapFeatures`（后端核心）

作用：为“地图主图层”准备每个市镇的颜色状态。

主要步骤：

1. SQL 从 `communes` 左连接其“最新 prélèvement”；
2. 读四个合规字段：`bacterio/chimique/refBact/refChim`；
3. 调 `ConformityColor.fromPrelevement(...)` 得到 `color + status`；
4. 输出为列表：`id, name, departement, color, status`。

这个列表由 `MapDataServlet` 序列化成：

```json
{
  "features": [
    {
      "id": "75056",
      "name": "Paris",
      "departement": "75",
      "color": "#22c55e",
      "status": "Conforme"
    }
  ]
}
```

### 6.2 `renderCommunesChoropleth(features)`（前端着色核心）

作用：把 API 的 `features` 映射到 GeoJSON 多边形颜色。

关键点：

1. 建立 `featureMap = Map<code_insee, feature>`；
2. 遍历 GeoJSON 的每个市镇面；
3. 用 `getCommuneCode(geoFeature)` 取 code；
4. 在 `featureMap` 查颜色并调用 `createCommuneStyle`；
5. `bindCommuneInteractions` 绑定 hover/click/popup 行为。

本质是：**几何来自 GeoJSON，颜色来自后端 API，通过 code_insee 关联。**

### 6.3 `createCommuneStyle(mapFeature)`（颜色落地）

当 `mapFeature` 存在时使用其 `color`；不存在时用默认浅灰。  
色盲模式时会通过 `resolveDisplayColor` 做颜色替换。

### 6.4 `bindCommuneInteractions(...)`（交互桥接）

负责：

- popup 初次内容；
- 鼠标移入/移出样式；
- 点击后按 `codeInsee` 请求 `/latest`，刷新状态并打开详情面板；
- popup 按钮跳转到详细 fiche。

---

## 7. 颜色规则来源

规则在 `ConformityColor` 中（后端计算，前端仅展示）：

- 全部合规 -> 绿
- 参考合规但指标异常 -> 黄
- 细菌合规且化学不合规 -> 橙
- 无数据 -> 灰
- 其他不合规 -> 红

这保证前后端职责清晰：

- 后端定义“怎么算”
- 前端只负责“怎么画”

---

## 8. 当前前端主要模块速览（`app.js`）

- 地图初始化：Leaflet map/tile/zoom control
- 数据加载：`loadMap`
- 地图着色：`renderCommunesChoropleth`
- 搜索：`runSearch`
- 搜索定位：`resolveCommuneLatLng` + `focusSearchResultOnMap`
- 搜索弹窗：`openSearchResultMapPopup`
- 详情面板：`showCommuneDetails` + `loadAnalyses`
- 颜色切换：`resolveDisplayColor` + `refreshMapColors`
- 领土按钮：`applyTerritoryView`

---

## 9. 调试建议（和“地图没颜色”最相关）

1. 先看 `/api/map-data` 是否返回 `features`；
2. 检查返回中的 `id` 是否与 GeoJSON 的 `code/code_insee` 对得上；
3. 看浏览器 network 是否有 `/latest`、`/details` 404/500；
4. 若 Docker 与本地结果不同，优先确认挂载的静态目录与 `target`/`src` 是否一致。

---

## 10. 运行与部署（简版）

1. 执行 `database/schema.sql` 初始化 MySQL；
2. `mvn clean package` 产出 `target/newEAU-1.0-SNAPSHOT.war`；
3. 部署到 Tomcat；
4. 打开 `embed.html` 或 `index.html`，必要时加 `?api=...`。

---

## 11. 一句话总结主链路

后端把每个市镇算成颜色状态 -> 前端用市镇 code 把颜色贴到 GeoJSON 面上 -> 用户点击后再按城市拉最新检测与详细指标。
