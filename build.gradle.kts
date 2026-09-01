// AGP 9 traz Kotlin embutido (built-in Kotlin) e já depende do KGP.
// O classpath abaixo eleva KGP/KSP para as versões do version catalog —
// é a forma suportada de subir de versão, já que o plugin kotlin-android
// não é mais aplicado nos módulos.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
