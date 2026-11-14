import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun secret(envName: String, propertyName: String): String? {
    return System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}

val releaseStoreFile = secret("MIRA_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = secret("MIRA_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("MIRA_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("MIRA_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it != null }

if (!hasReleaseSigning) {
    logger.warn(
        "Release signing is not configured. Release artifacts will be " +
            "unsigned. Set MIRA_KEYSTORE_FILE, MIRA_KEYSTORE_PASSWORD, " +
            "MIRA_KEY_ALIAS, and MIRA_KEY_PASSWORD, or create an " +
            "untracked android/keystore.properties."
    )
}

val releaseApiBaseUrl = providers.gradleProperty("miraApiBaseUrl")
    .orElse(providers.environmentVariable("MIRA_API_BASE_URL"))
    .orElse("https://api.example.com/")
    .get()

val debugApiBaseUrl = providers.gradleProperty("miraDebugApiBaseUrl")
    .orElse(providers.environmentVariable("MIRA_DEBUG_API_BASE_URL"))
    .orElse("http://10.0.2.2:8080/")
    .get()

val appVersionCode = providers.gradleProperty("miraVersionCode")
    .orElse(providers.environmentVariable("MIRA_VERSION_CODE"))
    .orElse("1")
    .get()
    .toInt()

val appVersionName = providers.gradleProperty("miraVersionName")
    .orElse(providers.environmentVariable("MIRA_VERSION_NAME"))
    .orElse("1.0")
    .get()

fun quoted(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.mira.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mira.app"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "API_BASE_URL",
                quoted(debugApiBaseUrl)
            )
        }

        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                quoted(releaseApiBaseUrl)
            )

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }

            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.security.crypto)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
