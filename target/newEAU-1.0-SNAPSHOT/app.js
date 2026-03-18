// ========== 地图与数据库联动 ==========
// 1. 前端：GeoJSON 仅提供几何（市镇边界），每个 feature 有 properties.code / code_insee（即 INSEE 代码）。
// 2. 前端请求后端：GET /api/communes 或 GET /api/map-data → 后端从 DB 读取 communes + prelevements，按 code_insee 计算颜色。
// 3. 前端用 GeoJSON 的 code_insee 与 API 返回的 id（codeInsee）匹配，对每个多边形应用后端返回的 color。
// 结论：颜色 100% 来自数据库，无静态演示色；若后端/DB 不可用，地图全部显示灰色并提示。
// ==========

// 后端 API 根地址。本机 RunServer/Cargo 默认 8081；Docker 若映射到 8082 则用 embed.html?api=http://localhost:8082/water-quality/api
const API_BASE = (() => {
  const params = new URLSearchParams(window.location.search);
  return params.get('api') || 'http://localhost:8081/water-quality/api';
})();
const COMMUNES_GEOJSON_URL = './data/communes.geojson';
const DEPARTMENTS_GEOJSON_URL = './data/departements.geojson';
const COMMUNES_DROM_GEOJSON_URL = './data/communes-drom.geojson';
const DEPARTMENTS_DROM_GEOJSON_URL = './data/departements-drom.geojson';
const map = L.map('map', { zoomControl: false, attributionControl: false }).setView([46.5, 2.5], 6);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  maxZoom: 19
}).addTo(map);
L.control.zoom({ position: 'bottomright' }).addTo(map);

const markerLayer = L.layerGroup().addTo(map);
let departmentLayer = null;   // 部门多边形，始终显示（在 zoom 足够时可见）
let choroplethLayer = null;   // 当前展开部门的市镇多边形，仅一个部门展开时存在
let dromCardLayer = null;
let communesGeoJson = null;
let departmentsGeoJson = null;
let allMapDataFeatures = []; // 初始 map-data 全部 features，用于按部门过滤
let openDepartmentCode = null;
const ZOOM_COMMUNE_THRESHOLD = 9; // 缩放低于此级别时只显示部门，不显示市镇
const appShell = document.querySelector('.app-shell');
const results = document.getElementById('results');
const searchInput = document.getElementById('search');
const sidePanel = document.getElementById('side-panel');
const panelToggle = document.getElementById('panel-toggle');
const closePanel = document.getElementById('close-panel');
const communeTitle = document.getElementById('commune-title');
const communeMeta = document.getElementById('commune-meta');
const analysisList = document.getElementById('analysis-list');
const analysisReference = document.getElementById('analysis-reference');
const legendToggle = document.getElementById('legend-toggle');
const legendBody = document.getElementById('legend-body');
const legendArrow = document.getElementById('legend-arrow');
const colorblindToggle = document.getElementById('colorblind-toggle');
const statusBadge = document.getElementById('status-badge');
const metricView = document.getElementById('metric-view');
const metricSamples = document.getElementById('metric-samples');
const detailHeading = document.getElementById('detail-heading');
const territoryIconBtns = document.querySelectorAll('.territory-icon-btn');

let currentSearchItems = [];
let zoneFallbackMode = 'departments';
let colorblindMode = false;

const COLORBLIND_MAP = {
  '#9ca3af': '#9ca3af',
  '#22c55e': '#56B4E9',
  '#eab308': '#F0E442',
  '#facc15': '#F0E442',
  '#f97316': '#E69F00',
  '#ef4444': '#D55E00',
  '#e11d48': '#D55E00',
  '#cbd5e1': '#9ca3af'
};

function resolveDisplayColor(hex) {
  if (!hex || !colorblindMode) return hex || '#cbd5e1';
  const h = (hex || '').toLowerCase();
  return COLORBLIND_MAP[h] || hex;
}

const TERRITORY_VIEWS = {
  metro: [[41.0, -5.8], [51.7, 9.8]],
  guadeloupe: [[15.7, -61.9], [16.6, -60.9]],
  martinique: [[14.25, -61.35], [14.95, -60.75]],
  guyane: [[2.0, -54.9], [6.1, -51.3]],
  reunion: [[-21.45, 55.15], [-20.8, 55.95]],
  mayotte: [[-13.1, 45.0], [-12.45, 45.4]],
  france_entiere: [[-21.5, -62], [51.7, 56]]
};
const DROM_KEYS = ['guadeloupe', 'martinique', 'guyane', 'reunion', 'mayotte'];
const DROM_LABELS = { guadeloupe: 'Guadeloupe', martinique: 'Martinique', guyane: 'Guyane', reunion: 'La Réunion', mayotte: 'Mayotte' };

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

