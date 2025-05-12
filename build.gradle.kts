plugins {
    java
}

group = "com.ipoint"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val poiVersion = "3.11"
val batikVersion = "1.8"
val freehepGraphicsioVersion = "2.4"

repositories {
    mavenLocal()
    mavenCentral()
    // Локальный репозиторий
    maven {
        name = "project-local"
        url = uri("file:${projectDir}/repo")
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("src/resources"))
    }
    test {
        java.setSrcDirs(listOf("src/test/java"))
        resources.setSrcDirs(listOf("src/test/resources"))
    }
}

dependencies {
    implementation("local:imsglobal:1.0")
    implementation("local:fMath:2.0")
    implementation("local:batik-codec:1.8.0.1")

    implementation("org.apache.xmlgraphics:batik-transcoder:$batikVersion")
    implementation("org.apache.xmlgraphics:batik-anim:$batikVersion")
    implementation("org.apache.xmlgraphics:xmlgraphics-commons:2.1")

    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    implementation("org.apache.poi:ooxml-schemas:1.1")

    implementation("net.arnx:wmf2svg:0.9.8")
    implementation("org.jdom:jdom:1.1")
    implementation("org.htmlparser:htmlparser:2.1")
    implementation("org.freemarker:freemarker:2.3.22")
    implementation("org.apache.commons:commons-lang3:3.4")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.google.code.gson:gson:2.8.2")
    implementation("com.google.collections:google-collections:1.0-rc2")
    implementation("org.freehep:freehep-graphicsio-emf:$freehepGraphicsioVersion")
    implementation("org.freehep:freehep-graphicsio-svg:$freehepGraphicsioVersion")
}
