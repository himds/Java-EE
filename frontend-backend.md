# 前端与后端是怎么连在一起的

## 1. 一句话

**前端（浏览器里的 JS）通过一个“基础地址”用 HTTP 请求后端的 REST 接口，拿到 JSON 后再在页面上画地图、填侧边栏。**

---

## 2. 唯一的“接头”：API_BASE

前端只有一个地方写死了后端地址：

**文件**：`frontend/app.js` 第 1 行

```js
const API_BASE = 'http://localhost:8081/water-quality/api';
```

含义：

- `http://localhost:8081`：后端服务跑在这台机器的 8081 端口（你改成 8080 也行，前后端一致即可）。
- `/water-quality`：后端应用的上下文路径（WAR 部署名）。
- `/api`：后端里 Jersey 的 servlet 映射前缀（见 `web.xml` 里 `url-pattern`）。

所以**所有接口**都是：`API_BASE` + 具体路径，例如：

- `http://localhost:8081/water-quality/api/communes`
- `http://localhost:8081/water-quality/api/map-data`
- …

**要前后端连上**：后端必须已经启动，并且端口、上下文路径和上面一致（否则改 `API_BASE`）。

---

## 3. 前端在什么时候调后端？

| 前端行为 | 调用的地址 | 后端类/方法 | 拿到的数据用途 |
|----------|------------|-------------|----------------|
| 打开地图（区域/部门视图） | `GET .../map-data?mode=regions&year=...&pollutant=...` | `CommuneResource.getMapData` | 按市镇/部门着色、弹窗 |
| 打开地图（市镇多边形） | `GET .../communes?year=...&pollutant=...` | `CommuneResource.getAll` | 市镇列表 → 和 GeoJSON 按 INSEE 匹配后画多边形 |
| 打开地图（点视图） | `GET .../communes?year=...&pollutant=...` | `CommuneResource.getAll` | 市镇列表 → 用 GeoJSON 中心点画圆点 |
| 搜索框输入 | `GET .../search?q=...&year=...&pollutant=...` | `CommuneResource.search` | 搜索结果列表 |
| 点某个市镇看详情 | `GET .../latest/{codeInsee}?year=...&pollutant=...` | `CommuneResource.getLatestPrelevement` | 最新 prélèvement、颜色、结论 |
| 看某次检测的指标 | `GET .../details/{prelevementId}` | `CommuneResource.getDetails` | 分析结果列表（paramètre、valeur 等） |

前端用 `fetch(API_BASE + 路径)` 发请求，用 `response.json()` 拿 JSON，再根据字段更新地图和右侧面板。**没有别的“联系”方式**，就是这一套 HTTP + JSON。

---

## 4. 后端是怎么接上的？

- **部署**：WAR 部署到 Tomcat，上下文为 `/water-quality`，所以根地址是 `http://localhost:8081/water-quality`。
- **Servlet**：`web.xml` 里把 Jersey 映射到 `/api/*`，所以所有 `.../water-quality/api/xxx` 都进 Jersey。
- **REST**：`CommuneResource` 上 `@Path("/")`，所以：
  - `@Path("communes")` → `/api/communes`
  - `@Path("map-data")` → `/api/map-data`
  - 等等。

后端从数据库读数据（communes、prelevements、resultats_analyses），封装成 JSON 返回，**不关心**前端是哪个页面、哪个域名，只要 HTTP 请求到正确 URL 就能连上。

---

## 5. 小结

- **联系**：前端 `API_BASE` + 路径 = 后端完整 URL；前端用 `fetch` 调这些 URL，后端返回 JSON。
- **改端口/上下文**：后端改端口或部署名时，把 `frontend/app.js` 里的 `API_BASE` 改成同样的 `http://主机:端口/上下文路径/api` 即可。
- **跨域**：若前端不是从 `http://localhost:8081` 打开（例如用 `file://` 或别的端口），浏览器可能报 CORS；那时需要后端允许该来源，或把前端也放到同源下访问。

- **开发手册**：前端地图与后端、数据库的交互逻辑详见 `docs/开发手册-地图与后端数据库交互.md`。
