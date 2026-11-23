import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Credenciais de assinatura fora do controlo de versões (ver keystore.properties).
// Sem o ficheiro a app compila na mesma; só o release é que sai por assinar.
val segredosAssinatura = Properties().apply {
    val ficheiro = rootProject.file("keystore.properties")
    if (ficheiro.exists()) ficheiro.inputStream().use { load(it) }
}
val podeAssinar = segredosAssinatura.getProperty("storeFile") != null

android {
    namespace = "pt.jpduarte.obragastos"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.jpduarte.obragastos"
        minSdk = 26
        targetSdk = 35
        // Subir a cada APK instalado no telemóvel, para se saber qual está lá.
        versionCode = 2
        versionName = "1.1"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (podeAssinar) {
            create("obra") {
                storeFile = rootProject.file(segredosAssinatura.getProperty("storeFile"))
                storePassword = segredosAssinatura.getProperty("storePassword")
                keyAlias = segredosAssinatura.getProperty("keyAlias")
                keyPassword = segredosAssinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 tira o código e os recursos não usados (sobretudo os milhares
            // de ícones do material-icons-extended): o APK passa de ~11 MB para
            // poucos MB. Nada na app usa reflexão; o Room traz as suas regras.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("obra")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
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

    testOptions {
        // Robolectric precisa dos recursos e dos assets (os esquemas do Room).
        unitTests.isIncludeAndroidResources = true
        // O Robolectric descarrega uma imagem do Android (~170 MB) para o
        // repositório Maven local. Nesta máquina o settings.xml do Maven aponta
        // para "${'$'}{env.USERPROFILE}/...", que ele não expande — e a imagem ia
        // parar a uma pasta com esse nome dentro do projeto. Se as imagens já
        // estiverem em ~/.robolectric, usam-se de lá sem rede.
        val imagens = File(System.getProperty("user.home"), ".robolectric")
        if (imagens.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true) {
            unitTests.all {
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", imagens.path)
            }
        }
    }

    // Os esquemas exportados pelo Room servem de referência aos testes de migração.
    // Entram nos assets da build de depuração (é daí que o Robolectric os lê);
    // a de release não os leva.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    // Cada versão da base de dados deixa aqui o seu esquema, para se poder
    // testar a migração a partir de uma instalação antiga.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