async function runConnectionCheck() {
  const btn = document.getElementById('check-connection-btn');
  const list = document.getElementById('analysis-list');
  if (btn) btn.disabled = true;
  const lines = [];
  try {
    const healthUrl = `${API_BASE}/health`;
    const healthR = await fetch(healthUrl);
    if (!healthR.ok) {
      lines.push('<strong>API :</strong> inaccessible (backend non démarré ou mauvais port).');
      lines.push('URL testée : ' + escapeHtml(healthUrl));
    } else {
      lines.push('<strong>API :</strong> OK.');
    }
  } catch (e) {
    lines.push('<strong>API :</strong> inaccessible (erreur réseau ou CORS). Vérifiez que le backend écoute sur ' + escapeHtml(API_BASE.split('/')[2]) + '.');
  }
  try {
    const dbR = await fetch(`${API_BASE}/health/db`);
    const dbJson = await dbR.json().catch(() => ({}));
    if (!dbR.ok) {
      lines.push('<strong>Base de données :</strong> inaccessible.');
      if (dbJson.message) lines.push(escapeHtml(dbJson.message));
    } else {
      lines.push('<strong>Base de données :</strong> connectée.');
    }
  } catch (e) {
    lines.push('<strong>Base de données :</strong> non testée (API déjà en erreur).');
  }
  if (list) {
    const extra = list.querySelector('li button') ? '<li><button type="button" class="popup-btn" id="check-connection-btn">Vérifier la connexion</button></li>' : '';
    list.innerHTML = lines.map(s => '<li>' + s + '</li>').join('') + extra;
    document.getElementById('check-connection-btn')?.addEventListener('click', runConnectionCheck);
  }
  if (btn) btn.disabled = false;
}

function invalidateMapSoon() {
  setTimeout(() => map.invalidateSize(), 260);
}

function openPanel() {
  sidePanel.classList.remove('is-hidden');
  appShell.classList.remove('panel-collapsed');
  panelToggle.classList.remove('is-collapsed');
  panelToggle.textContent = '›';
  invalidateMapSoon();
}
function closeSidePanel() {
  sidePanel.classList.add('is-hidden');
  appShell.classList.add('panel-collapsed');
  panelToggle.classList.add('is-collapsed');
  panelToggle.textContent = '‹';
  invalidateMapSoon();
}
panelToggle.addEventListener('click', () => sidePanel.classList.contains('is-hidden') ? openPanel() : closeSidePanel());
closePanel.addEventListener('click', closeSidePanel);
legendToggle.addEventListener('click', () => {
  const collapsed = legendBody.classList.toggle('is-collapsed');
  legendArrow.textContent = collapsed ? '▸' : '▾';
});

if (colorblindToggle) {
  colorblindToggle.addEventListener('change', async () => {
    colorblindMode = colorblindToggle.checked;
    if (legendBody) legendBody.classList.toggle('colorblind', colorblindMode);
    document.body.classList.toggle('colorblind', colorblindMode);
    setStatusBadge(lastStatusBadgeText, lastStatusBadgeColor);
    await refreshMapColors();
  });
}

function queryString(extra = {}) {
  const params = new URLSearchParams();
  Object.entries(extra).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') params.set(key, value);
  });
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

function badgeClassFromColor(color) {
  const c = (color || '').toLowerCase();
  if (c === '#22c55e' || c === '#56b4e9') return 'green';
  if (c === '#facc15' || c === '#eab308' || c === '#f0e442') return 'yellow';
  if (c === '#f97316' || c === '#e69f00') return 'orange';
  if (c === '#e11d48' || c === '#ef4444' || c === '#d55e00') return 'red';
  return 'neutral';
}

let lastStatusBadgeText = '';
let lastStatusBadgeColor = '';

function setStatusBadge(text, color) {
  lastStatusBadgeText = text || 'Aucune donnée';
  lastStatusBadgeColor = color;
  const displayColor = resolveDisplayColor(color);
  statusBadge.className = `status-badge ${badgeClassFromColor(displayColor)}`;
  statusBadge.textContent = lastStatusBadgeText;
}

function renderRegionPopup(feature) {
  return `
    <div class="popup-region-card">
      <div class="popup-region-title">${escapeHtml(feature.name)}</div>
      <div class="popup-region-line">Statut: ${escapeHtml(feature.status || '—')}</div>
      <div class="popup-region-line">Communes agrégées: ${feature.sampleCount ?? 0}</div>
      <button type="button" class="popup-btn" data-region="${escapeHtml(feature.id)}">Voir la fiche</button>
    </div>
  `;
}

