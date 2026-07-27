import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gameshift.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gameshift.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val propFile = rootProject.file("local.properties")
            val properties = Properties()
            if (propFile.exists()) {
                propFile.inputStream().use { properties.load(it) }
            }
            
            val storeFileVal = properties.getProperty("signing.storeFile")
            val storePasswordVal = properties.getProperty("signing.storePassword")
            val keyAliasVal = properties.getProperty("signing.keyAlias")
            val keyPasswordVal = properties.getProperty("signing.keyPassword")

            if (storeFileVal != null && storePasswordVal != null && keyAliasVal != null && keyPasswordVal != null) {
                storeFile = file(storeFileVal)
                storePassword = storePasswordVal
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null) {
                output.outputFileName = "GameShift-v${variant.versionName}-${variant.buildType.name}.apk"
            }
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
        compose = true
        buildConfig = true
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
