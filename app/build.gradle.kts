plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sillage.gpx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sillage.gpx"
        minSdk = 26
        targetSdk = 36
        // Sur GitHub Actions, chaque build a un numéro croissant : les mises à jour s'installent par-dessus.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Clé de signature stable fournie par l'environnement (secrets GitHub en CI) : indispensable pour que
        // chaque nouvelle version s'installe par-dessus la précédente. Sans elle, clé de debug locale.
        val keystore = System.getenv("SILLAGE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
        if (keystore != null) {
            create("sillage") {
                storeFile = keystore
                storePassword = System.getenv("SILLAGE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SILLAGE_KEY_ALIAS")
                keyPassword = System.getenv("SILLAGE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("sillage") ?: signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                test.systemProperty("roborazzi.test.record", "true")
                // Miroir Maven optionnel pour les jars Android de Robolectric (utile derrière un proxy limité).
                System.getenv("ROBOLECTRIC_REPO_URL")?.let { test.systemProperty("robolectric.dependency.repo.url", it) }
            }
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.osmdroid)
    implementation(libs.osmdroid.mapsforge)
    implementation(libs.play.services.location)
    // Play Services tire une vieille version de Fragment qui casse les retours de permissions.
    implementation(libs.androidx.fragment)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
