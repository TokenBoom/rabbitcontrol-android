plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.rabbitcontrol.android"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.rabbitcontrol.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        ndk.abiFilters += "arm64-v8a"
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
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
        include("Cargo.toml", "Cargo.lock", "src/**")
    }, fileTree(rootProject.file("rabbitcontrol-core")) {
        include("Cargo.toml", "crates/**/Cargo.toml", "crates/**/src/**", "crates/**/certs/**")
    })
    outputs.dir(nativeLibs)
    doFirst {
        environment("ANDROID_NDK_HOME", ndkDir.get().asFile.absolutePath)
    }
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "--platform", "29",
        "-o", nativeLibs.get().asFile.absolutePath, "build", "--locked", "--release")
}

android.sourceSets["main"].jniLibs.srcDir(nativeLibs.get().asFile)
tasks.named("preBuild").configure { dependsOn(buildRust) }