async function loadAnalyses(prelevementId) {
  if (analysisReference) analysisReference.textContent = '';
  analysisList.innerHTML = '<li>Chargement...</li>';
  const response = await fetch(`${API_BASE}/details/${prelevementId}`);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const msg = data.message || data.error || `HTTP ${response.status}`;
    analysisList.innerHTML = `<li>Aucune analyse détaillée disponible.</li><li class="error-msg">${escapeHtml(msg)}</li>`;
    return;
  }
  const referenceprel = data.referenceprel != null ? String(data.referenceprel).trim() : '';
  let analyses = Array.isArray(data.analyses) ? data.analyses : [];
  const seenParametres = new Set();
  analyses = analyses.filter(item => {
    const p = (item.parametre != null ? String(item.parametre).trim() : '');
    if (!p || seenParametres.has(p)) return false;
    seenParametres.add(p);
    return true;
  });
  if (analysisReference) analysisReference.textContent = referenceprel ? `Prélèvement: ${escapeHtml(referenceprel)}` : '';
  if (analyses.length === 0) {
    analysisList.innerHTML = '<li>Aucune analyse détaillée disponible.</li>';
    return;
  }
  analysisList.innerHTML = analyses.map(item => {
    const parametre = item.parametre != null ? String(item.parametre) : '';
    const v = item.valeurMesuree;
    const lim = item.limiteLegale;
    const nonConforme = typeof v === 'number' && typeof lim === 'number' && v > lim;
    const rowClass = nonConforme ? 'analysis-item non-conforme' : 'analysis-item';
    const limitText = lim != null ? ` (limite ≤ ${lim})` : '';
    return `<li class="${rowClass}">
      <strong>${escapeHtml(parametre)}</strong><br>
      Valeur mesurée: ${v ?? '—'} · Limite légale: ${lim ?? '—'}${limitText}
    </li>`;
  }).join('');
}

function setMetrics(viewText, sampleCount) {
  metricView.textContent = viewText;
  metricSamples.textContent = sampleCount ?? '—';
}

async function showCommuneDetails(city) {
  openPanel();
  detailHeading.textContent = 'Commune';
  communeTitle.textContent = city.nomCommune;
  setMetrics('Commune', 1);
  communeMeta.textContent = `Code INSEE: ${city.codeInsee} · Département: ${city.departement ?? '—'} · Statut: ${city.status ?? '—'}`;
  setStatusBadge(city.status, city.color);
  analysisList.innerHTML = '<li>Chargement...</li>';

  const latestResponse = await fetch(`${API_BASE}/latest/${city.codeInsee}${queryString()}`);
  if (!latestResponse.ok) {
    const errBody = await latestResponse.json().catch(() => ({}));
    const msg = errBody.message || errBody.error || `HTTP ${latestResponse.status}`;
    analysisList.innerHTML = `<li>Aucun prélèvement trouvé.</li><li class="error-msg">${escapeHtml(msg)}</li>`;
    return;
  }
  const latest = await latestResponse.json();
  const prelevement = latest.prelevement;
  if (!prelevement || prelevement.id == null) {
    communeMeta.textContent += ' · Aucun prélèvement';
    analysisList.innerHTML = '<li>Aucun prélèvement trouvé pour cette commune.</li>';
    return;
  }
  communeMeta.textContent += ` · Dernier prélèvement: ${prelevement.dateprel ?? '—'}`;
  setStatusBadge(prelevement.status || city.status, prelevement.color);
  await loadAnalyses(prelevement.id);
}

async function focusOnCommuneOnMap(city) {
  if (city.latitude != null && city.longitude != null) {
    map.setView([city.latitude, city.longitude], 11);
    return;
  }
  const geojson = await ensureCommunesGeoJson().catch(() => null);
  if (!geojson || !Array.isArray(geojson.features)) return;
  const targetCode = normalizeCommuneCode(city.codeInsee);
  const feature = geojson.features.find(f => {
    const code = getCommuneCode(f);
    return normalizeCommuneCode(code) === targetCode;
  });
  if (!feature) return;
  const center = centroidFromGeoFeature(feature);
  if (center && Array.isArray(center) && center.length === 2) {
    map.setView(center, 11);
  }
}

async function showRegionDetails(feature) {
  openPanel();
  detailHeading.textContent = zoneFallbackMode === 'communes' ? 'Commune' : 'Département';
  communeTitle.textContent = feature.name;
  setMetrics(zoneFallbackMode === 'communes' ? 'Choropleth communal' : 'Choropleth départemental', feature.sampleCount ?? 1);
  communeMeta.textContent = zoneFallbackMode === 'communes'
    ? `Code INSEE: ${feature.id ?? '—'} · Département: ${feature.departement ?? '—'} · Statut: ${feature.status ?? '—'}`
    : `Département: ${feature.departement ?? '—'} · Statut: ${feature.status ?? '—'} · Communes agrégées: ${feature.sampleCount ?? 0}`;
  setStatusBadge(feature.status, feature.color);

  if (zoneFallbackMode === 'communes' && feature.id) {
    analysisList.innerHTML = '<li>Chargement...</li>';
    if (analysisReference) analysisReference.textContent = '';
    try {
      const response = await fetch(`${API_BASE}/latest/${encodeURIComponent(feature.id)}${queryString()}`);
      const data = await response.json().catch(() => ({}));
      const prelevement = data.prelevement;
      if (prelevement && prelevement.id != null) {
        communeMeta.textContent += ` · Dernier prélèvement: ${prelevement.dateprel ?? '—'}`;
        setStatusBadge(prelevement.status || feature.status, prelevement.color);
        await loadAnalyses(prelevement.id);
      } else {
        if (analysisReference) analysisReference.textContent = '';
        analysisList.innerHTML = '<li>Aucun prélèvement trouvé pour cette commune.</li>';
      }
    } catch (e) {
      if (analysisReference) analysisReference.textContent = '';
      analysisList.innerHTML = `<li>Aucune analyse détaillée disponible.</li><li class="error-msg">${escapeHtml(e.message || 'Erreur réseau')}</li>`;
    }
    return;
  }

  analysisList.innerHTML = zoneFallbackMode === 'communes'
    ? `
    <li>Zone coloriée à partir du fond communal France.</li>
    <li>Département: ${feature.departement ?? '—'}.</li>
    <li>Cette vue correspond à une carte choropleth par commune, sans routes ni fond OSM.</li>
  `
    : `
    <li>Le fond communal est trop lourd ou n'a pas pu être affiché correctement.</li>
    <li>Affichage de secours: carte départementale colorée.</li>
    <li>Communes agrégées: ${feature.sampleCount ?? 0}.</li>
  `;
}

