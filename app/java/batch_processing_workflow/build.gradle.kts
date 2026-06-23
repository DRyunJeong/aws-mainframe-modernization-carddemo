plugins {
    java
    application
}

group = "com.carddemo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Target the Java 17 language level (per MIGRATION_STRATEGY) while compiling/
// running on the installed JDK (21). --release 17 guarantees 17 bytecode/APIs
// without provisioning a separate JDK 17.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

application {
    mainClass.set("com.carddemo.batch.Cbact04cMain")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Convenience: regenerate every program's GnuCOBOL golden masters (requires cobc).
// Each migrated program has src/test/cobol/<prog>/oracle.conf; this runs them all.
tasks.register<Exec>("generateGolden") {
    group = "verification"
    description = "Run each original batch program under GnuCOBOL to (re)generate golden masters."
    commandLine("bash", "src/test/cobol/run_all_oracles.sh")
}
