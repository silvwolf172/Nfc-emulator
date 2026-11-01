# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Rendu des cartes hors ligne (mapsforge) : thèmes et rendu chargés dynamiquement.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
-keep class org.osmdroid.mapsforge.** { *; }
