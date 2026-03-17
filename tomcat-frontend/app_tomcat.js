// 修改API地址为相对路径，因为前端和后端在同一个服务器上
const API_BASE = '/water-quality/api';  // 相对路径，指向同一个Tomcat应用
const STATIC_DATA_URL = './data/communes.json';
const COMMUNES_GEOJSON_URL = './data/communes.geojson';
const DEPARTMENTS_GEOJSON_URL = './data/departements.geojson';

// 其余代码保持不变...
// 这里只显示修改部分，完整代码需要从原app.js复制

const map = L.map('map', { zoomControl: false, attributionControl: false }).setView([46.5, 2.5], 6);
L.control.zoom({ position: 'bottomright' }).addTo(map);

const markerLayer = L.layerGroup().addTo(map);
let choroplethLayer = null;
let communesGeoJson = null;
let departmentsGeoJson = null;
let zoneFallbackMode = 'communes';
const appShell = document.querySelector('.app-shell');
const results = document.getElementById('results');
const searchInput = document.getElementById('search');
const sidePanel = document.getElementById('side-panel');
const panelToggle = document.getElementById('panel-toggle');
const closePanel = document.getElementById('close-panel');
const communeTitle = document.getElementById('commune-title');
const communeMeta = document.getElementById('commune-meta');
const analysisList = document.getElementById('analysis-list');
const legendToggle = document.getElementById('legend-toggle');
const legendBody = document.getElementById('legend-body');
const legendArrow = document.getElementById('legend-arrow');
const yearFilter = document.getElementById('year-filter');
const categoryFilter = document.getElementById('category-filter');
const viewModeFilter = document.getElementById('view-mode');
const statusBadge = document.getElementById('status-badge');
const metricView = document.getElementById('metric-view');
const metricPollutant = document.getElementById('metric-pollutant');
const metricYear = document.getElementById('metric-year');
const metricSamples = document.getElementById('metric-samples');
const detailHeading = document.getElementById('detail-heading');
const territoryJumpButtons = document.querySelectorAll('.territory-btn');

let currentMode = 'regions';
let currentYear = null;
let currentPollutant = 'all';
let currentSearchItems = [];

const TERRITORY_VIEWS = {
  metro: [[41.0, -5.8], [51.7, 9.8]],
  guadeloupe: [[15.7, -61.9], [16.6, -60.9]],
  martinique: [[14.25, -61.35], [14.95, -60.75]],
  guyane: [[2.0, -54.9], [6.1, -51.3]],
  reunion: [[-21.45, 55.15], [-20.8, 55.95]],
  mayotte: [[-13.1, 45.0], [-12.45, 45.4]]
};

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[char]));
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

function queryString(extra = {}) {
  const params = new URLSearchParams();
  if (currentYear) params.set('year', currentYear);
  if (currentPollutant && currentPollutant !== 'all') params.set('pollutant', currentPollutant);
  Object.entries(extra).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') params.set(key, value);
  });
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

function labelForPollutant(value) {
  return ({ all: 'Tous polluants', bacterio: 'Bactériologique', chimique: 'Chimique', reference: 'Référence qualité' })[value] || value;
}

function yearLabel() {
  return currentYear || 'Dernière analyse';
}

function badgeClassFromColor(color) {
  if (color === '#22c55e') return 'green';
  if (color === '#facc15') return 'yellow';
  if (color === '#f97316') return 'orange';
  if (color === '#e11d48') return 'red';
  return 'gray';
}

function showStatusBadge(text, color) {
  statusBadge.textContent = text;
  statusBadge.className = `status-badge ${badgeClassFromColor(color)}`;
  statusBadge.classList.remove('is-hidden');
}

function hideStatusBadge() {
  statusBadge.classList.add('is-hidden');
}

function updateMetrics(mode, pollutant, year, sampleCount) {
  metricView.textContent = mode === 'communes' ? 'Communes' : mode === 'departements' ? 'Départements' : 'Régions';
  metricPollutant.textContent = labelForPollutant(pollutant);
  metricYear.textContent = yearLabel();
  metricSamples.textContent = sampleCount?.toLocaleString('fr') || '0';
}

