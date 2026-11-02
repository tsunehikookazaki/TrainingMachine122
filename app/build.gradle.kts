plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "net.the_okazakis.trainingmachin" // ★注意: ご自身のパッケージ名に合わせてください
    compileSdk = 37

    defaultConfig {
        applicationId = "net.the_okazakis.trainingmachin" // ★注意: ご自身のパッケージ名に合わせてください
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    dependencies {
        // 1. Android基本機能とCompose（画面作成）のライブラリ（直接指定）
        val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
        implementation(composeBom)
        androidTestImplementation(composeBom)

        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
        implementation("androidx.activity:activity-compose:1.8.2")
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-graphics")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")

        // 2. テスト用ライブラリ
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-tooling")
        debugImplementation("androidx.compose.ui:ui-test-manifest")

        // 3. 今回追加したRoomデータベースと画面遷移
        val room_version = "2.6.1"
        implementation("androidx.room:room-runtime:$room_version")
        implementation("androidx.room:room-ktx:$room_version")
        ksp("androidx.room:room-compiler:$room_version")

        val nav_version = "2.7.7"
        implementation("androidx.datastore:datastore-preferences:1.0.0")
        implementation("androidx.navigation:navigation-compose:$nav_version")
        implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
        implementation("androidx.navigation:navigation-ui-ktx:$nav_version")
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    }
}