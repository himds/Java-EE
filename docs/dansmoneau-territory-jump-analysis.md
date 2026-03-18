# 分析：dansmoneau.fr 海外领土跳转实现思路

## 1. 网站公开说明（来自 dansmoneau.fr）

- **法国本土（France métropolitaine）**：按 **UDI**（unité de distribution，供水单元）显示结果；一个 UDI 可覆盖多市镇，一个市镇也可被多个 UDI 覆盖。
- **海外领土（DROM）**：按 **市镇（commune）** 显示结果。
- 地图数据来自 data.gouv.fr 开放数据，由 ARS 控制的水质分析结果，每月更新。

因此，dansmoneau 的“海外领土跳转”本质是：**切换当前视图到某一领土（本土或某个 DROM），并可能切换数据粒度（UDI vs 市镇）**。

---

## 2. 海外领土跳转的典型实现方式

无法直接抓取 dansmoneau 的前端源码（地图可能在 iframe/SPA 或另一子域），下面是一般地图类网站会用的做法，与 dansmoneau 描述一致。

### 2.1 领土列表 + 固定视野（bounds）

- 在侧栏或顶部提供领土选择：**Métropole**、**Guadeloupe**、**Martinique**、**Guyane**、**La Réunion**、**Mayotte**，可选 **France entière**。
- 每个领土对应一个经纬度范围 `[[latMin, lngMin], [latMax, lngMax]]`。
- 用户选择某一项时：
  - 调用 `map.fitBounds(bounds)` 将地图平移+缩放至该范围；
  - 可选：根据当前领土切换加载的数据（本土用 UDI 几何，DROM 用市镇几何）。

### 2.2 数据与图层

- **本土**：用 UDI 的 GeoJSON 做 choropleth，颜色来自 API（按 UDI/市镇聚合的水质结果）。
- **DROM**：用各海外省的**市镇** GeoJSON 做 choropleth，数据按市镇从同一套 API 或另一接口获取。
- 若 DROM 暂无几何或数据，可只做“跳转视野 + 占位卡片/说明”，与当前我们项目做法一致。

### 2.3 界面位置（与 dansmoneau 风格一致）

- 领土选择器放在**右侧信息栏靠上、靠左**，便于“先选领土、再看详情”。
- 侧栏收起/展开时，领土控件随侧栏一起显示/隐藏，位置随侧栏宽度自适应（flex/百分比）。

---

## 3. 本项目中已实现的对应关系

| 功能点 | dansmoneau 思路 | 本项目实现 |
|--------|------------------|------------|
| 领土列表 | 侧栏或顶部：Métropole + 5 DROM + France entière | 右侧栏顶部 `<select id="territory-select">`，选项：metro, guadeloupe, martinique, guyane, reunion, mayotte, france_entiere |
| 视野跳转 | 选择后 `fitBounds` 到该领土 | `TERRITORY_VIEWS[key]` 存各领土 bounds，`applyTerritoryView(key)` 内 `map.fitBounds(bounds)` |
| 本土数据 | UDI 或市镇 choropleth | 使用 `communes.geojson` / `departements.geojson` + API 颜色 |
| DROM 数据 | 按市镇展示（有则显示） | 当前无 DROM GeoJSON，用 `L.rectangle(bounds)` 做占位卡片 + 文案说明 |
| 控件位置 | 右侧栏左侧、随侧栏伸缩 | 领土区块在 `.side-panel-body` 顶部，CSS 使用 flex + `min-width: 0` 随侧栏伸缩 |

---

## 4. 结论与可扩展点

- **海外领土跳转**：dansmoneau 与常见做法一致——**领土选择器 + 预定义 bounds + fitBounds**；我们已按同一思路实现。
- **差异**：dansmoneau 在 DROM 有按市镇的真实数据和几何；我们目前 DROM 仅做视野跳转 + 占位卡片，若今后有 DROM 的 communes GeoJSON 与 API，可改为与本土类似的 choropleth 图层即可。

如需进一步对齐 dansmoneau（例如 URL 参数记忆当前领土、或按领土切换请求不同 API），可在现有 `territorySelect` 与 `applyTerritoryView` 上扩展。
