package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

/**
 * Hardened Testing Conventions Plugin for OpenCms.
 * 
 * Replaces 'opencms.testing-conventions.gradle.kts' with a formal Kotlin class.
 */
class TestingConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure the base plugin is applied, which registers the 'opencms' extension
        project.plugins.apply(OpenCmsBasePlugin::class.java)
        
        val extension = project.extensions.getByType<OpenCmsExtension>()
        
        applyCorePlugins(project)
        configureTestDefaults(project, extension)
        configureStandardTestTask(project)
        registerTestSingleTask(project)
        registerArtifactTasks(project)
    }

    private fun applyCorePlugins(project: Project) {
        project.plugins.apply("java-library")
    }

    private fun configureTestDefaults(project: Project, extension: OpenCmsExtension) {
        project.tasks.withType<Test>().configureEach {
            // Use the centralized value from the extension (allows CLI override via -Pmax_heap_size)
            maxHeapSize = extension.maxHeapSize.get()
            useJUnit()
            isScanForTestClasses = false
            testLogging.showStandardStreams = true
            ignoreFailures = true
        }
    }

    private fun configureStandardTestTask(project: Project) {
        project.tasks.named<Test>("test") {
            applyCoreTestConfig(this)
            description = "Runs the complete test suite 'org.opencms.test.AllTests'."
            filter {
                includeTestsMatching("org.opencms.test.AllTests")
            }
        }
    }

    private fun registerTestSingleTask(project: Project) {
        project.tasks.register<Test>("testSingle") {
            description = """
                Runs a specified test case. Equivalent to standard 'test' task. 
                NOTE: Usage of legacy '-PtestCaseToRun' property is DEPRECATED. Use standard '--tests' filtering instead. 
                Conflict Resolution: If both '-PtestCaseToRun' and '--tests' are defined, standard '--tests' takes precedence. 
                Default: Runs TestCmsSystemInfo test case if no filters are provided.
            """.trimIndent()
            group = "verification"
            applyCoreTestConfig(this)

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
    }

    private fun registerArtifactTasks(project: Project) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val testSourceSet = sourceSets.getByName("test")

        project.tasks.apply {
            register<Jar>("testJar") {
                dependsOn("compileTestJava")
                from(testSourceSet.output)
                archiveBaseName.set("opencms-test")
                exclude("**/.gitignore")
                exclude("**/*.java")
            }

            register<Javadoc>("javadocTest") {
                dependsOn("jar")
                source = testSourceSet.allJava
                classpath = testSourceSet.compileClasspath
                setDestinationDir(project.layout.buildDirectory.dir("docs/javadocTest").get().asFile)
            }

            register<Jar>("javadocJarTest") {
                dependsOn("javadocTest")
                archiveClassifier.set("javadoc")
                from(project.layout.buildDirectory.dir("docs/javadocTest"))
                archiveBaseName.set("opencms-test")
            }

            register<Jar>("sourcesJarTest") {
                from(testSourceSet.allSource)
                archiveClassifier.set("sources")
                archiveBaseName.set("opencms-test")
            }
        }
    }

    private fun applyCoreTestConfig(testTask: Test) {
        val project = testTask.project
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val testSourceSet = sourceSets.getByName("test")
        
        testTask.apply {
            testClassesDirs = testSourceSet.output.classesDirs
            systemProperty("test.data.path", "${project.projectDir}/test/data")
            systemProperty("test.webapp.path", "${project.projectDir}/webapp")
            systemProperty("test.project.path", "${project.projectDir}")
            systemProperty("test.build.folder", testSourceSet.output.resourcesDir!!)
            
            inputs.dir("${project.projectDir}/test/data")
            inputs.dir("${project.projectDir}/webapp")
        }
    }
}
