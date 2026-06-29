import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

android {
    namespace = "com.w11mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.w11mobile.windows11"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DEFAULT_WINDOWS_IMAGE_URL",
            "\"\"",
        )
    }

    signingConfigs {
        create("upload") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("upload")
        }
        release {
            signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // MVVM: ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Activity KTX (viewModels delegate)
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Networking & archive extraction
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

val alpineBusyboxOutput = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libalpine_busybox.so")

tasks.register("prepareAlpineBusybox") {
    val archiveUrl =
        "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"
    outputs.file(alpineBusyboxOutput)

    onlyIf {
        !alpineBusyboxOutput.asFile.exists() || alpineBusyboxOutput.asFile.length() == 0L
    }

    doLast {
        val outputFile = alpineBusyboxOutput.asFile
        outputFile.parentFile.mkdirs()
        val workDir = temporaryDir.resolve("alpine-busybox").apply { mkdirs() }
        val archive = workDir.resolve("alpine-minirootfs.tar.gz")

        URI(archiveUrl).toURL().openStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }

        providers.exec {
            workingDir = workDir
            commandLine("tar", "-xzf", archive.absolutePath, "./bin/busybox")
        }.result.get()

        workDir.resolve("bin/busybox").copyTo(outputFile, overwrite = true)
    }
}

tasks.named("preBuild") {
    dependsOn("prepareAlpineBusybox")
}
