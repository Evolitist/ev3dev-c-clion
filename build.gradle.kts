plugins {
    java
    id("org.jetbrains.intellij") version "0.4.10"
    id("org.jetbrains.kotlin.jvm") version "1.3.50"
}

group = "com.evolitist"
version = "0.8.2"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testCompile(group="junit", name="junit", version="4.12")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

intellij {
    version = "LATEST-EAP-SNAPSHOT"
    type = "CL"
}

tasks {
    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    named<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
        changeNotes("""
            <b>0.8.2</b>
                <ul>
                    <li>Adapt for CLion 2019.2</li>
                </ul>
            <b>0.8.1</b>
                <ul>
                    <li>Adapt for CLion 2019.1</li>
                    <li>Make ev3dev C++ projects use static libstdc++ for compatibility purposes</li>
                    <li>Stabilize connection manager by adding a pre-defined tethering address</li>
                    <li>Allow installing ev3dev-C library from local file</li>
                </ul>
            <b>0.8</b>
                <ul>
                    <li>Adapt for CLion 2018.3</li>
                    <li>Implement ev3dev C++ project support</li>
                    <li>Rework connected device indicator & connection manager</li>
                </ul>
            <b>0.7.2</b>
                <ul>
                    <li>Improve connection indicator stability</li>
                    <li>Support MinGW toolchain on Windows</li>
                </ul>
            <b>0.7.1</b>
                <ul>
                    <li>Added separate "Deploy" button</li>
                    <li>Added run configuration allowing for program execution on ev3dev</li>
                    <li>Suppressed "Endless loop" warnings in ev3dev-C projects</li>
                    <li>Added automatic library updater</li>
                    <li>Added device connection indicator</li>
                    <li>Added basic ev3dev file manager tool window</li>
                </ul>
            <b>0.4.1</b>
                <ul>
                    <li>Added ability to link executable to ev3dev lib statically</li>
                </ul>
            <b>0.4</b>
                <ul>
                    <li>Updated to support CLion 2018.2</li>
                    <li>Improved library installation progress indication</li>
                </ul>
            <b>0.3</b>
                <ul>
                    <li>Added action for deploying library to the EV3 brick</li>
                </ul>
            <b>0.2</b>
                <ul>
                    <li>Added action for host library download and installation</li>
                </ul>
            <b>0.1</b>
                <ul>
                    <li>Initial release</li>
                </ul>
        """.trimIndent())
        pluginDescription("""
            <a href="https://github.com/Evolitist/ev3dev-c-clion">GitHub</a> |
            <a href="https://github.com/Evolitist/ev3dev-c-clion/issues">Issues</a>
            <br>
            <br>
            This plugin allows programming <a href="https://www.ev3dev.org">ev3dev</a> devices.
            <br>
            <br>
            This plugin provides support for project creation & execution with
            <a href="https://github.com/Evolitist/ev3dev-c">the ev3dev C library</a>
            as well as a few tools for configuring ev3dev devices.
            <br>
            <br>
            More instruments for interacting with ev3dev devices wil be added before v1.0.
        """.trimIndent())
        sinceBuild("191")
    }
}
