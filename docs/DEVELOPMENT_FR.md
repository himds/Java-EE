# newEAU — Documentation développeur (français)

## 1. Présentation du projet

**newEAU** est une application web de **visualisation de la qualité de l’eau**, inspirée de [dansmoneau.fr](https://dansmoneau.fr). Elle affiche une carte administrative de la France métropolitaine et des DROM (Guadeloupe, Martinique, Guyane, La Réunion, Mayotte), avec un code couleur par commune (vert / jaune / orange / rouge) selon la conformité des prélèvements, ainsi que la recherche, les fiches détail et un mode daltonien.

- **Back-end** : Java, Jakarta Servlet, MySQL, exposant des API de type REST.
- **Front-end** : HTML/CSS/JavaScript, carte Leaflet, interface en une page.
- **Données** : contours des communes et départements via GeoJSON ; couleurs de conformité calculées à partir de quatre champs de conformité des prélèvements en base.

---

## 2. Stack technique

| Couche   | Technologie |
|----------|-------------|
| Back-end | Java 8+, Jakarta Servlet API 6, Maven, MySQL 8 (JDBC) |
| Front-end| HTML5 / CSS3 / JavaScript natif, Leaflet 1.9.4 |
| Build    | Maven (packaging: war) |
| Déploiement | Tomcat ou tout conteneur Servlet compatible |

---

## 3. Structure du projet

```
newEAU/
├── pom.xml
├── database/
│   └── schema.sql              # Script de création des tables
├── datas/                       # Données brutes (import CSV/TXT)
│   ├── Table communes/
│   ├── Table prelevements/
│   └── Table resultats/
├── docs/
│   ├── DEVELOPMENT_ZH.md       # Documentation développeur (chinois)
│   └── DEVELOPMENT_FR.md      # Présent document (français)
├── src/main/
│   ├── java/com/waterquality/
│   │   ├── controller/         # Servlets (points d’entrée API)
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
│   │       ├── ConformityColor.java  # Quatre champs → couleur / libellé
│   │       └── ImportWaterData.java  # Import des données (main)
│   └── webapp/
│       ├── WEB-INF/web.xml
│       ├── data/
│       │   ├── README.md
│       │   ├── departements-drom.geojson
│       │   └── communes-drom.geojson
│       ├── embed.html          # Page carte (iframe ou direct)
│       ├── index.html          # Page d’accueil (avec iframe)
│       ├── app.js              # Carte, recherche, légende, mode daltonien
│       └── style.css
└── target/                     # Sortie Maven (war)
```

---

## 4. Base de données

### 4.1 Tables

- **communes** : code_insee, nom_commune, departement
- **prelevements** : prélèvements par commune (referenceprel, dateprel, quatre champs de conformité C/N)
- **resultats_analyses** : paramètres par prélèvement (parametre, valeur_mesuree, limite_legale)

Détails dans `database/schema.sql`.

### 4.2 Connexion

`DatabaseConnection.java` utilise par défaut :

- URL : `jdbc:mysql://localhost:3307/waterdb?...`
- Identifiants : modifiables via les variables d’environnement `DB_URL`, `DB_USER`, `DB_PASSWORD`.

---

## 5. API

| Méthode | Chemin | Description |
|--------|--------|-------------|
| GET | `/api/test` | Test de connectivité |
| GET | `/api/search?q=xxx` | Recherche fuzzy par nom de commune, retourne un tableau JSON |
| GET | `/api/map-data` | Toutes les communes avec id, name, departement, color, status (pour la carte) |
| GET | `/api/latest/{codeInsee}` | Dernier prélèvement de la commune + infos commune (dont color) |
| GET | `/api/details/{prelevementId}` | referenceprel et liste analyses (parametre, valeurMesuree, limiteLegale) |

L’URL de base de l’API est configurable côté front via le paramètre `api` (ex. `embed.html?api=http://localhost:8081/newEAU/api`).

---

## 6. Logique des couleurs (conformité → carte)

`ConformityColor.fromPrelevement(bacterio, chimique, refBact, refChim)` détermine la couleur à partir des quatre champs (C/N) :

| Condition | Couleur | Signification |
|-----------|---------|----------------|
| Tous C | Vert | Conforme |
| Référence conforme + indicateur(s) anormal(aux) | Jaune | Conformité santé, indicateur(s) anormal(aux) |
| Bactério conforme + chimie non conforme | Orange | Bactériologie conforme, chimie non conforme |
| Aucune donnée | Gris | Pas de prélèvement ou champs vides |
| Autre | Rouge | Non conforme |

La carte n’utilise ces couleurs que pour la couche **communes** (après clic sur un département). La couche départements reste grise au chargement.

---

## 7. Fonctionnalités front-end

- **Carte** : Leaflet, fond OSM ; couche départements (gris), puis couche communes par département avec couleurs issues de map-data.
- **Territoires** : Icônes dans la barre latérale (Métropole, Guadeloupe, Martinique, Guyane, La Réunion, Mayotte) avec fitBounds.
- **Recherche** : Requête débourrée vers `/api/search`, liste de suggestions, clic → zoom sur la commune et ouverture du panneau détail.
- **Panneau droit** : Infos commune/département, dernier prélèvement, section « Analyses détaillées » (referenceprel + parametre, valeurMesuree, limiteLegale).
- **Légende** : En bas à gauche, avec case à cocher « Mode daltonien » pour activer/désactiver la palette adaptée aux daltoniens (carte et badges mis à jour immédiatement).

---

## 8. Données GeoJSON

- **Optionnel** : `data/departements.geojson`, `data/communes.geojson` (métropole, téléchargeables depuis [Contours administratifs](https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/latest/geojson/)).
- **Inclus** : `data/departements-drom.geojson`, `data/communes-drom.geojson` (DROM, géométrie simplifiée).

Le front fusionne les features des fichiers principaux et DROM. Les propriétés attendues sont `code` (ou `code_insee`) et `nom`.

---

## 9. Lancement et déploiement

### 9.1 En local

1. MySQL : créer la base `waterdb`, exécuter `database/schema.sql` ; optionnel : lancer `ImportWaterData` pour importer les données.
2. Build : `mvn clean package` → `target/newEAU-1.0-SNAPSHOT.war`.
3. Déployer le war dans le répertoire `webapps/` de Tomcat (ou via l’IDE).
4. Ouvrir dans le navigateur `http://localhost:8080/newEAU/embed.html` (adapter port et contexte). Pour une API sur un autre port, utiliser `?api=http://...`.

### 9.2 Contexte d’application

Le chemin d’accès par défaut est `/newEAU/` (nom de l’artifact). L’API est exposée sous `http://<host>:<port>/newEAU/api`.

---

## 10. Mode daltonien

- La case « Mode daltonien » se trouve dans la légende en bas à gauche (« Légende — Qualité de l’eau »).
- Lorsqu’elle est cochée : couleurs de la carte, de la légende et du badge de statut passent sur une palette daltonien (bleu, jaune clair, orange, rouge-orange), et les couches département / communes sont rafraîchies.

---

## 11. Version et dépendances

- Version du projet : 1.0-SNAPSHOT (`pom.xml`).
- Dépendances principales : jakarta.servlet-api 6.0.0, mysql-connector-j 8.2.0, Leaflet 1.9.4 (CDN côté front).