async function ensureCommunesGeoJson() {
  if (communesGeoJson) return communesGeoJson;
  const features = [];
  try {
    const r = await fetch(COMMUNES_GEOJSON_URL);
    if (r.ok) {
      const data = await r.json();
      features.push(...(data.features || []));
    }
  } catch (e) { /* ignore */ }
  try {
    const r = await fetch(COMMUNES_DROM_GEOJSON_URL);
    if (r.ok) {
      const data = await r.json();
      features.push(...(data.features || []));
    }
  } catch (e) { /* ignore */ }
  communesGeoJson = { type: 'FeatureCollection', features };
  return communesGeoJson;
}

async function ensureDepartmentsGeoJson() {
  if (departmentsGeoJson) return departmentsGeoJson;
  const features = [];
  try {
    const r = await fetch(DEPARTMENTS_GEOJSON_URL);
    if (r.ok) {
      const data = await r.json();
      features.push(...(data.features || []));
    }
  } catch (e) { /* ignore */ }
  try {
    const r = await fetch(DEPARTMENTS_DROM_GEOJSON_URL);
    if (r.ok) {
      const data = await r.json();
      features.push(...(data.features || []));
    }
  } catch (e) { /* ignore */ }
  departmentsGeoJson = { type: 'FeatureCollection', features };
  return departmentsGeoJson;
}

function normalizeCommuneCode(code) {
  if (code == null || code === '') return '';
  const s = String(code).trim();
  // French INSEE: 5 digits (pad with leading zero for consistent GeoJSON matching)
  if (/^\d+$/.test(s) && s.length <= 5) return s.padStart(5, '0');
  return s.toUpperCase();
}

function getCommuneCode(feature) {
  const props = feature.properties || {};
  return normalizeCommuneCode(props.code || props.insee || props.code_insee || props.id);
}

function getDepartmentCode(feature) {
  const props = feature.properties || {};
  return normalizeCommuneCode(props.code || props.code_departement || props.id);
}

/** 从市镇 GeoJSON feature 得到部门代码：本土 2 位（如 76），海外 DROM 3 位（如 971） */
function getDepartmentFromCommuneFeature(feature) {
  const code = getCommuneCode(feature);
  const s = String(code).trim();
  if (s.length < 2) return s;
  if (s.length >= 3 && /^97|98/.test(s)) return s.slice(0, 3);
  return s.slice(0, 2);
}

/** 统一部门码：本土 2 位（76），海外 DROM 保持 3 位（971, 972, 973, 974, 976） */
function normalizeDepartmentCode(v) {
  const s = String(v ?? '').trim().replace(/^0+/, '');
  if (s.length === 0) return '';
  if (/^97|98/.test(s)) return s.length >= 3 ? s.slice(0, 3) : s.padStart(3, '0');
  const two = s.slice(-2);
  return two.length >= 2 ? two : two.padStart(2, '0');
}

function createCommuneStyle(mapFeature) {
  const color = resolveDisplayColor(mapFeature?.color || '#cbd5e1');
  return {
    color: '#ffffff',
    weight: 1.5,
    opacity: 1,
    fillColor: color,
    fillOpacity: 0.96,
    dashArray: ''
  };
}

function createCommuneHoverStyle(mapFeature) {
  const color = resolveDisplayColor(mapFeature?.color || '#cbd5e1');
  return {
    color: '#0f172a',
    weight: 2.2,
    opacity: 1,
    fillColor: color,
    fillOpacity: 1,
    dashArray: ''
  };
}

async function fetchCommuneFromBackend(codeInsee) {
  const response = await fetch(`${API_BASE}/latest/${encodeURIComponent(codeInsee)}${queryString()}`);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
  return data.commune || data;
}

