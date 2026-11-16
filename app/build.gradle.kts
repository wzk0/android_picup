import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself — only the compiler plugins are applied here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The version only changes in deliberate release commits; rebuilding the same
// sources must not dirty tracked files.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val releaseVersionCode = requireNotNull(versionProps.getProperty("code")).toInt()
val releaseVersionName = requireNotNull(versionProps.getProperty("name"))
require(releaseVersionCode > 0 && releaseVersionName.isNotBlank())

// Signing material stays outside Git. Without it release builds are unsigned,
// which is fine for local package inspection; CI supplies the variables.
val releaseStorePath = providers.environmentVariable("PICUP_KEYSTORE_PATH").orNull
fun signingValue(name: String) = requireNotNull(providers.environmentVariable(name).orNull) {
    "Missing release signing environment variable: $name"
}

android {
    namespace = "com.example.picup"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "github.wzk0.picup"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        resValue("string", "picup_release_version", releaseVersionName)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseStorePath != null) {
            create("externalRelease") {
                storeFile = rootProject.file(releaseStorePath)
                storePassword = signingValue("PICUP_KEYSTORE_PASSWORD")
                keyAlias = signingValue("PICUP_KEY_ALIAS")
                keyPassword = signingValue("PICUP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = releaseStorePath?.let { signingConfigs.getByName("externalRelease") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    // The icons-extended artifact ships a lot of classes; keep only the JVM
    // resources we actually need so packaging does not warn.
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}