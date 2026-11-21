plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.il2patch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.il2patch"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "3.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Signs repacked APKs (v1 + v2 + v3)
    implementation("com.android.tools.build:apksig:8.5.2")
    // Copies APK entries without recompressing them
    implementation("org.apache.commons:commons-compress:1.26.2")
}

// The app signs patched APKs with a key bundled as an asset. It is generated automatically
// on the first build if it does not exist (needs keytool, which ships with the JDK).
val genKeystore by tasks.registering {
    val ks = layout.projectDirectory.file("src/main/assets/il2patch.p12").asFile
    outputs.file(ks)
    onlyIf { !ks.exists() }
    doLast {
        ks.parentFile.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        project.exec {
            commandLine(
                keytool, "-J-Dkeystore.pkcs12.legacy",
                "-genkeypair", "-alias", "il2patch",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "36500",
                "-storetype", "PKCS12", "-keystore", ks.absolutePath,
                "-storepass", "il2patch", "-keypass", "il2patch",
                "-dname", "CN=Il2Patch"
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(genKeystore)
}