function bindCommuneInteractions(layer, mapFeature, fallbackName, codeInsee) {
  const featureData = mapFeature || {
    id: codeInsee || fallbackName,
    name: fallbackName,
    departement: null,
    status: 'Aucune donnée',
    color: '#9ca3af',
    sampleCount: 0
  };

  // Popup 初始先展示（若无数据为灰），点击/打开时把 code_insee 回传后端查库算色并刷新
  layer.bindPopup(renderRegionPopup(featureData));
  layer.on('click', async () => {
    if (!codeInsee) return showRegionDetails(featureData);
    try {
      const commune = await fetchCommuneFromBackend(codeInsee);
      featureData.id = commune.codeInsee || codeInsee;
      featureData.name = commune.nomCommune || featureData.name;
      featureData.departement = commune.departement || featureData.departement;
      featureData.status = commune.status || featureData.status;
      featureData.color = commune.color || featureData.color;
      layer.setStyle(createCommuneStyle(featureData));
    } catch (e) {
      // ignore; keep grey
    }
    showRegionDetails(featureData);
  });
  layer.on('mouseover', () => {
    layer.setStyle(createCommuneHoverStyle(featureData));
    if (!L.Browser.ie && !L.Browser.opera && !L.Browser.edge) {
      layer.bringToFront();
    }
  });
  layer.on('mouseout', () => {
    layer.setStyle(createCommuneStyle(featureData));
  });
  layer.on('popupopen', () => {
    if (codeInsee) {
      // 打开 popup 时也刷新一次（避免首屏全灰但点击后才变色的体验）
      fetchCommuneFromBackend(codeInsee)
        .then(commune => {
          featureData.id = commune.codeInsee || codeInsee;
          featureData.name = commune.nomCommune || featureData.name;
          featureData.departement = commune.departement || featureData.departement;
          featureData.status = commune.status || featureData.status;
          featureData.color = commune.color || featureData.color;
          layer.setStyle(createCommuneStyle(featureData));
          layer.setPopupContent(renderRegionPopup(featureData));
        })
        .catch(() => { });
    }
    setTimeout(() => {
      const btn = document.querySelector(`.popup-btn[data-region="${CSS.escape(featureData.id)}"]`);
      if (btn) btn.onclick = () => showRegionDetails(featureData);
    }, 0);
  });
}

function clearChoropleth() {
  if (choroplethLayer) {
    map.removeLayer(choroplethLayer);
    choroplethLayer = null;
  }
}

function clearCommuneLayer() {
  clearChoropleth();
  openDepartmentCode = null;
}

function clearDepartmentLayer() {
  if (departmentLayer) {
    map.removeLayer(departmentLayer);
    departmentLayer = null;
  }
}

async function renderCommunesChoropleth(features) {
  const geojson = await ensureCommunesGeoJson();
  // featureMap: code_insee (id) -> { color, status, ... } 来自后端/数据库，前端仅按 id 匹配着色
  const featureMap = new Map(features.map(item => [normalizeCommuneCode(item.id), item]));

  clearChoropleth();

  const featuresToRender = geojson.features;

  choroplethLayer = L.geoJSON({ type: 'FeatureCollection', features: featuresToRender }, {
    style: geoFeature => {
      const codeInsee = getCommuneCode(geoFeature);
      const mapFeature = featureMap.get(codeInsee);
      return createCommuneStyle(mapFeature);
    },
    onEachFeature: (geoFeature, layer) => {
      const props = geoFeature.properties || {};
      const codeInsee = getCommuneCode(geoFeature);
      const mapFeature = featureMap.get(codeInsee);
      const fallbackName = props.nom || props.name || `Commune ${codeInsee}`;

      // 从GeoJSON几何中获取中心点坐标
      if (geoFeature.geometry && geoFeature.geometry.type === 'Polygon' && geoFeature.geometry.coordinates) {
        const coordinates = geoFeature.geometry.coordinates[0];
        let sumLat = 0, sumLng = 0;
        for (const coord of coordinates) {
          sumLng += coord[0];
          sumLat += coord[1];
        }
        const centerLng = sumLng / coordinates.length;
        const centerLat = sumLat / coordinates.length;

        // 如果需要点视图中心，前端会用 GeoJSON 质心；这里不再把坐标塞进后端对象
      }

      bindCommuneInteractions(layer, mapFeature, fallbackName, codeInsee);
    }
  }).addTo(map);

  try { map.fitBounds(choroplethLayer.getBounds(), { padding: [20, 20] }); } catch { }
}

function aggregateFeaturesByDepartment(features) {
  const grouped = new Map();
  for (const item of features) {
    const key = (item.departement && String(item.departement).trim().slice(0, 2)) || (item.id && String(item.id).trim().slice(0, 2)) || '';
    if (!key) continue;
    const prev = grouped.get(key);
    if (!prev) {
      grouped.set(key, {
        id: `dept-${key}`,
        name: `Département ${key}`,
        departement: key,
        color: item.color || '#9ca3af',
        status: item.status || 'Aucune donnée',
        sampleCount: 1
      });
    } else {
      prev.sampleCount += 1;
      const severity = { '#e11d48': 4, '#f97316': 3, '#facc15': 2, '#22c55e': 1, '#9ca3af': 0 };
      if ((severity[item.color] || 0) > (severity[prev.color] || 0)) {
        prev.color = item.color;
        prev.status = item.status;
      }
    }
  }
  return grouped;
}

