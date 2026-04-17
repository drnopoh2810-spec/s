import java.util.Properties
  import java.io.FileInputStream

  plugins {
      id("com.android.application")
      id("org.jetbrains.kotlin.android")
      id("com.google.devtools.ksp")
      id("com.google.dagger.hilt.android")
  }

  android {
      namespace  = "com.sms.paymentgateway"
      compileSdk = 34

      defaultConfig {
          applicationId  = "com.sms.paymentgateway"
          minSdk         = 26
          targetSdk      = 34
          versionCode    = 1
          versionName    = "1.0"
          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

          val localProperties = Properties()
          val localPropertiesFile = rootProject.file("local.properties")
          if (localPropertiesFile.exists()) {
              localProperties.load(FileInputStream(localPropertiesFile))
          }
          val relayApiKey: String =
              System.getenv("RELAY_API_KEY")
                  ?: localProperties.getProperty("RELAY_API_KEY")
                  ?: "default_api_key_for_development"

          buildConfigField("String", "RELAY_API_KEY", "\"$relayApiKey\"")
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
          sourceCompatibility = JavaVersion.VERSION_17
          targetCompatibility = JavaVersion.VERSION_17
      }

      kotlinOptions {
          jvmTarget = "17"
      }

      buildFeatures {
          compose    = true
          buildConfig = true
      }

      composeOptions {
          kotlinCompilerExtensionVersion = "1.5.8"
      }

      packaging {
          resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
  }

  dependencies {
      // Core
      implementation("androidx.core:core-ktx:1.12.0")
      implementation("androidx.appcompat:appcompat:1.6.1")
      implementation("com.google.android.material:material:1.11.0")

      // Compose
      val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
      implementation(composeBom)
      implementation("androidx.compose.ui:ui")
      implementation("androidx.compose.material3:material3")
      implementation("androidx.compose.material:material-icons-extended")
      implementation("androidx.compose.ui:ui-tooling-preview")
      implementation("androidx.activity:activity-compose:1.8.2")
      debugImplementation("androidx.compose.ui:ui-tooling")

      // Room - using KSP instead of kapt
      implementation("androidx.room:room-runtime:2.6.1")
      implementation("androidx.room:room-ktx:2.6.1")
      ksp("androidx.room:room-compiler:2.6.1")

      // Retrofit & OkHttp
      implementation("com.squareup.retrofit2:retrofit:2.9.0")
      implementation("com.squareup.retrofit2:converter-gson:2.9.0")
      implementation("com.squareup.okhttp3:okhttp:4.12.0")
      implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

      // Hilt - using KSP instead of kapt
      implementation("com.google.dagger:hilt-android:2.50")
      ksp("com.google.dagger:hilt-compiler:2.50")
      implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

      // Coroutines
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

      // WorkManager
      implementation("androidx.work:work-runtime-ktx:2.9.0")

      // Gson
      implementation("com.google.code.gson:gson:2.10.1")

      // Timber
      implementation("com.jakewharton.timber:timber:5.0.1")

      // NanoHTTPD
      implementation("org.nanohttpd:nanohttpd:2.3.1")
      implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

      // Testing
      testImplementation("junit:junit:4.13.2")
      testImplementation("io.mockk:mockk:1.13.8")
      testImplementation("org.mockito:mockito-core:5.3.1")
      testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
      testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
      testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
      androidTestImplementation("androidx.test.ext:junit:1.1.5")
      androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  }
  