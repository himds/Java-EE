/**
 * Carte qualité de l'eau — inspiration « Dans Mon Eau » (Générations Futures / Data For Good)
 * https://dansmoneau.fr/ — carte par commune, couleurs = synthèse du contrôle sanitaire, clic → fiche détaillée.
 */

const API_BASE = (() => {
  const params = new URLSearchParams(window.location.search);
  return params.get('api') || 'http://localhost:8081/water-quality/api';
})();
const COMMUNES_GEOJSON_URL = './data/communes.geojson';
const COMMUNES_DROM_GEOJSON_URL = './data/communes-drom.geojson';
/** Vue initiale uniquement ; ensuite aucun setView/fitBounds automatique (zoom/deplacement manuel). */
const map = L.map('map', {
  zoomControl: false,
  attributionControl: false,
  center: [46.5, 2.5],
  zoom: 6
});
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  maxZoom: 19
}).addTo(map);
L.control.zoom({ position: 'bottomright' }).addTo(map);

const markerLayer = L.layerGroup().addTo(map);
let choroplethLayer = null;   // Couche choropleth communale uniquement (sans GeoJSON departemental).
let dromCardLayer = null;
let communesGeoJson = null;
let allMapDataFeatures = []; // Ensemble map-data (coloration + panneau lateral).
const appShell = document.querySelector('.app-shell');
if (appShell) {
  appShell.classList.add('no-transition');
  setTimeout(() => appShell.classList.remove('no-transition'), 400);
}
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
  const accent = resolveDisplayColor(feature.color || '#cbd5e1');
  const status = feature.status || 'Aucune donnée récente';
  return `
    <div class="water-popup">
      <div class="water-popup-accent" style="background:${accent}"></div>
      <div class="water-popup-inner">
        <div class="water-popup-kicker">Qualité de l'eau potable</div>
        <strong class="water-popup-title">${escapeHtml(feature.name)}</strong>
        <p class="water-popup-status">${escapeHtml(status)}</p>
        <p class="water-popup-note">Synthèse à partir du dernier prélèvement disponible en base (comme sur la carte nationale open data).</p>
        <button type="button" class="popup-btn water-popup-cta" data-region="${escapeHtml(feature.id)}">Voir la fiche détaillée</button>
      </div>
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
  analysisList.className = 'analysis-list';
  detailHeading.textContent = 'Dernières analyses officielles';
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

async function showRegionDetails(feature) {
  openPanel();
  analysisList.className = 'analysis-list';
  detailHeading.textContent = 'Qualité de l\'eau — commune';
  communeTitle.textContent = feature.name;
  setMetrics('Commune', feature.sampleCount ?? 1);
  communeMeta.textContent = `Code INSEE: ${feature.id ?? '—'} · Département: ${feature.departement ?? '—'} · Statut: ${feature.status ?? '—'}`;
  setStatusBadge(feature.status, feature.color);

  if (!feature.id) {
    analysisList.innerHTML = '<li>Sélectionnez une commune sur la carte pour afficher les analyses.</li>';
    return;
  }

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
  const baseStyle = () => createCommuneStyle(featureData);
  const hoverStyle = () => createCommuneHoverStyle(featureData);

  // Popup type « fiche » (style carte nationale eau)
  layer.bindPopup(renderRegionPopup(featureData), {
    minWidth: 280,
    maxWidth: 340,
    className: 'leaflet-water-popup',
    autoPanPadding: [12, 12]
  });
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
    layer.setStyle(hoverStyle());
    if (!L.Browser.ie && !L.Browser.opera && !L.Browser.edge) {
      layer.bringToFront();
    }
  });
  layer.on('mouseout', () => {
    layer.setStyle(baseStyle());
  });
  layer.on('popupopen', () => {
    if (codeInsee) {
      // Rafraichit aussi a l'ouverture du popup (evite un premier affichage tout gris).
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
      const btn = document.querySelector(`.water-popup-cta[data-region="${CSS.escape(String(featureData.id))}"]`);
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
}

async function renderCommunesChoropleth(features) {
  const geojson = await ensureCommunesGeoJson();
  // featureMap: code_insee (id) -> { color, status, ... } ; donnees backend/BDD, association par id cote front.
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

      // Calcule un centre a partir de la geometrie GeoJSON.
      if (geoFeature.geometry && geoFeature.geometry.type === 'Polygon' && geoFeature.geometry.coordinates) {
        const coordinates = geoFeature.geometry.coordinates[0];
        let sumLat = 0, sumLng = 0;
        for (const coord of coordinates) {
          sumLng += coord[0];
          sumLat += coord[1];
        }
        const centerLng = sumLng / coordinates.length;
        const centerLat = sumLat / coordinates.length;

        // Si un recentrage est necessaire, le front utilise le centroide GeoJSON ; pas d'injection dans l'objet backend.
      }

      bindCommuneInteractions(layer, mapFeature, fallbackName, codeInsee);
    }
  }).addTo(map);
  try { choroplethLayer.bringToFront(); } catch { /* ignore */ }
}

async function refreshMapColors() {
  await renderCommunesChoropleth(allMapDataFeatures);
}

function centroidFromGeoFeature(geoFeature) {
  if (!geoFeature?.geometry?.coordinates) return null;
  const g = geoFeature.geometry;
  let ring = null;
  if (g.type === 'Polygon' && g.coordinates[0]) ring = g.coordinates[0];
  else if (g.type === 'MultiPolygon' && g.coordinates[0]?.[0]) ring = g.coordinates[0][0];
  if (!Array.isArray(ring) || ring.length === 0) return null;
  let sumLat = 0, sumLng = 0, n = 0;
  for (const c of ring) {
    const lon = Array.isArray(c) ? c[0] : c;
    const lat = Array.isArray(c) ? c[1] : c;
    if (typeof lon === 'number' && typeof lat === 'number') { sumLng += lon; sumLat += lat; n++; }
  }
  return n ? [sumLat / n, sumLng / n] : null;
}

/** Recentrage via recherche : priorite aux coordonnees API, sinon centroide GeoJSON communal. */
async function resolveCommuneLatLng(city) {
  if (city.latitude != null && city.longitude != null) {
    const lat = Number(city.latitude);
    const lon = Number(city.longitude);
    if (Number.isFinite(lat) && Number.isFinite(lon)) return [lat, lon];
  }
  const geojson = await ensureCommunesGeoJson().catch(() => null);
  if (!geojson?.features?.length) return null;
  const targetCode = normalizeCommuneCode(city.codeInsee);
  const feature = geojson.features.find(f => normalizeCommuneCode(getCommuneCode(f)) === targetCode);
  if (!feature) return null;
  const c = centroidFromGeoFeature(feature);
  return c && c.length === 2 ? c : null;
}

const SEARCH_RESULT_ZOOM = 12;

/** Utilise uniquement apres selection de recherche : centre la carte sur la commune puis realigne apres invalidateSize. */
async function focusSearchResultOnMap(city) {
  const latlng = await resolveCommuneLatLng(city);
  if (!latlng) return null;
  map.setView(latlng, SEARCH_RESULT_ZOOM, { animate: true });
  setTimeout(() => {
    map.invalidateSize({ animate: false });
    map.setView(latlng, SEARCH_RESULT_ZOOM, { animate: false });
  }, 320);
  return L.latLng(latlng[0], latlng[1]);
}

/**
 * Apres selection dans la recherche, ouvre un popup au point cible avec le meme style que les polygones communaux (L.popup independant).
 */
async function openSearchResultMapPopup(pick, latlng) {
  if (!latlng) return;
  const featureData = {
    id: pick.codeInsee,
    name: pick.nomCommune,
    departement: pick.departement,
    status: pick.status || 'Aucune donnée',
    color: pick.color || '#cbd5e1',
    sampleCount: 1
  };
  if (pick.codeInsee) {
    try {
      const commune = await fetchCommuneFromBackend(pick.codeInsee);
      featureData.id = commune.codeInsee || featureData.id;
      featureData.name = commune.nomCommune || featureData.name;
      featureData.departement = commune.departement || featureData.departement;
      featureData.status = commune.status || featureData.status;
      featureData.color = commune.color || featureData.color;
    } catch { /* garder les données recherche */ }
  }

  const html = renderRegionPopup(featureData);
  const popup = L.popup({
    minWidth: 280,
    maxWidth: 340,
    className: 'leaflet-water-popup',
    autoPanPadding: [12, 12],
    autoPan: true,
    closeButton: true
  })
    .setLatLng(latlng)
    .setContent(html)
    .openOn(map);

  const wireCta = () => {
    const btn = document.querySelector(
      `.leaflet-popup-pane .water-popup-cta[data-region="${CSS.escape(String(featureData.id))}"]`
    );
    if (btn && !btn.dataset.wired) {
      btn.dataset.wired = '1';
      btn.onclick = () => {
        map.closePopup();
        showRegionDetails(featureData);
      };
    }
  };

  popup.on('add', () => setTimeout(wireCta, 0));
  setTimeout(wireCta, 200);
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
  allMapDataFeatures = [];

  // 1) Dessine d'abord tous les contours communaux (sans attendre l'API) pour afficher immediatement toutes les villes.
  await renderCommunesChoropleth([]);
  try {
    const gj = await ensureCommunesGeoJson();
    setMetrics('Communes', String((gj.features || []).length));
  } catch { setMetrics('Communes', '—'); }

  if (!sidePanel.classList.contains('is-hidden')) {
    communeTitle.textContent = 'Carte communale';
    communeMeta.textContent = 'Toutes les communes sont affichées. Chargement des couleurs depuis l\'API…';
    analysisList.innerHTML = '<li>Chargement des données de qualité…</li>';
  }

  try {
    const response = await fetch(`${API_BASE}/map-data${queryString()}`);
    const mapData = await response.json().catch(() => null);
    if (!response.ok) {
      const msg = (mapData && (mapData.message || mapData.error)) || `HTTP ${response.status}`;
      throw new Error(msg);
    }
    const features = mapData.features || [];
    allMapDataFeatures = features;
    await renderCommunesChoropleth(features);
    setMetrics('Communes', String(features.length));
    if (!sidePanel.classList.contains('is-hidden')) {
      communeMeta.textContent = 'Couleurs à jour. Cliquez sur une commune pour la fiche.';
    }
  } catch (error) {
    console.warn('API map-data indisponible, carte reste sur tous les contours communaux (sans couleurs API)', error);
    allMapDataFeatures = [];
    try {
      const gj2 = await ensureCommunesGeoJson();
      setMetrics('Communes', String((gj2.features || []).length));
    } catch { setMetrics('Communes', '0'); }
    if (!sidePanel.classList.contains('is-hidden')) {
      communeTitle.textContent = 'Données base indisponibles';
      detailHeading.textContent = 'Connexion BDD requise';
      communeMeta.textContent = 'Les communes restent visibles en gris. Démarrez le backend et MySQL pour les couleurs.';
      setStatusBadge('Sans BDD', '#9ca3af');
      analysisList.innerHTML = '<li><button type="button" class="popup-btn" id="check-connection-btn">Vérifier la connexion</button></li>';
      document.getElementById('check-connection-btn')?.addEventListener('click', runConnectionCheck);
    }
  }

  requestAnimationFrame(() => { map.invalidateSize(); });
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

  const inner = document.createElement('div');
  inner.className = 'dropdown-list';

  data.forEach(city => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'result-item';
    button.textContent = `${city.nomCommune} (${city.departement || 'N/A'})`;
    button.title = 'Centrer la carte sur cette commune et ouvrir la fiche';
    button.onclick = async () => {
      results.innerHTML = '';
      results.classList.remove('has-items');
      searchInput.value = city.nomCommune || '';
      const pick = {
        codeInsee: city.codeInsee,
        nomCommune: city.nomCommune,
        departement: city.departement,
        status: city.status,
        color: city.color,
        latitude: city.latitude,
        longitude: city.longitude
      };
      openPanel();
      const ll = await focusSearchResultOnMap(pick);
      await new Promise(r => setTimeout(r, 340));
      await openSearchResultMapPopup(pick, ll);
      showCommuneDetails(pick);
    };
    inner.appendChild(button);
  });

  results.innerHTML = '';
  results.appendChild(inner);
  if (data.length) results.classList.add('has-items');
}

// Lance la recherche a partir de 3 caracteres, debounce 300 ms, retour attendu < 1.5 s.
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

// Les communes restent toujours visibles ; le choropleth ne se masque plus selon le zoom.

// Bascule plein ecran (conteneur de carte)
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

async function applyTerritoryView(territoryKey) {
  if (!territoryKey || !TERRITORY_LABELS[territoryKey]) return;
  openPanel();
  setTerritoryButtonSelected(territoryKey);
  const label = TERRITORY_LABELS[territoryKey] || territoryKey;
  communeTitle.textContent = label;
  detailHeading.textContent = 'Territoire';
  const isDrom = DROM_KEYS.includes(territoryKey);
  clearDromCardLayer();
  clearCommuneLayer();
  // Re-dessine toujours toutes les communes (colorees si API disponible, sinon teinte claire).
  await renderCommunesChoropleth(allMapDataFeatures);
  if (isDrom) {
    const name = DROM_LABELS[territoryKey] || territoryKey;
    communeMeta.textContent = `Vue : ${name}. Zoomez et cliquez sur une commune pour voir la qualité de l'eau et les analyses.`;
    analysisList.innerHTML = '<li>Les communes sont colorées selon le dernier contrôle sanitaire disponible en base.</li>';
  } else {
    communeMeta.textContent = `Vue : ${label}. Cliquez sur une commune pour ouvrir la fiche détaillée (comme sur la carte nationale).`;
    analysisList.innerHTML = '<li>Navigation territoriale — la carte reste à l\'échelle communale.</li>';
  }
  setStatusBadge('Navigation', '#22c55e');
}

territoryIconBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    const territory = btn.getAttribute('data-territory');
    if (!territory || !TERRITORY_LABELS[territory]) return;
    applyTerritoryView(territory).catch(() => { });
  });
});

loadMap().catch(err => {
  console.error('Erreur chargement carte', err);
  // Ne pas ouvrir automatiquement le panneau (index doit rester "fermé" par défaut).
  if (!sidePanel.classList.contains('is-hidden')) {
    communeTitle.textContent = 'Carte indisponible';
    detailHeading.textContent = 'Erreur';
    communeMeta.textContent = 'Le design et les filtres sont prêts, mais l\'API ne répond pas encore.';
    setStatusBadge('Indisponible', '#9ca3af');
    analysisList.innerHTML = '<li>Vérifie le backend et la base de données.</li>';
  }
});