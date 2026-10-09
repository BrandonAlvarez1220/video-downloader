plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") // genera el código de Room en tiempo de compilación
}

android {
    namespace = "com.brandon.videodownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brandon.videodownloader"
        minSdk = 29 // Android 10+: permite guardar en la galería (MediaStore) sin permisos de almacenamiento
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Llave fija y versionada en el repo para que cada APK compilado en la nube
        // tenga la MISMA firma y puedas actualizar la app sin desinstalarla.
        // No es secreta: sirve solo para uso personal (ver README).
        create("personal") {
            storeFile = file("signing.keystore")
            storePassword = "videodownloader"
            keyAlias = "videodownloader"
            keyPassword = "videodownloader"
        }
    }

    buildTypes {
        release {
            // Sin R8/minify: la librería usa reflexión (Jackson) y no vale la pena el riesgo.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    // Un APK por arquitectura de CPU: Python + ffmpeg nativos pesan mucho,
    // así cada APK solo lleva los binarios de tu procesador.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        // OBLIGATORIO para youtubedl-android: Python/ffmpeg se ejecutan como procesos
        // desde la carpeta de librerías nativas, así que deben extraerse al instalar.
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // expone versionName a la pantalla "Acerca de"
    }

    lint {
        // Proyecto personal: que una advertencia de lint no bloquee la compilación del APK.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Motor de descargas: Python + yt-dlp + ffmpeg empaquetados para Android.
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    // UI declarativa (Jetpack Compose) — piensa en Blazor/React, pero nativo.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // Descargas en segundo plano que sobreviven a cerrar la app.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Base de datos local (SQLite con ORM). Similar a EF Core.
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Reproductor integrado (el mismo motor que usa la app de YouTube).
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Carga de miniaturas desde internet.
    implementation("io.coil-kt:coil-compose:2.7.0")
}
