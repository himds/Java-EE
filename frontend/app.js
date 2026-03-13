const API_BASE = 'http://localhost:8080/water-quality/api';
const COMMUNES_GEOJSON_URL = './data/communes.geojson';
const DEPARTMENTS_GEOJSON_URL = './data/departements.geojson';
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

let currentMode = 'regions';
let currentYear = null;
let currentPollutant = 'all';
let currentSearchItems = [];


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
  return 'neutral';
}

function setStatusBadge(text, color) {
  statusBadge.className = `status-badge ${badgeClassFromColor(color)}`;
  statusBadge.textContent = text || 'Aucune donnée';
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
  analysisList.innerHTML = '<li>Chargement...</li>';
  const response = await fetch(`${API_BASE}/details/${prelevementId}`);
  const data = await response.json();
  if (!Array.isArray(data) || data.length === 0) {
    analysisList.innerHTML = '<li>Aucune analyse détaillée disponible.</li>';
    return;
  }
  analysisList.innerHTML = data.map(item => `
    <li>
      <strong>${escapeHtml(item.parametre)}</strong><br>
      Valeur mesurée: ${item.valeurMesuree ?? '—'} · Limite légale: ${item.limiteLegale ?? '—'}
    </li>
  `).join('');
}

function setMetrics(viewText, sampleCount) {
  metricView.textContent = viewText;
  metricPollutant.textContent = labelForPollutant(currentPollutant);
  metricYear.textContent = yearLabel();
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
    analysisList.innerHTML = '<li>Aucun prélèvement trouvé.</li>';
    return;
  }
  const latest = await latestResponse.json();
  const prelevement = latest.prelevement;
  communeMeta.textContent += ` · Dernier prélèvement: ${prelevement.dateprel ?? '—'}`;
  setStatusBadge(latest.tone || city.status, prelevement.color);
  await loadAnalyses(prelevement.id);
}

function showRegionDetails(feature) {
  openPanel();
  detailHeading.textContent = zoneFallbackMode === 'communes' ? 'Commune' : 'Département';
  communeTitle.textContent = feature.name;
  setMetrics(zoneFallbackMode === 'communes' ? 'Choropleth communal' : 'Choropleth départemental', feature.sampleCount ?? 1);
  communeMeta.textContent = zoneFallbackMode === 'communes'
    ? `Code INSEE: ${feature.id ?? '—'} · Département: ${feature.departement ?? '—'} · Statut: ${feature.status ?? '—'}`
    : `Département: ${feature.departement ?? '—'} · Statut: ${feature.status ?? '—'} · Communes agrégées: ${feature.sampleCount ?? 0}`;
  setStatusBadge(feature.status, feature.color);
  analysisList.innerHTML = zoneFallbackMode === 'communes'
    ? `
    <li>Zone coloriée à partir du fond communal France.</li>
    <li>Département: ${feature.departement ?? '—'}.</li>
    <li>Filtre actif: ${labelForPollutant(currentPollutant)} · ${yearLabel()}.</li>
    <li>Cette vue correspond à une carte choropleth par commune, sans routes ni fond OSM.</li>
  `
    : `
    <li>Le fond communal est trop lourd ou n’a pas pu être affiché correctement.</li>
    <li>Affichage de secours: carte départementale colorée.</li>
    <li>Communes agrégées: ${feature.sampleCount ?? 0}.</li>
    <li>Filtre actif: ${labelForPollutant(currentPollutant)} · ${yearLabel()}.</li>
  `;
}