function showRegionDetails(region) {
  openPanel();
  communeTitle.textContent = region.name;
  communeMeta.innerHTML = `
    <span class="meta-item">${region.departement || '—'}</span>
    <span class="meta-item">${region.sampleCount || 0} prélèvements</span>
  `;
  analysisList.innerHTML = `<li class="analysis-item"><span class="analysis-status ${badgeClassFromColor(region.color)}">${region.status}</span></li>`;
  detailHeading.textContent = 'Dernier prélèvement';
}

function renderRegionPopup(region) {
  return `
    <div class="popup">
      <h4 class="popup-title">${escapeHtml(region.name)}</h4>
      <p class="popup-meta">${region.departement || '—'} • ${region.sampleCount || 0} prélèvements</p>
      <p class="popup-status"><span class="status-badge ${badgeClassFromColor(region.color)}">${region.status}</span></p>
      <button class="popup-btn" data-region="${escapeHtml(region.id)}">Voir détails</button>
    </div>
  `;
}

// 修改：添加API测试和回退逻辑
async function testApiConnection() {
  try {
    console.log('测试API连接...');
    const response = await fetch(`${API_BASE}/communes`);
    if (response.ok) {
      console.log('✅ API连接成功');
      return true;
    } else {
      console.warn(`⚠️ API返回错误: ${response.status}`);
      return false;
    }
  } catch (error) {
    console.warn('❌ API连接失败:', error.message);
    return false;
  }
}

async function fetchWithFallback(url, fallbackUrl) {
  try {
    console.log(`尝试从API获取数据: ${url}`);
    const response = await fetch(url);
    if (response.ok) {
      const data = await response.json();
      console.log(`✅ API返回 ${data.length} 条记录`);
      return data;
    } else {
      console.warn(`⚠️ API返回错误 ${response.status}, 使用静态数据`);
      throw new Error(`API错误: ${response.status}`);
    }
  } catch (error) {
    console.log(`🔄 API失败, 使用静态数据: ${error.message}`);
    if (fallbackUrl) {
      const fallbackResponse = await fetch(fallbackUrl);
      const data = await fallbackResponse.json();
      console.log(`📁 静态数据加载 ${data.length} 条记录`);
      return data;
    }
    throw error;
  }
}

async function loadCommunesData() {
  console.log('加载市镇数据...');
  try {
    // 首先尝试API，失败时使用静态JSON
    const data = await fetchWithFallback(`${API_BASE}/communes${queryString()}`, STATIC_DATA_URL);
    console.log(`成功加载 ${data.length} 条市镇记录`);
    return data;
  } catch (error) {
    console.error('无法加载市镇数据:', error);
    // 返回空数组而不是抛出错误
    return [];
  }
}

async function ensureCommunesGeoJson() {
  if (communesGeoJson) return communesGeoJson;
  console.log('加载GeoJSON数据...');
  const response = await fetch(COMMUNES_GEOJSON_URL);
  communesGeoJson = await response.json();
  console.log(`加载了 ${communesGeoJson.features.length} 个GeoJSON特征`);
  return communesGeoJson;
}

async function ensureDepartmentsGeoJson() {
  if (departmentsGeoJson) return departmentsGeoJson;
  const response = await fetch(DEPARTMENTS_GEOJSON_URL);
  departmentsGeoJson = await response.json();
  return departmentsGeoJson;
}

function normalizeCommuneCode(code) {
  if (!code) return '';
  return String(code).trim().toUpperCase();
}

function getCommuneCode(feature) {
  const props = feature.properties || {};
  return normalizeCommuneCode(props.code || props.insee || props.code_insee || props.id);
}

function getDepartmentCode(feature) {
  const props = feature.properties || {};
  return normalizeCommuneCode(props.code || props.code_departement || props.id);
}

