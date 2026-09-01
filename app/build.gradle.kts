import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * versionCode vem do número da run do GitHub Actions, que é monotônico — o app só instala uma
 * atualização com versionCode maior que o instalado, então esse número não pode retroceder nunca.
 * Fora do CI vale 1, suficiente para build local.
 */
val ciVersionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

/** Tag mais recente, ou short SHA. O CI exporta; localmente cai em "dev". */
val ciVersionName: String = System.getenv("BUILD_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "dev"

/**
 * O keystore de release vive fora do repositório. No CI ele é reconstituído a partir do secret
 * KEYSTORE_BASE64; localmente, de um keystore.properties que o .gitignore barra.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun keystoreValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.ederfmatos.batterystats"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ederfmatos.batterystats"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreValue("KEYSTORE_FILE", "storeFile")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = keystoreValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = keystoreValue("KEY_ALIAS", "keyAlias")
                keyPassword = keystoreValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sem keystore configurado a assinatura fica nula e o build de release falha na hora
            // de empacotar — melhor do que publicar um APK assinado com chave de debug, que nunca
            // conseguiria substituir o app instalado.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // O org.json do Android é um stub no classpath de teste do AGP e estoura em qualquer chamada.
    // Esta é a implementação real, só para os testes; em produção vale a do sistema.
    testImplementation(libs.org.json)
}