/** 仅绘制部门层，初始全部灰色；点击某部门后展开该部门内的市镇并显示各市颜色 */
async function renderDepartmentLayerOnly(features) {
  const geojson = await ensureDepartmentsGeoJson();
  const greyDept = { color: '#9ca3af', status: 'Aucune donnée', sampleCount: 0 };

  clearDepartmentLayer();
  departmentLayer = L.geoJSON(geojson, {
    style: () => createCommuneStyle(greyDept),
    onEachFeature: (geoFeature, layer) => {
      const props = geoFeature.properties || {};
      const deptCode = getDepartmentCode(geoFeature);
      const dept2 = String(deptCode).trim().slice(-2);
      const mapFeature = {
        id: `dept-${dept2}`,
        name: props.nom || `Département ${dept2}`,
        departement: dept2,
        status: 'Aucune donnée',
        color: '#9ca3af',
        sampleCount: 0
      };
      bindCommuneInteractions(layer, mapFeature, mapFeature.name);
      layer.on('click', () => {
        openDepartmentCode = dept2;
        if (map.getZoom() >= ZOOM_COMMUNE_THRESHOLD) {
          showCommunesForDepartment(dept2);
        } else {
          map.setZoom(ZOOM_COMMUNE_THRESHOLD);
          map.once('zoomend', () => showCommunesForDepartment(dept2));
        }
      });
    }
  }).addTo(map);

  try { map.fitBounds(departmentLayer.getBounds(), { padding: [20, 20] }); } catch { }
}

/** 只显示指定部门内的市镇，并用 allMapDataFeatures 的 color 为每个市镇上色（绿/黄/橙/红/灰）；同时只能有一个部门展开 */
async function showCommunesForDepartment(deptCode) {
  clearChoropleth();
  zoneFallbackMode = 'communes';
  const geojson = await ensureCommunesGeoJson();
  const normalizedDept = normalizeDepartmentCode(deptCode);
  const communeFeatures = (geojson.features || []).filter(f => {
    const d = getDepartmentFromCommuneFeature(f);
    return d === normalizedDept || normalizeDepartmentCode(d) === normalizedDept;
  });
  if (communeFeatures.length === 0) return;

  const featuresForDept = allMapDataFeatures.filter(item => {
    const raw = item.departement || (item.id && getDepartmentFromCommuneFeature({ properties: { code: item.id } })) || '';
    const itemDept = normalizeDepartmentCode(raw);
    return itemDept === normalizedDept;
  });
  const featureMap = new Map(featuresForDept.map(item => [normalizeCommuneCode(item.id), item]));

  choroplethLayer = L.geoJSON({ type: 'FeatureCollection', features: communeFeatures }, {
    style: geoFeature => {
      const codeInsee = normalizeCommuneCode(getCommuneCode(geoFeature));
      const mapFeature = featureMap.get(codeInsee);
      return createCommuneStyle(mapFeature);
    },
    onEachFeature: (geoFeature, layer) => {
      const props = geoFeature.properties || {};
      const codeInsee = normalizeCommuneCode(getCommuneCode(geoFeature));
      const mapFeature = featureMap.get(codeInsee);
      const fallbackName = props.nom || props.name || `Commune ${codeInsee}`;
      bindCommuneInteractions(layer, mapFeature, fallbackName, codeInsee);
    }
  }).addTo(map);

  try { map.fitBounds(choroplethLayer.getBounds(), { padding: [20, 20] }); } catch { }
}

async function refreshMapColors() {
  const greyDept = { color: '#9ca3af', status: 'Aucune donnée', sampleCount: 0 };
  if (departmentLayer) {
    departmentLayer.eachLayer(l => l.setStyle(createCommuneStyle(greyDept)));
  }
  if (choroplethLayer && openDepartmentCode) {
    await showCommunesForDepartment(openDepartmentCode);
  }
}

async function renderDepartmentsFallback(features) {
  const grouped = aggregateFeaturesByDepartment(features);
  const geojson = await ensureDepartmentsGeoJson();
  clearChoropleth();
  choroplethLayer = L.geoJSON(geojson, {
    style: geoFeature => {
      const deptCode = getDepartmentCode(geoFeature);
      const mapFeature = grouped.get(deptCode);
      return createCommuneStyle(mapFeature);
    },
    onEachFeature: (geoFeature, layer) => {
      const props = geoFeature.properties || {};
      const deptCode = getDepartmentCode(geoFeature);
      const mapFeature = grouped.get(deptCode) || {
        id: `dept-${deptCode}`,
        name: props.nom || `Département ${deptCode}`,
        departement: deptCode,
        status: 'Aucune donnée',
        color: '#9ca3af',
        sampleCount: 0
      };
      bindCommuneInteractions(layer, mapFeature, mapFeature.name);
    }
  }).addTo(map);
  try { map.fitBounds(choroplethLayer.getBounds(), { padding: [20, 20] }); } catch { }
}

async function renderChoropleth(features) {
  zoneFallbackMode = 'communes';
  try {
    await renderCommunesChoropleth(features);
  } catch (error) {
    console.warn('Commune choropleth failed, fallback to departments', error);
    zoneFallbackMode = 'departments';
    await renderDepartmentsFallback(features);
  }
}

