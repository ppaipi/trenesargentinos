import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clave de firma fuera del repo (~/Android/trenes-firma.properties). Sin ese
// archivo el release sale sin firmar y solo sirve el debug.
val firma = Properties().apply {
    val f = File(System.getProperty("user.home"), "Android/trenes-firma.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// versionCode = cantidad de commits (Play exige que crezca). Sin git: 1.
val conteoCommits: Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1
} catch (e: Exception) { 1 }

android {
    namespace = "ar.chiqui.trenes"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.chiqui.trenes"
        minSdk = 23 // Android 6: lo que no existe ahí se oculta (ver Build.VERSION en el código)
        targetSdk = 35
        versionCode = conteoCommits
        versionName = "1.0"
    }

    signingConfigs {
        if (!firma.isEmpty) {
            create("subida") {
                storeFile = File(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("subida")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // java.time en Android 6/7
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

// Los JSON de estaciones y recorridos son los mismos del sitio: se copian al
// compilar, así hay una sola fuente de verdad.
abstract class CopiarDatos : DefaultTask() {
    @get:InputFiles abstract val fuentes: ConfigurableFileCollection
    @get:OutputDirectory abstract val salida: DirectoryProperty

    @TaskAction
    fun copiar() {
        fuentes.forEach { it.copyTo(salida.get().file(it.name).asFile, overwrite = true) }
    }
}

val copiarDatos = tasks.register<CopiarDatos>("copiarDatos") {
    fuentes.from(listOf("estaciones.json", "estaciones-coords.json", "recorridos.json").map { rootDir.parentFile.resolve(it) })
}

androidComponents {
    onVariants { variante ->
        variante.sources.assets?.addGeneratedSourceDirectory(copiarDatos, CopiarDatos::salida)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
