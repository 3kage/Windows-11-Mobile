import java.net.URI
import java.security.MessageDigest
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
        minSdk = 24
        targetSdk = 28
        versionCode = 50
        versionName = "1.9.1"

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

    lint {
        // Sideload builds keep targetSdk 28 for compatibility; Play lint would block release APK export.
        disable += "ExpiredTargetSdkVersion"
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
val prootNativeLibOutput = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libproot.so")
val prootLoaderNativeLibOutput = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libproot_loader.so")
val qemuNativeLibOutput = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libqemu.so")
val qemuImgNativeLibOutput = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libqemu_img.so")
val uefiFirmwareAssetOutput = layout.projectDirectory.file("src/main/assets/firmware/QEMU_EFI.fd")
val qemuVirtioRomAssetOutput = layout.projectDirectory.file("src/main/assets/qemu/efi-virtio.rom")
val qemuKeymapsAssetDir = layout.projectDirectory.dir("src/main/assets/qemu/keymaps")

tasks.register("prepareProotNativeLibs") {
    val packagesUrl =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
    outputs.files(prootNativeLibOutput, prootLoaderNativeLibOutput)

    onlyIf {
        !prootNativeLibOutput.asFile.exists() || prootNativeLibOutput.asFile.length() == 0L ||
            !prootLoaderNativeLibOutput.asFile.exists() || prootLoaderNativeLibOutput.asFile.length() == 0L
    }

    doLast {
        val workDir = temporaryDir.resolve("termux-proot").apply { mkdirs() }
        val packagesFile = workDir.resolve("Packages")
        URI(packagesUrl).toURL().openStream().use { input ->
            packagesFile.outputStream().use { output -> input.copyTo(output) }
        }

        val packagesText = packagesFile.readText()
        val debPath = packagesText.split("\n\n")
            .first { it.startsWith("Package: proot\n") }
            .lines()
            .first { it.startsWith("Filename: ") }
            .removePrefix("Filename: ")
            .trim()
        val debUrl = "https://packages.termux.dev/apt/termux-main/$debPath"
        val debFile = workDir.resolve("proot.deb")
        URI(debUrl).toURL().openStream().use { input ->
            debFile.outputStream().use { output -> input.copyTo(output) }
        }

        val extractDir = workDir.resolve("extract").apply { mkdirs() }
        providers.exec {
            workingDir = workDir
            commandLine("bash", "-lc", """
                set -euo pipefail
                cd '${extractDir.absolutePath}'
                ar x '${debFile.absolutePath}' data.tar.xz
                tar -xJf data.tar.xz \
                  ./data/data/com.termux/files/usr/bin/proot \
                  ./data/data/com.termux/files/usr/libexec/proot/loader
            """.trimIndent())
        }.result.get()

        val prootBin = extractDir.resolve("data/data/com.termux/files/usr/bin/proot")
        val loaderBin = extractDir.resolve("data/data/com.termux/files/usr/libexec/proot/loader")

        prootNativeLibOutput.asFile.parentFile.mkdirs()
        prootBin.copyTo(prootNativeLibOutput.asFile, overwrite = true)
        loaderBin.copyTo(prootLoaderNativeLibOutput.asFile, overwrite = true)
    }
}

tasks.register("prepareQemuNativeLibs") {
    val packagesUrl =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
    outputs.files(qemuNativeLibOutput, qemuImgNativeLibOutput)

    onlyIf {
        !qemuNativeLibOutput.asFile.exists() || qemuNativeLibOutput.asFile.length() == 0L ||
            !qemuImgNativeLibOutput.asFile.exists() || qemuImgNativeLibOutput.asFile.length() == 0L
    }

    doLast {
        val workDir = temporaryDir.resolve("termux-qemu").apply { mkdirs() }
        val packagesFile = workDir.resolve("Packages")
        URI(packagesUrl).toURL().openStream().use { input ->
            packagesFile.outputStream().use { output -> input.copyTo(output) }
        }

        fun debUrlFor(packageName: String): String {
            val debPath = packagesFile.readText().split("\n\n")
                .first { it.startsWith("Package: $packageName\n") }
                .lines()
                .first { it.startsWith("Filename: ") }
                .removePrefix("Filename: ")
                .trim()
            return "https://packages.termux.dev/apt/termux-main/$debPath"
        }

        val extractDir = workDir.resolve("extract").apply { mkdirs() }
        listOf(
            "qemu-system-aarch64-headless" to "usr/bin/qemu-system-aarch64",
            "qemu-utils" to "usr/bin/qemu-img",
        ).forEach { (packageName, binaryPath) ->
            val debFile = workDir.resolve("$packageName.deb")
            URI(debUrlFor(packageName)).toURL().openStream().use { input ->
                debFile.outputStream().use { output -> input.copyTo(output) }
            }
            providers.exec {
                workingDir = workDir
                commandLine("bash", "-lc", """
                    set -euo pipefail
                    cd '${extractDir.absolutePath}'
                    ar x '${debFile.absolutePath}' data.tar.xz
                    tar -xJf data.tar.xz \
                      ./data/data/com.termux/files/$binaryPath
                """.trimIndent())
            }.result.get()
        }

        val qemuBin = extractDir.resolve("data/data/com.termux/files/usr/bin/qemu-system-aarch64")
        val qemuImgBin = extractDir.resolve("data/data/com.termux/files/usr/bin/qemu-img")

        qemuNativeLibOutput.asFile.parentFile.mkdirs()
        qemuBin.copyTo(qemuNativeLibOutput.asFile, overwrite = true)
        qemuImgBin.copyTo(qemuImgNativeLibOutput.asFile, overwrite = true)
    }
}

tasks.register("prepareQemuRoms") {
    val packagesUrl =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
    outputs.file(qemuVirtioRomAssetOutput)

    onlyIf {
        !qemuVirtioRomAssetOutput.asFile.exists() || qemuVirtioRomAssetOutput.asFile.length() == 0L
    }

    doLast {
        val workDir = temporaryDir.resolve("termux-qemu-roms").apply { mkdirs() }
        val packagesFile = workDir.resolve("Packages")
        URI(packagesUrl).toURL().openStream().use { input ->
            packagesFile.outputStream().use { output -> input.copyTo(output) }
        }

        val debPath = packagesFile.readText().split("\n\n")
            .first { it.startsWith("Package: qemu-common\n") }
            .lines()
            .first { it.startsWith("Filename: ") }
            .removePrefix("Filename: ")
            .trim()
        val debUrl = "https://packages.termux.dev/apt/termux-main/$debPath"
        val debFile = workDir.resolve("qemu-common.deb")
        URI(debUrl).toURL().openStream().use { input ->
            debFile.outputStream().use { output -> input.copyTo(output) }
        }

        val extractDir = workDir.resolve("extract").apply { mkdirs() }
        providers.exec {
            workingDir = workDir
            commandLine("bash", "-lc", """
                set -euo pipefail
                cd '${extractDir.absolutePath}'
                ar x '${debFile.absolutePath}' data.tar.xz
                tar -xJf data.tar.xz \
                  ./data/data/com.termux/files/usr/share/qemu/efi-virtio.rom
            """.trimIndent())
        }.result.get()

        val rom = extractDir.resolve("data/data/com.termux/files/usr/share/qemu/efi-virtio.rom")
        qemuVirtioRomAssetOutput.asFile.parentFile.mkdirs()
        rom.copyTo(qemuVirtioRomAssetOutput.asFile, overwrite = true)
    }
}

tasks.register("prepareQemuKeymaps") {
    val packagesUrl =
        "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
    outputs.dir(qemuKeymapsAssetDir)

    onlyIf {
        val keymapsDir = qemuKeymapsAssetDir.asFile
        !File(keymapsDir, "en-us").exists() || File(keymapsDir, "en-us").length() == 0L
    }

    doLast {
        val workDir = temporaryDir.resolve("termux-qemu-keymaps").apply { mkdirs() }
        val packagesFile = workDir.resolve("Packages")
        URI(packagesUrl).toURL().openStream().use { input ->
            packagesFile.outputStream().use { output -> input.copyTo(output) }
        }

        val debPath = packagesFile.readText().split("\n\n")
            .first { it.startsWith("Package: qemu-common\n") }
            .lines()
            .first { it.startsWith("Filename: ") }
            .removePrefix("Filename: ")
            .trim()
        val debUrl = "https://packages.termux.dev/apt/termux-main/$debPath"
        val debFile = workDir.resolve("qemu-common.deb")
        URI(debUrl).toURL().openStream().use { input ->
            debFile.outputStream().use { output -> input.copyTo(output) }
        }

        val extractDir = workDir.resolve("extract").apply { mkdirs() }
        providers.exec {
            workingDir = workDir
            commandLine("bash", "-lc", """
                set -euo pipefail
                cd '${extractDir.absolutePath}'
                ar x '${debFile.absolutePath}' data.tar.xz
                tar -xJf data.tar.xz \
                  ./data/data/com.termux/files/usr/share/qemu/keymaps
            """.trimIndent())
        }.result.get()

        val sourceKeymapsDir =
            extractDir.resolve("data/data/com.termux/files/usr/share/qemu/keymaps")
        val targetKeymapsDir = qemuKeymapsAssetDir.asFile.apply { mkdirs() }
        sourceKeymapsDir.walkTopDown().forEach { source ->
            val relativePath = source.relativeTo(sourceKeymapsDir).path
            val target = if (relativePath.isEmpty()) targetKeymapsDir else File(targetKeymapsDir, relativePath)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }
}

tasks.register("prepareUefiFirmware") {
    val debUrl =
        "http://ftp.debian.org/debian/pool/main/e/edk2/qemu-efi-aarch64_2022.11-6+deb12u2_all.deb"
    outputs.file(uefiFirmwareAssetOutput)

    onlyIf {
        !uefiFirmwareAssetOutput.asFile.exists() || uefiFirmwareAssetOutput.asFile.length() == 0L
    }

    doLast {
        val workDir = temporaryDir.resolve("uefi-firmware").apply { mkdirs() }
        val debFile = workDir.resolve("qemu-efi-aarch64.deb")
        URI(debUrl).toURL().openStream().use { input ->
            debFile.outputStream().use { output -> input.copyTo(output) }
        }

        val extractDir = workDir.resolve("extract").apply { mkdirs() }
        providers.exec {
            workingDir = workDir
            commandLine("bash", "-lc", """
                set -euo pipefail
                cd '${extractDir.absolutePath}'
                ar x '${debFile.absolutePath}' data.tar.xz
                tar -xJf data.tar.xz ./usr/share/qemu-efi-aarch64/QEMU_EFI.fd
            """.trimIndent())
        }.result.get()

        val firmware = extractDir.resolve("usr/share/qemu-efi-aarch64/QEMU_EFI.fd")
        uefiFirmwareAssetOutput.asFile.parentFile.mkdirs()
        firmware.copyTo(uefiFirmwareAssetOutput.asFile, overwrite = true)
    }
}

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
    dependsOn(
        "prepareAlpineBusybox",
        "prepareProotNativeLibs",
        "prepareQemuNativeLibs",
        "prepareQemuRoms",
        "prepareQemuKeymaps",
        "prepareUefiFirmware",
    )
}

val apkExportDir = rootProject.layout.buildDirectory.dir("dist")

fun registerApkExportTask(
    taskName: String,
    assembleTaskName: String,
    buildType: String,
    sourceApkPath: String,
) {
    tasks.register(taskName, Copy::class.java) {
        group = "build"
        description = "Copy $buildType APK to build/dist/ with a versioned filename"
        dependsOn(assembleTaskName)
        from(layout.buildDirectory.file(sourceApkPath))
        into(apkExportDir)
        rename { "windows11-mobile-${android.defaultConfig.versionName}-$buildType.apk" }
        doLast {
            val exported = apkExportDir.get().file(
                "windows11-mobile-${android.defaultConfig.versionName}-$buildType.apk",
            ).asFile
            logger.lifecycle("Exported APK: ${exported.absolutePath}")
        }
    }
}

registerApkExportTask(
    taskName = "exportDebugApk",
    assembleTaskName = "assembleDebug",
    buildType = "debug",
    sourceApkPath = "outputs/apk/debug/app-debug.apk",
)

registerApkExportTask(
    taskName = "exportReleaseApk",
    assembleTaskName = "assembleRelease",
    buildType = "release",
    sourceApkPath = "outputs/apk/release/app-release.apk",
)

val buildToolsVersion = "34.0.0"

fun registerVerifyApkTask(taskName: String, buildType: String) {
    tasks.register(taskName) {
        group = "verification"
        description = "Verify exported $buildType APK signature with apksigner"
        val apkFile = apkExportDir.map {
            it.file("windows11-mobile-${android.defaultConfig.versionName}-$buildType.apk")
        }
        inputs.file(apkFile)
        dependsOn("export${buildType.replaceFirstChar { it.uppercase() }}Apk")
        doLast {
            val apk = apkFile.get().asFile
            check(apk.isFile && apk.length() > 1_000_000L) {
                "Exported APK missing or too small (${apk.absolutePath}, ${apk.length()} bytes). " +
                    "Do not install zip archives — extract the .apk file first."
            }
            val apksigner = File(android.sdkDirectory, "build-tools/$buildToolsVersion/apksigner")
            exec {
                commandLine(apksigner.absolutePath, "verify", "--verbose", apk.absolutePath)
            }
            logger.lifecycle("Verified APK: ${apk.absolutePath} (${apk.length()} bytes, SHA-256 below)")
            logger.lifecycle(
                MessageDigest.getInstance("SHA-256")
                    .digest(apk.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) },
            )
        }
    }
}

registerVerifyApkTask(taskName = "verifyReleaseApk", buildType = "release")
registerVerifyApkTask(taskName = "verifyDebugApk", buildType = "debug")

tasks.register("buildApk") {
    group = "build"
    description = "Build release APK, export to build/dist/, and verify signature"
    dependsOn("verifyReleaseApk")
}