function centroidFromGeoFeature(geoFeature) {
  if (!geoFeature?.geometry?.coordinates) return null;
  const coords = geoFeature.geometry.type === 'Polygon' ? geoFeature.geometry.coordinates[0] : geoFeature.geometry.coordinates;
  if (!Array.isArray(coords) || coords.length === 0) return null;
  let sumLat = 0, sumLng = 0, n = 0;
  for (const c of coords) {
    const lon = Array.isArray(c) ? c[0] : c;
    const lat = Array.isArray(c) ? c[1] : c;
    if (typeof lon === 'number' && typeof lat === 'number') { sumLng += lon; sumLat += lat; n++; }
  }
  return n ? [sumLat / n, sumLng / n] : null;
}

function buildInseeToCentroidMap(geojson) {
  const map = new Map();
  for (const f of geojson.features || []) {
    const code = normalizeCommuneCode(getCommuneCode(f));
    if (code && !map.has(code)) {
      const c = centroidFromGeoFeature(f);
      if (c) map.set(code, c);
    }
  }
  return map;
}

function buildPopupContent(city, latest) {
  const ville = escapeHtml(city.nomCommune);
  const date = latest?.prelevement?.dateprel ?? '—';
  const statut = escapeHtml(latest?.tone || city.status || '—');
  const isConforme = statut === 'Conforme' || (city.color === '#22c55e');
  let body = '';
  if (isConforme) {
    body = '<p class="popup-conforme">Tous les prélèvements conformes.</p>';
  } else {
    body = '<button type="button" class="popup-btn popup-btn-detail" data-code="' + escapeHtml(city.codeInsee) + '">Voir les résultats détaillés</button>';
  }
  return `
    <div class="popup-commune-card">
      <div class="popup-line"><strong>Ville :</strong> ${ville}</div>
      <div class="popup-line"><strong>Date :</strong> ${escapeHtml(String(date))}</div>
      <div class="popup-line"><strong>Statut :</strong> ${statut}</div>
      ${body}
    </div>
  `;
}

function addCommuneMarker(city, boundsAccumulator, centroidMap) {
  let lat = city.latitude, lon = city.longitude;
  if (lat == null || lon == null) {
    const c = centroidMap?.get(normalizeCommuneCode(city.codeInsee));
    if (!c) return;
    lat = c[0]; lon = c[1];
  }
  const color = city.color || '#9ca3af';
  boundsAccumulator.push([lat, lon]);

  const marker = L.circleMarker([lat, lon], {
    radius: 8,
    color,
    fillColor: color,
    fillOpacity: 0.9,
    weight: 2
  }).addTo(markerLayer);

  marker.bindPopup('<div class="popup-loading">Chargement...</div>', { minWidth: 260 });
  marker.on('popupopen', async () => {
    try {
      const response = await fetch(`${API_BASE}/latest/${city.codeInsee}${queryString()}`);
      const latest = response.ok ? await response.json() : null;
      marker.setPopupContent(buildPopupContent(city, latest));
      setTimeout(() => {
        const btn = document.querySelector('.popup-btn-detail[data-code="' + CSS.escape(city.codeInsee) + '"]');
        if (btn) btn.onclick = () => { marker.closePopup(); showCommuneDetails(city); };
      }, 0);
    } catch {
      marker.setPopupContent(buildPopupContent(city, null));
    }
  });
  marker.on('click', () => showCommuneDetails(city));
}

function demoFeaturesFromGeoJson(geojson, limit = 800) {
  const palette = ['#22c55e', '#facc15', '#f97316', '#e11d48'];
  return (geojson.features || []).slice(0, limit).map((feature, index) => {
    const props = feature.properties || {};
    const code = props.code || props.insee || `demo-${index}`;
    return {
      id: code,
      name: props.nom || `Commune ${code}`,
      departement: String(code).slice(0, 2),
      color: palette[index % palette.length],
      status: ['Conforme', 'Surveillance', 'Alerte chimique', 'Alerte forte'][index % palette.length],
      sampleCount: 1
    };
  });
}

async function loadMap() {
  markerLayer.clearLayers();
  clearCommuneLayer();
  clearDepartmentLayer();
  if (!sidePanel.classList.contains('is-hidden')) {
    communeTitle.textContent = 'Chargement...';
    communeMeta.textContent = 'Récupération des données depuis l\'API.';
    analysisList.innerHTML = '<li>Chargement en cours.</li>';
  }

  try {
    const response = await fetch(`${API_BASE}/map-data${queryString({ mode: 'regions' })}`);
    const mapData = await response.json().catch(() => null);
    if (!response.ok) {
      const msg = (mapData && (mapData.message || mapData.error)) || `HTTP ${response.status}`;
      throw new Error(msg);
    }
    const features = mapData.features || [];
    allMapDataFeatures = features;
    zoneFallbackMode = 'departments';
    await renderDepartmentLayerOnly(features);
  } catch (error) {
    console.warn('API map-data indisponible, carte en gris', error);
    await ensureDepartmentsGeoJson();
    await renderDepartmentsFallback([]);
    openPanel();
    communeTitle.textContent = 'Données base indisponibles';
    detailHeading.textContent = 'Connexion BDD requise';
    communeMeta.textContent = 'Démarrez le backend et MySQL pour afficher les données.';
    setStatusBadge('Sans BDD', '#9ca3af');
    analysisList.innerHTML = '<li><button type="button" class="popup-btn" id="check-connection-btn">Vérifier la connexion</button></li>';
    document.getElementById('check-connection-btn')?.addEventListener('click', runConnectionCheck);
  }

  setMetrics('Département', '—');
}

