import java.time.Instant
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun signingProp(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

fun runGitInCore(vararg args: String): String? {
    val coreDir = rootProject.file("rabbitcontrol-core")
    if (!coreDir.isDirectory) return null
    return try {
        val proc = ProcessBuilder(listOf("git", "-C", coreDir.absolutePath) + args)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && out.isNotBlank()) out else null
    } catch (_: Exception) {
        null
    }
}

fun readPublicKeyB64(): String {
    val override = (project.findProperty("coreSigningPublicKey") as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv("CORE_SIGNING_PUBLIC_KEY_B64")?.takeIf { it.isNotBlank() }
    if (override != null) {
        return override
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")
    }
    val pemFile = project.file("core-signing-public.pem")
    if (!pemFile.isFile) return ""
    return pemFile.readText()
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace(Regex("\\s+"), "")
}

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

val coreAbiVersion = 2
val bundledCoreVersion = ((project.findProperty("coreVersion") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("RC_CORE_VERSION")?.takeIf { it.isNotBlank() }
    ?: runGitInCore("describe", "--tags", "--abbrev=0")
    ?: "0.2.7")
    .trim()
    .removePrefix("v")
    .removePrefix("V")
val bundledCoreGitSha = (project.findProperty("coreGitSha") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("RC_CORE_GIT_SHA")?.takeIf { it.isNotBlank() }
    ?: runGitInCore("rev-parse", "--short", "HEAD")
    ?: "unknown"
val explicitCoreVersion = ((project.findProperty("coreVersion") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("RC_CORE_VERSION")?.takeIf { it.isNotBlank() })
    ?.trim()
    ?.removePrefix("v")
    ?.removePrefix("V")
val coreSigningPublicKeyB64 = readPublicKeyB64()

android {
    namespace = "io.rabbitcontrol.android"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.rabbitcontrol.android"
        minSdk = 29
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 6
        versionName = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.2.4"
        ndk.abiFilters += "arm64-v8a"
        buildConfigField("int", "CORE_ABI_VERSION", coreAbiVersion.toString())
        buildConfigField("String", "BUNDLED_CORE_VERSION", "\"${escapeBuildConfigString(bundledCoreVersion)}\"")
        buildConfigField("String", "BUNDLED_CORE_GIT_SHA", "\"${escapeBuildConfigString(bundledCoreGitSha)}\"")
        buildConfigField("String", "CORE_SIGNING_PUBLIC_KEY_B64", "\"${escapeBuildConfigString(coreSigningPublicKeyB64)}\"")
    }

    signingConfigs {
        create("release") {
            val path = signingProp("RELEASE_STORE_FILE", "storeFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = signingProp("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = signingProp("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingProp("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
    }

    androidResources {
        localeFilters += listOf("en", "zh", "zh-rCN", "zh-rTW", "zh-rHK", "b+zh+Hant")
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/**/LICENSE*",
                "/META-INF/**/NOTICE*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("io.insert-koin:koin-android:4.0.4")
    implementation("io.insert-koin:koin-androidx-compose:4.0.4")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.json:json:20240303")
}

// Cargo resolves the checked-out core using local path dependencies. Both APK
// variants use optimized native code so decoder/input measurements are comparable.
val nativeLibs = layout.buildDirectory.dir("generated/rustJniLibs")
val ndkDir = androidComponents.sdkComponents.ndkDirectory
val buildRust by tasks.registering(Exec::class) {
    workingDir(rootProject.file("native"))
    inputs.files(fileTree(rootProject.file("native")) {
        include("Cargo.toml", "Cargo.lock", "build.rs", ".cargo/config.toml", "src/**")
    }, fileTree(rootProject.file("rabbitcontrol-core")) {
        include("Cargo.toml", "crates/**/Cargo.toml", "crates/**/src/**", "crates/**/certs/**")
    })
    inputs.property("coreAbiVersion", coreAbiVersion)
    inputs.property("bundledCoreVersion", bundledCoreVersion)
    inputs.property("bundledCoreGitSha", bundledCoreGitSha)
    outputs.dir(nativeLibs)
    doFirst {
        environment("ANDROID_NDK_HOME", ndkDir.get().asFile.absolutePath)
        if (!explicitCoreVersion.isNullOrBlank()) {
            environment("RC_CORE_VERSION", explicitCoreVersion)
        }
        val sourceDateEpoch = System.getenv("SOURCE_DATE_EPOCH")?.takeIf { it.isNotBlank() }
            ?: (Instant.now().epochSecond.toString())
        environment("SOURCE_DATE_EPOCH", sourceDateEpoch)
    }
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "--platform", "29",
        "-o", nativeLibs.get().asFile.absolutePath, "build", "--locked", "--release")
}

android.sourceSets["main"].jniLibs.srcDir(nativeLibs.get().asFile)
tasks.named("preBuild").configure { dependsOn(buildRust) }
