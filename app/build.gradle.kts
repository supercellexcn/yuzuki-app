plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yuzuki.app"
    compileSdk = 35
    // 显式对齐本机已装的 Build Tools，避免 AGP 转去自动安装默认版本 34.0.0
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.yuzuki.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 文档级注入 addDocumentStartJavaScript 需要 androidx.webkit
    // N0：全原生方案已彻底移除 WebView 与 JS 注入，webkit 依赖不再需要
    // TokenStore / 本地偏好：DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 全量 REST 客户端（OkHttp + org.json，无需额外序列化/注解处理器依赖）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // S3：Media3 播放内核与系统媒体会话（版本按方案 §8 锁定）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // S1 白名单策略的 JVM 断言（方案 §10）
    testImplementation("junit:junit:4.13.2")
    // JVM 单测里 android.jar 的 org.json 是 stub，补一个真实实现供模型解析断言使用
    testImplementation("org.json:json:20240303")
}