async function ensureCommunesGeoJson() {
  if (communesGeoJson) return communesGeoJson;
  const response = await fetch(COMMUNES_GEOJSON_URL);
  communesGeoJson = await response.json();
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
  choroplethLayer = L.geoJSON(geojson, {
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
  zoneFallbackMode = 'communes';
  try {
    await renderCommunesChoropleth(features);
  } catch (error) {
    console.warn('Commune choropleth failed, fallback to departments', error);
    zoneFallbackMode = 'departments';
    await renderDepartmentsFallback(features);
  }
}

function addCommuneMarker(city, boundsAccumulator) {
  if (city.latitude == null || city.longitude == null) return;
  const color = city.color || '#9ca3af';
  boundsAccumulator.push([city.latitude, city.longitude]);

  const marker = L.circleMarker([city.latitude, city.longitude], {
    radius: 8,
    color,
    fillColor: color,
    fillOpacity: 0.9,
    weight: 2
  }).addTo(markerLayer);

  marker.bindPopup(`
    <b>${escapeHtml(city.nomCommune)}</b><br>
    Statut: ${escapeHtml(city.status || 'Conforme')}<br>
    Département: ${escapeHtml(city.departement || '—')}<br>
    <button type="button" class="popup-btn" data-code="${escapeHtml(city.codeInsee)}">Voir la fiche</button>
  `);

  marker.on('popupopen', () => {
    setTimeout(() => {
      const btn = document.querySelector(`.popup-btn[data-code="${CSS.escape(city.codeInsee)}"]`);
      if (btn) btn.onclick = () => showCommuneDetails(city);
    }, 0);
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
  clearChoropleth();
  const boundsAccumulator = [];

  if (currentMode === 'points') {
    try {
      const response = await fetch(`${API_BASE}/communes${queryString()}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const cities = await response.json();
      cities.forEach(city => addCommuneMarker(city, boundsAccumulator));
      if (boundsAccumulator.length) map.fitBounds(boundsAccumulator, { padding: [40, 40] });
    } catch (error) {
      openPanel();
      communeTitle.textContent = 'Backend indisponible';
      detailHeading.textContent = 'Mode point indisponible';
      communeMeta.textContent = 'Le backend ne répond pas encore, donc les points communaux ne peuvent pas être chargés.';
      setStatusBadge('Sans backend', '#9ca3af');
      analysisList.innerHTML = '<li>Démarre l’API si tu veux la vue points.</li>';
    }
  } else {
    try {
      const response = await fetch(`${API_BASE}/map-data${queryString({ mode: 'regions' })}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const mapData = await response.json();
      await renderChoropleth(mapData.features || []);
    } catch (error) {
      console.warn('API map-data unavailable, using local demo choropleth', error);
      const localGeoJson = await ensureDepartmentsGeoJson();
      zoneFallbackMode = 'departments';
      await renderDepartmentsFallback(demoFeaturesFromGeoJson(localGeoJson, 96));
      openPanel();
      communeTitle.textContent = 'Carte de démonstration';
      detailHeading.textContent = 'Affichage local';
      communeMeta.textContent = 'Le backend ne répond pas encore. J’affiche donc une carte administrative locale de démonstration pour que la carte soit visible immédiatement.';
      setStatusBadge('Démo locale', '#22c55e');
      analysisList.innerHTML = '<li>Le fond administratif est bien chargé.</li><li>Les couleurs actuelles sont des couleurs de démonstration locales.</li><li>Quand le backend répondra, elles seront remplacées par les vraies données.</li>';
    }
  }

  setMetrics(currentMode === 'points' ? 'Commune (point)' : (zoneFallbackMode === 'communes' ? 'Commune (zone)' : 'Département (secours)'), '—');
}

async function runSearch() {
  const value = searchInput.value.trim();
  results.innerHTML = '';
  results.classList.remove('has-items');
  currentSearchItems = [];
  if (value.length < 2) return;

  const response = await fetch(`${API_BASE}/search${queryString({ q: value })}`);
  const data = await response.json();
  currentSearchItems = data;

  data.forEach(city => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'result-item';
    button.textContent = `${city.nomCommune} (${city.departement || 'N/A'})`;
    button.onclick = () => {
      results.innerHTML = '';
      results.classList.remove('has-items');
      if (city.latitude != null && city.longitude != null) map.setView([city.latitude, city.longitude], 11);
      showCommuneDetails(city);
    };
    results.appendChild(button);
  });

  if (data.length) results.classList.add('has-items');
}

searchInput.addEventListener('input', runSearch);
yearFilter.addEventListener('change', async () => {
  currentYear = yearFilter.value === 'latest' ? null : yearFilter.value;
  await loadMap();
  await runSearch();
});
categoryFilter.addEventListener('change', async () => {
  currentPollutant = categoryFilter.value;
  await loadMap();
  await runSearch();
});
viewModeFilter.addEventListener('change', async () => {
  currentMode = viewModeFilter.value;
  await loadMap();
});

loadMap().catch(err => {
  console.error('Erreur chargement carte', err);
  openPanel();
  communeTitle.textContent = 'Carte indisponible';
  detailHeading.textContent = 'Erreur';
  communeMeta.textContent = 'Le design et les filtres sont prêts, mais l’API ne répond pas encore.';
  setStatusBadge('Indisponible', '#9ca3af');
  analysisList.innerHTML = '<li>Vérifie le backend et la base de données.</li>';
});
