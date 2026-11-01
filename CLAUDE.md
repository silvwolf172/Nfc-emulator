# Sillage — consignes du projet

## Règle : chaque APK va dans les Releases GitHub
Chaque APK produit doit être publié dans les **Releases GitHub** du dépôt.
C'est automatique : `.github/workflows/android.yml` compile à chaque push et crée une release
`v1.0.<numéro de build>` avec `Sillage.apk` en pièce jointe. Ne pas retirer cette étape ; après un push,
vérifier que le workflow a réussi et que la release existe.

## Signature
Les APK doivent être signés avec une clé stable, sinon une nouvelle version ne s'installe pas par-dessus
la précédente. La clé n'est jamais versionnée : elle vient des secrets GitHub `SILLAGE_KEYSTORE_BASE64`,
`SILLAGE_KEYSTORE_PASSWORD`, `SILLAGE_KEY_ALIAS`, `SILLAGE_KEY_PASSWORD` (variables d'environnement
`SILLAGE_KEYSTORE_FILE`, etc. en local). Sans ces secrets, la CI signe avec une clé temporaire.

## Commandes
- Tests : `./gradlew :app:testDebugUnitTest` (captures d'écran dans `app/build/screenshots`)
- APK : `./gradlew :app:assembleRelease`
- Derrière un proxy limité, `ROBOLECTRIC_REPO_URL=<miroir Maven Central>` pour les jars Robolectric.

## Langue
Interface et textes en français.
