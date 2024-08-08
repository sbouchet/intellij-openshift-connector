import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.testLogger) // Nice test logs
    id("jacoco") // Code coverage
    alias(libs.plugins.sonarqube) // SonarQube
}

group = "org.jboss.tools.intellij"
version = providers.gradleProperty("projectVersion").get() // Plugin version
val ideaVersion = providers.gradleProperty("platformVersion").get()

// Set the JVM language level used to build the project.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenLocal()
    maven { url = uri("https://repository.jboss.org") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaUltimate, ideaVersion)

        // Bundled Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // for local plugin -> https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-add-a-dependency-on-a-plugin-available-in-the-file-system
        //plugins.set(listOf(file("/path/to/plugin/")))

        pluginVerifier()

        instrumentationTools()

        testFramework(TestFrameworkType.Platform)
    }

    implementation(libs.openshift.client) {
        //exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.devtools.common)
    implementation(libs.keycloak)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)
    implementation(libs.converter.jackson)

    // for unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.easytesting)
    testImplementation(libs.mockserver.client)
    testImplementation(libs.mockserver.netty)

    constraints {
        implementation("io.undertow:undertow-core:2.3.15.Final") { // keycloak
            because("https://security.snyk.io/vuln/SNYK-JAVA-IOUNDERTOW-6567186")
        }
        implementation("org.bouncycastle:bcprov-jdk18on:1.78.1") { // keycloak
            because("https://security.snyk.io/vuln/SNYK-JAVA-ORGBOUNCYCASTLE-6612984")
        }
        implementation("com.squareup.okhttp3:okhttp:4.12.0") { // converter.jackson/retrofit
            because("https://security.snyk.io/vuln/SNYK-JAVA-COMSQUAREUPOKHTTP3-2958044")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    publishing {
        token = providers.gradleProperty("jetBrainsToken")
        channels = providers.gradleProperty("jetBrainsChannel").map { listOf(it) }
    }

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaUltimate, ideaVersion)
        }
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId,TemplateWordInPluginName"
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    runIde {
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "debug")
        //systemProperty("jboss.sandbox.api.endpoint", "http://localhost:3000") // enable when running sandbox locally, see below
    }

    test {
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "disabled")
        jvmArgs("-Djava.awt.headless=true")
    }

    withType<Test> {
        configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    jacocoTestReport {
        executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/*.exec"))
        classDirectories.setFrom(instrumentCode)
        reports {
            xml.required = true
        }
    }

    jacocoTestCoverageVerification {
        classDirectories.setFrom(instrumentCode)
    }

    sonar {
        properties {
            property("sonar.projectKey", "redhat-developer_intellij-openshift-connector")
            property("sonar.organization", "redhat-developer")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.junit.reportsPath", layout.buildDirectory.dir("test-results").get().asFile.absolutePath)
            property("sonar.gradle.skipCompile", "true")
        }
    }

    register("runSandbox", JavaExec::class.java) {
        group = "Execution"
        description = "Run the Sandbox registration server in port 3000"
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("org.jboss.tools.intellij.openshift.ui.sandbox.SandboxRegistrationServerMock")
    }

    register("copyKey", Copy::class.java) {
        from("idea_license_token/idea.key")
        into("build/idea-sandbox/config-uiTest")
    }

    register("list") {
        val classpath = sourceSets["integrationTest"].runtimeClasspath
        doLast {
            println(classpath.files)
        }
    }
}

sourceSets {
    create("integrationTest") {
        java.srcDir("src/it/java")
        resources.srcDir("src/it/resources")
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.test.get().compileClasspath + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

val integrationTest by intellijPlatformTesting.testIde.registering {
    task {
        systemProperty("com.redhat.devtools.intellij.telemetry.mode", "disabled")
        description = "Runs the integration tests."
        group = "verification"
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        //outputs.upToDateWhen { false }
        testlogger {
            showStandardStreams = true
            showPassedStandardStreams = false
            showSkippedStandardStreams = false
            showFailedStandardStreams = true
            showFullStackTraces = true
        }
        jvmArgs("-Djava.awt.headless=true")
        mustRunAfter(tasks["test"])
    }

    plugins {
        robotServerPlugin()
    }
    dependencies {
        testImplementation(libs.junit.platform.launcher)
        testImplementation(libs.junit.platform.suite)
        testImplementation(libs.junit.jupiter)
        testImplementation(libs.junit.jupiter.api)
        testImplementation(libs.junit.jupiter.engine)
        testImplementation("com.redhat.devtools.intellij:intellij-common:1.9.6-SNAPSHOT:test")
        testImplementation(libs.devtools.common.ui.test)
        testImplementation(libs.awaitility)
    }
}
/*
configurations {
    integrationTestImplementation.extendsFrom testImplementation
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}

*/

/*
tasks.register('clusterIntegrationUITest', Test) {
    dependsOn copyKey
    useJUnitPlatform {
        includeTags 'ui-test'
    }
    systemProperties['com.redhat.devtools.intellij.telemetry.mode'] = 'disabled'
    systemProperties['CLUSTER_ALREADY_LOGGED_IN'] = System.getenv('CLUSTER_ALREADY_LOGGED_IN') ?: 'false'
    description = 'Runs the cluster integration UI tests.'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    outputs.upToDateWhen { true }
    testlogger {
        showStandardStreams true
        showPassedStandardStreams false
        showSkippedStandardStreams false
        showFailedStandardStreams true
        showFullStackTraces true
    }
    jvmArgs "-Djava.awt.headless=false" // use of clipboard in AboutPublicTest, set to false
    jacoco {
        includeNoLocationClasses = true
        excludes = ["jdk.internal.*"]
    } */
//    include '**/ClusterTestsSuite.class'
//}
/*
tasks.register('publicIntegrationUITest', Test) {
    dependsOn copyKey
    useJUnitPlatform {
        includeTags 'ui-test'
    }
    systemProperties['com.redhat.devtools.intellij.telemetry.mode'] = 'disabled'
    description = 'Runs the public integration UI tests.'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    outputs.upToDateWhen { true }
    testlogger {
        showStandardStreams true
        showPassedStandardStreams false
        showSkippedStandardStreams false
        showFailedStandardStreams true
        showFullStackTraces true
    }
    jvmArgs "-Djava.awt.headless=false" // use of clipboard in AboutPublicTest, set to false
    jacoco {
        includeNoLocationClasses = true
        excludes = ["jdk.internal.*"]
    } */
//    include '**/PublicTestsSuite.class'
/*
}

*/

