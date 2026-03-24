# newEAU — Documentation développeur (français, version flux de données)

## 1. Objectif du document

Ce document répond principalement à quatre questions :

1. Comment le code est structuré ;
2. À quoi sert chaque module ;
3. Comment les données circulent du back-end vers le front-end ;
4. Comment la couleur finale de la carte est produite.

---

## 2. Architecture globale (en une ligne)

`MySQL -> Service -> Servlet (JSON API) -> app.js (fetch) -> coloration Leaflet GeoJSON`

Le front reçoit, pour chaque commune, un statut et une couleur, puis fait la jointure avec les polygones GeoJSON via `code_insee`.

---

## 3. Structure et responsabilités

### 3.1 Back-end (`src/main/java/com/waterquality`)

- `controller/` : points d'entrée HTTP (Servlets)
  - `MapDataServlet` : données de coloration de la carte
  - `SearchServlet` : recherche de communes
  - `LatestServlet` : dernier prélèvement d'une commune
  - `DetailsServlet` : détails analytiques d'un prélèvement
- `service/` : logique métier et requêtes SQL
  - `MapDataService`, `SearchService`, `LatestService`, `DetailsService`
- `util/ConformityColor` : conversion des 4 indicateurs de conformité en `color/status`
- `dao/DatabaseConnection` : connexion JDBC

### 3.2 Front-end (`src/main/webapp`)

- `embed.html` : page carte
- `app.js` : chargement données, rendu carte, recherche, popup, panneau détail
- `style.css` : styles
- `data/*.geojson` : géométries communes/départements (métropole + DROM)

---

## 4. API utilisées par le front

| Méthode | Route | Usage |
|---|---|---|
| GET | `/api/map-data` | source principale de coloration communale |
| GET | `/api/search?q=...` | autocomplétion recherche |
| GET | `/api/latest/{codeInsee}` | dernier prélèvement d'une commune |
| GET | `/api/details/{prelevementId}` | résultats analytiques détaillés |

Le front peut changer la base API via `?api=...`, par exemple :
`embed.html?api=http://localhost:8082/newEAU/api`

---

## 5. Flux de données principal

### 5.1 Chargement initial de la carte

1. `loadMap()` démarre ;
2. `renderCommunesChoropleth([])` dessine immédiatement tous les contours communaux (teinte neutre) ;
3. appel HTTP `GET /api/map-data` ;
4. réception de `features` (`id/name/departement/color/status`) ;
5. `renderCommunesChoropleth(features)` recolore la couche avec les données réelles ;
6. clic utilisateur -> flux détail (`/latest` puis `/details`).

Avantage : la carte s'affiche tout de suite, même si l'API est lente.

### 5.2 Flux recherche -> recentrage -> popup

1. `runSearch()` interroge `/api/search` ;
2. sélection d'un résultat :
   - `focusSearchResultOnMap(pick)` recentre la carte ;
   - `openSearchResultMapPopup(pick, latlng)` ouvre un popup au point ciblé ;
   - `showCommuneDetails(pick)` alimente le panneau latéral.

### 5.3 Flux panneau détail

1. `showCommuneDetails(city)` appelle `/api/latest/{codeInsee}` ;
2. récupère `prelevement.id` ;
3. `loadAnalyses(prelevementId)` appelle `/api/details/{id}` ;
4. le front affiche paramètres, valeurs, limites et dépassements.

---

## 6. Code principal expliqué

### 6.1 `MapDataService#getMapFeatures` (back-end clé)

Responsabilité : produire l'état cartographique par commune.

Étapes :

1. jointure `communes` + dernier `prelevement` ;
2. lecture des 4 champs de conformité ;
3. calcul couleur/statut via `ConformityColor.fromPrelevement(...)` ;
4. sérialisation JSON par `MapDataServlet`.

Format renvoyé :

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

### 6.2 `renderCommunesChoropleth(features)` (front-end clé)

Rôle : appliquer les couleurs API aux polygones GeoJSON.

Mécanisme :

1. création d'une map `code_insee -> feature API` ;
2. parcours des géométries communales ;
3. extraction du code via `getCommuneCode` ;
4. style via `createCommuneStyle(mapFeature)` ;
5. interactions via `bindCommuneInteractions`.

Le principe central est : **géométrie locale + attributs API = rendu final**.

### 6.3 `createCommuneStyle(mapFeature)`

- utilise `mapFeature.color` si disponible ;
- sinon, teinte par défaut (gris clair) ;
- passe ensuite par `resolveDisplayColor` pour le mode daltonien.

### 6.4 `bindCommuneInteractions(...)`

Gère :

- hover / mouseout ;
- popup commune ;
- refresh de données via `/latest` au clic/ouverture ;
- ouverture de la fiche détaillée dans le panneau.

---

## 7. Règles de couleur

Les règles sont calculées côté serveur (`ConformityColor`) :

- tous conformes -> vert
- référence conforme + indicateur anormal -> jaune
- bactério conforme + chimie non conforme -> orange
- sans données -> gris
- autres cas non conformes -> rouge

Le front ne fait pas le calcul métier : il affiche.

---

## 8. Fonctions front importantes (`app.js`)

- `loadMap` : bootstrap des données et rendu initial
- `renderCommunesChoropleth` : dessin de la couche colorée
- `runSearch` : recherche asynchrone
- `resolveCommuneLatLng` / `focusSearchResultOnMap` : recentrage après recherche
- `openSearchResultMapPopup` : popup au point trouvé
- `showCommuneDetails` / `loadAnalyses` : fiche détaillée
- `refreshMapColors` : re-rendu (incluant mode daltonien)
- `applyTerritoryView` : actions de navigation territoriale

---

## 9. Diagnostic rapide “carte grise / pas de couleur”

1. Vérifier la réponse de `/api/map-data` ;
2. Vérifier l'alignement `id` (API) vs `code_insee` (GeoJSON) ;
3. Vérifier les erreurs réseau sur `/latest` et `/details` ;
4. En Docker, vérifier les montages de fichiers statiques (éviter mélange `src` / `target` anciens).

---

## 10. Exécution et déploiement

1. Initialiser MySQL avec `database/schema.sql` ;
2. `mvn clean package` ;
3. déployer `target/newEAU-1.0-SNAPSHOT.war` sur Tomcat ;
4. ouvrir `embed.html` ou `index.html` (avec `?api=...` si besoin).

---

## 11. Résumé en une phrase

Le back-end calcule une couleur/statut par commune, le front associe ces données aux polygones GeoJSON par code INSEE, puis affiche la carte colorée et les détails analytiques au clic.