function createCommuneStyle(mapFeature) {
  const color = mapFeature?.color || '#cbd5e1';
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
  const color = mapFeature?.color || '#cbd5e1';
  return {
    color: '#0f172a',
    weight: 2.2,
    opacity: 1,
    fillColor: color,
    fillOpacity: 1,
    dashArray: ''
  };
}

function bindCommuneInteractions(layer, mapFeature, fallbackName) {
  const featureData = mapFeature || {
    id: fallbackName,
    name: fallbackName,
    departement: fallbackName,
    status: 'Aucune donnée',
    color: '#9ca3af',
    sampleCount: 0
  };

  layer.bindPopup(renderRegionPopup(featureData));
  layer.on('click', () => showRegionDetails(featureData));
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

async function renderCommunesChoropleth(features) {
  const geojson = await ensureCommunesGeoJson();
  const featureMap = new Map(features.map(item => [normalizeCommuneCode(item.id), item]));

  clearChoropleth();
  
  // 性能优化：只显示有数据的市镇
  const filteredFeatures = geojson.features.filter(feature => {
    const communeCode = getCommuneCode(feature);
    return featureMap.has(communeCode);
  });
  
  // 如果过滤后特征太少，显示所有市镇但用灰色表示无数据
  const featuresToRender = filteredFeatures.length > 50 ? 
    filteredFeatures : 
    geojson.features.slice(0, 500); // 限制数量以提高性能
  
  console.log(`渲染 ${featuresToRender.length} 个市镇特征，其中 ${filteredFeatures.length} 个有数据`);
  
  choroplethLayer = L.geoJSON({ type: 'FeatureCollection', features: featuresToRender }, {
    style: geoFeature => {
      const communeCode = getCommuneCode(geoFeature);
      const mapFeature = featureMap.get(communeCode);
      return createCommuneStyle(mapFeature);
    },
    onEachFeature: (geoFeature, layer) => {
      const props = geoFeature.properties || {};
      const communeCode = getCommuneCode(geoFeature);
      const mapFeature = featureMap.get(communeCode);
      const fallbackName = props.nom || props.name || `Commune ${communeCode}`;
      
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
        
        // 如果API没有提供坐标，使用几何中心
        if (mapFeature && !mapFeature.latitude && !mapFeature.longitude) {
          mapFeature.latitude = centerLat;
          mapFeature.longitude = centerLng;
        }
      }
      
      bindCommuneInteractions(layer, mapFeature, fallbackName);
    }
  }).addTo(map);

  try { map.fitBounds(choroplethLayer.getBounds(), { padding: [20, 20] }); } catch {}
}

async function renderDepartmentsFallback(features) {
  const geojson = await ensureDepartmentsGeoJson();
  const grouped = new Map();
  for (const item of features) {
    const key = normalizeCommuneCode(item.departement);
    const prev = grouped.get(key);
    if (!prev) {
      grouped.set(key, {
        id: `dept-${key}`,
        name: `Département ${key}`,
        departement: key,
        color: item.color,
        status: item.status,
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

  try { map.fitBounds(choroplethLayer.getBounds(), { padding: [20, 20] }); } catch {}
}

async function renderChoropleth(features) {
  if (features.length === 0) {
    console.warn('没有数据可渲染');
    showStatusBadge('Aucune donnée disponible', '#9ca3af');
    return;
  }
  
  zoneFallbackMode = 'communes';
  try {
    await renderCommunesChoropleth(features);
    showStatusBadge(`${features.length} communes chargées`, '#22c55e');
  } catch (error) {
    console.warn('Commune choropleth failed, fallback to departments', error);
    zoneFallbackMode = 'departments';
    await renderDepartmentsFallback(features);
    showStatusBadge('Carte départementale (fallback)', '#facc15');
  }
}

function addCommuneMarker(city, boundsAccumulator) {
  if (city.latitude == null || city.longitude == null) return;
  const color = city.color || '#9ca3af';
  boundsAccumulator.push([city.latitude, city.longitude]);

  const marker = L.circleMarker([city.latitude, city.longitude], {
    radius: 8,
    fillColor: color,
    color: '#