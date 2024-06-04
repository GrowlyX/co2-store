import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "2.0.0-RC3"
    kotlin("plugin.serialization") version "2.0.0-RC3"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "gg.growly.co2"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")
    implementation("de.brudaswen.kotlinx.serialization:kotlinx-serialization-csv:2.0.0")
    implementation("com.fazecast:jSerialComm:[2.0.0,3.0.0)")
    implementation("org.litote.kmongo:kmongo-serialization:4.11.0")
    implementation("org.knowm.xchart:xchart:3.8.0")
    implementation("ch.qos.logback:logback-classic:1.2.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(jdkVersion = 17)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.fork()
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    archiveFileName.set(
        "co2-monitor.jar"
    )
}

application {
    mainClass.set("gg.growly.co2.MainKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.javaParameters = true
    kotlinOptions.jvmTarget = "17"
}

