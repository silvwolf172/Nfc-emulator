# Sillage — liseuse et éditeur GPX pour Android

Sillage lit, crée et modifie des fichiers GPX. L'interface suit **Material You** (couleurs dynamiques
tirées du fond d'écran) et utilise des surfaces en **verre liquide** qui réagissent au toucher : elles
gonflent sous le doigt, s'étirent dans la direction du geste, suivent légèrement le doigt puis
reviennent en rebondissant. Un reflet lumineux suit aussi le doigt.

| Bibliothèque | Édition | Profil altimétrique | Thème sombre |
|---|---|---|---|
| ![](docs/screenshots/library_light.jpg) | ![](docs/screenshots/editor.jpg) | ![](docs/screenshots/viewer_profile.jpg) | ![](docs/screenshots/viewer_dark.jpg) |

<sub>Ces captures sont générées automatiquement par les tests (Robolectric). Les tuiles de la carte n'ont
pas toujours le temps d'arriver pendant le test : on voit alors la grille d'attente à la place.</sub>

## Fonctionnalités

**Lire**
- Import de fichiers GPX 1.0 / 1.1 (sélecteur de fichiers, « Ouvrir avec », partage depuis une autre app).
- Carte OpenStreetMap avec traces, itinéraires (en pointillés), repères, sens de parcours, départ/arrivée.
- Statistiques : distance, dénivelés (lissés contre le bruit GPS / MNT), durée, temps actif, vitesses,
  altitudes min/max.
- Profil altimétrique interactif : en le parcourant du doigt, le point correspondant s'affiche sur la carte.
- Liste des traces, sections, itinéraires et repères ; fiche détaillée d'un repère.
- Fonds de carte : Auto, Clair, Nuit, OpenStreetMap, Topographique (OpenTopoMap), CyclOSM.

**Créer et modifier**
- Outils : *Tracer* (ajouter des points), *Ajuster* (glisser un point, toucher la ligne pour insérer),
  *Gommer*, *Repère* (poser, renommer, décrire, déplacer).
- **Suivre les chemins** : en traçant, le segment suit les sentiers et routes (à pied, vélo, voiture).
- Annuler / rétablir, nouvelle section, nouvelle trace, inverser le sens, conversion trace ↔ itinéraire,
  simplification (Douglas-Peucker), ajout automatique des altitudes manquantes.
- Métadonnées du fichier (nom, description, auteur).

**Gérer**
- Recherche, renommage, duplication, suppression, partage, « Enregistrer sous… » (export GPX 1.1).
- Fichiers d'exemple pour découvrir l'app.

## Le verre liquide

Le code se trouve dans `app/src/main/java/com/sillage/gpx/ui/glass/`.

- `GlassBackdrop` enregistre ce qui est derrière le verre (la carte, ou le fond animé de la
  bibliothèque) dans un `GraphicsLayer`. Chaque surface de verre le redessine à sa position.
- `GlassSurface` applique un flou et une saturation (Android 12+), puis un **shader AGSL de lentille**
  (Android 13+) qui dévie la lumière près des bords, avec une légère dispersion chromatique et un liseré
  de Fresnel. Avant Android 12, le verre devient une surface teintée plus opaque.
- `Bounce` gère la physique au toucher (ressorts peu amortis, étirement directionnel, élastique) sans
  bloquer le défilement des listes. Si une liste prend le geste, l'élément revient en place.
- `LiquidTabs` : l'indicateur est une goutte de verre. Elle s'étire en se déplaçant, suit le doigt quand
  on glisse et gonfle à l'appui.

## Compiler

Prérequis : JDK 17+ et le SDK Android (API 36).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # APK minifié, signé avec la clé de debug
./gradlew :app:testDebugUnitTest    # tests GPX + captures d'écran dans app/build/screenshots
```

Le workflow GitHub Actions (`.github/workflows/android.yml`) lance les tests et publie les APK et les
captures d'écran comme artefacts de chaque build.

Minimum : Android 8.0 (API 26). Couleurs dynamiques : Android 12+. Réfraction : Android 13+.

## Services en ligne utilisés (gratuits, sans clé)

| Usage | Service |
|---|---|
| Fonds de carte | [OpenStreetMap](https://www.openstreetmap.org/copyright), [OpenTopoMap](https://opentopomap.org), [CyclOSM](https://www.cyclosm.org) |
| Suivre les chemins | Serveurs OSRM de [FOSSGIS / routing.openstreetmap.de](https://routing.openstreetmap.de) |
| Altitudes | [Open-Meteo Elevation API](https://open-meteo.com/en/docs/elevation-api) (Copernicus DEM) |

Ces services ont des conditions d'usage raisonnable. Pour une diffusion à grande échelle, il faudra
passer par un fournisseur de tuiles et de routage dédié.

## Limites connues

- Les extensions GPX propriétaires (fréquence cardiaque, cadence, etc.) sont ignorées. Elles sont donc
  perdues si l'on réenregistre un fichier qui en contient.
- Déplacer un point efface son altitude : utilisez « Ajouter les altitudes » pour la recalculer.
- Les exemples sont des tracés calculés sur OpenStreetMap, avec des horaires simulés.

## Architecture

```
gpx/      modèle, lecture SAX, écriture GPX 1.1, statistiques, opérations d'édition (pures)
data/     bibliothèque de fichiers, services en ligne
ui/       ViewModel, écrans (bibliothèque, lecture/édition), carte osmdroid, verre liquide, thème
```
