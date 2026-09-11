plugins {
    java
    checkstyle
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.2"
}

group = "su.twomc.staffwork"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:26.7.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("me.clip:placeholderapi:2.11.6")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped") }
}

spotless {
    java {
        palantirJavaFormat("2.75.0")
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint("1.7.1")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.md", ".gitignore", "*.yml", "*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "11.0.1"
    configFile = file("config/checkstyle/checkstyle.xml")
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName.set("TMCStaffWork")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "su.twomc.staffwork.libs.hikari")
    relocate("org.sqlite", "su.twomc.staffwork.libs.sqlite")
    relocate("org.h2", "su.twomc.staffwork.libs.h2")
    relocate("com.mysql", "su.twomc.staffwork.libs.mysql")
    relocate("com.google.gson", "su.twomc.staffwork.libs.gson")
    relocate("net.kyori.adventure", "su.twomc.staffwork.libs.adventure")
    mergeServiceFiles()
    manifest.attributes["Implementation-Version"] = project.version
}

tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.build { dependsOn(tasks.spotlessCheck) }

tasks.register("verifyJar") {
    group = "verification"
    dependsOn(tasks.shadowJar)
    doLast {
        val jar =
            tasks.shadowJar
                .get()
                .archiveFile
                .get()
                .asFile
        check(jar.exists() && jar.length() > 100_000) { "Итоговый JAR не создан или подозрительно мал" }
    }
}

tasks.check { dependsOn("verifyJar") }
