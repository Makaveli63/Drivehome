plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.drivehome"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.drivehome"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "2.4"
    }

    // Clé de signature fixe (optionnelle) fournie par les secrets GitHub :
    // les mises à jour s'installent alors par-dessus l'ancienne version.
    // Sans secret, l'APK est signé avec la clé de débogage.
    val ksFile = file("release.keystore")
    val ksPass = System.getenv("SIGNING_PASSWORD").orEmpty()
    val hasKey = ksFile.exists() && ksPass.isNotEmpty()

    signingConfigs {
        if (hasKey) {
            create("fixed") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = System.getenv("SIGNING_ALIAS").orEmpty().ifEmpty { "drivehome" }
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("fixed") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "glb"
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Moteur 3D (Filament) pour afficher le vrai modèle de la voiture
    implementation("io.github.sceneview:sceneview:2.2.1")
    // Carte en direct (OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