async function runSearch() {
  const value = searchInput.value.trim();
  results.innerHTML = '';
  results.classList.remove('has-items');
  currentSearchItems = [];
  if (value.length < 3) return;

  const response = await fetch(`${API_BASE}/search${queryString({ q: value })}`);
  if (!response.ok) return;
  const data = await response.json();
  if (!Array.isArray(data)) return;
  currentSearchItems = data;

  data.forEach(city => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'result-item';
    button.textContent = `${city.nomCommune} (${city.departement || 'N/A'})`;
    button.onclick = async () => {
      results.innerHTML = '';
      results.classList.remove('has-items');
      await focusOnCommuneOnMap(city);
      showCommuneDetails(city);
    };
    results.appendChild(button);
  });

  if (data.length) results.classList.add('has-items');
}

// 输入至少 3 个字符后触发搜索，防抖 300ms，保证 1.5s 内返回
let searchDebounce = null;
searchInput.addEventListener('input', () => {
  if (searchDebounce) clearTimeout(searchDebounce);
  const value = searchInput.value.trim();
  if (value.length < 3) {
    results.innerHTML = '';
    results.classList.remove('has-items');
    currentSearchItems = [];
    return;
  }
  searchDebounce = setTimeout(runSearch, 300);
});

// 缩小到一定级别后只显示部门，隐藏市镇
map.on('zoomend', () => {
  if (map.getZoom() < ZOOM_COMMUNE_THRESHOLD) {
    clearCommuneLayer();
    zoneFallbackMode = 'departments';
  }
});

// 全屏切换（地图容器）
const mapContainer = document.getElementById('map');
const fullscreenBtn = document.getElementById('fullscreen-btn');
if (fullscreenBtn && mapContainer) {
  fullscreenBtn.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      mapContainer.requestFullscreen().catch(() => { });
    } else {
      document.exitFullscreen();
    }
  });
  document.addEventListener('fullscreenchange', () => {
    setTimeout(() => map.invalidateSize(), 200);
  });
}

function clearDromCardLayer() {
  if (dromCardLayer) {
    map.removeLayer(dromCardLayer);
    dromCardLayer = null;
  }
}

const TERRITORY_LABELS = { metro: 'Métropole', guadeloupe: 'Guadeloupe', martinique: 'Martinique', guyane: 'Guyane', reunion: 'La Réunion', mayotte: 'Mayotte' };

function setTerritoryButtonSelected(territoryKey) {
  territoryIconBtns.forEach(btn => {
    const key = btn.getAttribute('data-territory');
    if (key === territoryKey) {
      btn.classList.add('is-selected');
      btn.setAttribute('aria-pressed', 'true');
    } else {
      btn.classList.remove('is-selected');
      btn.setAttribute('aria-pressed', 'false');
    }
  });
}

function applyTerritoryView(territoryKey) {
  const bounds = TERRITORY_VIEWS[territoryKey];
  if (!bounds) return;
  map.fitBounds(bounds, { padding: [20, 20] });
  openPanel();
  setTerritoryButtonSelected(territoryKey);
  const label = TERRITORY_LABELS[territoryKey] || territoryKey;
  communeTitle.textContent = label;
  detailHeading.textContent = 'Territoire';
  const isDrom = DROM_KEYS.includes(territoryKey);
  clearDromCardLayer();
  clearCommuneLayer();
  if (isDrom) {
    const name = DROM_LABELS[territoryKey] || territoryKey;
    communeMeta.textContent = `Vue : ${name}. Cliquez sur un département pour afficher les communes (si les données GeoJSON incluent ce territoire).`;
    analysisList.innerHTML = '<li>Cliquez sur un département dans la zone pour afficher les communes et les analyses.</li>';
  } else {
    communeMeta.textContent = `Vue : ${label}. Utilisez le zoom et cliquez sur une zone pour afficher sa fiche.`;
    analysisList.innerHTML = '<li>Raccourci de navigation territoriale.</li>';
  }
  setStatusBadge('Navigation', '#22c55e');
}

territoryIconBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    const territory = btn.getAttribute('data-territory');
    if (!territory || !TERRITORY_VIEWS[territory]) return;
    applyTerritoryView(territory);
  });
});

loadMap().catch(err => {
  console.error('Erreur chargement carte', err);
  openPanel();
  communeTitle.textContent = 'Carte indisponible';
  detailHeading.textContent = 'Erreur';
  communeMeta.textContent = 'Le design et les filtres sont prêts, mais l\'API ne répond pas encore.';
  setStatusBadge('Indisponible', '#9ca3af');
  analysisList.innerHTML = '<li>Vérifie le backend et la base de données.</li>';
});