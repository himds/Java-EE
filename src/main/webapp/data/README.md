# Données GeoJSON — Carte qualité de l'eau

## Fichiers inclus (DROM)

- **departements-drom.geojson** : 5 départements d'outre-mer (971 Guadeloupe, 972 Martinique, 973 Guyane, 974 La Réunion, 976 Mayotte). Géométrie simplifiée (rectangles).
- **communes-drom.geojson** : quelques communes exemples par DROM (géométrie simplifiée).

L’application charge automatiquement ces fichiers et les fusionne avec les éventuels fichiers métropole ci‑dessous.

## Fichiers optionnels (Métropole + DROM complets)

Pour afficher **toute la France** (métropole + DROM) avec des contours réels :

1. Téléchargez les GeoJSON **Contours administratifs** (Étalab) :
   - Départements : [departements-1000m.geojson](https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/latest/geojson/departements-1000m.geojson) (ou `-100m` / `-50m` pour plus de détail)
   - Communes : [communes-1000m.geojson](https://etalab-datasets.geo.data.gouv.fr/contours-administratifs/latest/geojson/communes-1000m.geojson)

2. Enregistrez-les dans ce dossier sous les noms :
   - `departements.geojson`
   - `communes.geojson`

3. Les propriétés utilisées sont : **code** (INSEE), **nom**. Les fichiers Étalab sont compatibles.

Avec ces deux fichiers en place, l’application affiche métropole + DROM. Les fichiers `*-drom.geojson` restent chargés en plus (vous pouvez les supprimer si vous avez mis les fichiers complets).
