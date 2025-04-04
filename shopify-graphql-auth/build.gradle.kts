// import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// val githubProperties = Properties()
// File(rootDir, "github.properties").takeIf { it.exists() }?.inputStream()?.use(githubProperties::load)

android {
    namespace = "com.github.inoles.shopifygraphqlauth"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

/*publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/inoles/shopifyauthgraphql")
            credentials {
                username = githubProperties["gpr.user"] as String? ?: System.getenv("GITHUB_USER")
                password = githubProperties["gpr.key"] as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.inoles.shopifygraphqlauth"
            artifactId = "shopifygraphqlauth"
            version = "1.0.0"
            artifact("${layout.buildDirectory.get()}/outputs/aar/reviewpreference-release.aar")
        }
    }
}*/

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.14"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.14")

}
