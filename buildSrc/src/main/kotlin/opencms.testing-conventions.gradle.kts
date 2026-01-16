
plugins {
    id("java-library")
}

// Shared configuration for all test tasks
tasks.withType<Test>().configureEach {
    maxHeapSize = "2000m"
    useJUnit()
    isScanForTestClasses = false
    testLogging.showStandardStreams = true
    ignoreFailures = true
}



fun Test.applyCoreTestConfig() {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val testSourceSet = sourceSets.getByName("test")
    
    testClassesDirs = testSourceSet.output.classesDirs
    systemProperty("test.data.path", "${project.projectDir}/test/data")
    systemProperty("test.webapp.path", "${project.projectDir}/webapp")
    systemProperty("test.project.path", "${project.projectDir}")
    systemProperty("test.build.folder", testSourceSet.output.resourcesDir!!)
    
    inputs.dir("${project.projectDir}/test/data")
    inputs.dir("${project.projectDir}/webapp")
}

// Configure the base 'test' task
tasks.named<Test>("test") {
    applyCoreTestConfig()
    description = "Runs the complete test suite 'org.opencms.test.AllTests'."
    filter {
        includeTestsMatching("org.opencms.test.AllTests")
    }
}

tasks.register<Test>("testSingle") {
    description = """
        Runs a specified test case. Equivalent to standard 'test' task. 
        NOTE: Usage of legacy '-PtestCaseToRun' property is DEPRECATED. Use standard '--tests' filtering instead. 
        Conflict Resolution: If both '-PtestCaseToRun' and '--tests' are defined, standard '--tests' takes precedence. 
        Default: Runs TestCmsSystemInfo test case if no filters are provided.
    """.trimIndent()
    group = "verification"
    applyCoreTestConfig()

    doFirst {
        val filter = this@register.filter
        logger.info("Test filter configuration - includePatterns: ${filter.includePatterns}")

        var hasExplicit = filter.includePatterns.isNotEmpty()

        // Backward compatibility: Check for -PtestCaseToRun property
        if (project.hasProperty("testCaseToRun")) {
            val legacyTestInfo = project.property("testCaseToRun") as String
            logger.info("Test filter configuration - legacy testCaseToRun: $legacyTestInfo")
            if (hasExplicit) {
                logger.warn("WARNING: Both -PtestCaseToRun and --tests filters are present. Using standard Gradle filters and ignoring legacy property.")
            } else {
                logger.warn("DEPRECATION WARNING: The 'testCaseToRun' property is deprecated. Please use standard Gradle filtering: ./gradlew testSingle --tests $legacyTestInfo")
                filter.includeTestsMatching("$legacyTestInfo*")
                hasExplicit = true
            }
        }

        logger.debug("hasExplicitTestSelection: $hasExplicit")

        if (!hasExplicit) {
            logger.lifecycle("No specific tests requested, applying default filter: org.opencms.main.TestCmsSystemInfo*")
            filter.includeTestsMatching("org.opencms.main.TestCmsSystemInfo*")
        }
    }
}

tasks.register<Jar>("testJar") {
    dependsOn("compileTestJava")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("test").output)
    archiveBaseName.set("opencms-test")
    exclude("**/.gitignore")
    exclude("**/*.java")
}

tasks.register<Javadoc>("javadocTest") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val testSourceSet = sourceSets.getByName("test")
    source = testSourceSet.allJava
    classpath = testSourceSet.compileClasspath
    setDestinationDir(layout.buildDirectory.dir("docs/javadocTest").get().asFile)
}

tasks.register<Jar>("javadocJarTest") {
    dependsOn("javadocTest")
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("docs/javadocTest"))
    archiveBaseName.set("opencms-test")
}

tasks.register<Jar>("sourcesJarTest") {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("test").allSource)
    archiveClassifier.set("sources")
    archiveBaseName.set("opencms-test")
}
