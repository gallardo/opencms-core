package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import java.io.FileInputStream
import java.util.Properties

/**
 * Hardened GWT Conventions Plugin for OpenCms.
 * 
 * This plugin centralizes GWT-related build logic, replacing the legacy 
 * 'opencms.gwt-conventions.gradle.kts' script. It handles:
 * - GWT compilation tasks for each module defined in 'src-gwt/gwt-modules.properties'.
 * - Registration of GWT-specific artifacts (gwtJar, javadocGwt, etc.).
 * - Integration with the 'resourcesJar' task to include compiled GWT assets.
 */
class GwtConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure the base plugin is applied
        project.plugins.apply(OpenCmsBasePlugin::class.java)
        
        val extension = project.extensions.getByType<OpenCmsExtension>()
        
        project.plugins.apply("java-library")

        val gwtModuleNames = resolveGwtModules(project)
        
        // Export for other plugins (legacy support)
        project.extensions.extraProperties.set("gwtModuleNames", gwtModuleNames)

        registerGwtModuleTasks(project, extension, gwtModuleNames)
        registerGwtArtifactTasks(project, extension)
        registerTestGwtTask(project, extension)
    }
    
    /**
     * Resolves the list of GWT modules from the 'src-gwt/gwt-modules.properties' file.
     */
    private fun resolveGwtModules(project: Project): String {
        val gwtProps = Properties()
        val propsFile = project.file("src-gwt/gwt-modules.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { gwtProps.load(it) }
        }
        return gwtProps.getProperty("gwtmodules") ?: ""
    }

    /**
     * Registers a 'gwt_<moduleName>' task for each GWT module.
     * These tasks use 'com.google.gwt.dev.Compiler' to compile GWT code to JS.
     */
    private fun registerGwtModuleTasks(project: Project, extension: OpenCmsExtension, gwtModuleNames: String) {
        val modules = gwtModuleNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        modules.forEach { gwtModule ->
            project.tasks.register<JavaExec>("gwt_$gwtModule") {
                dependsOn("gwtClasses")
                val buildDir = project.layout.buildDirectory.dir("gwt/$gwtModule").get().asFile
                val extraDir = project.layout.buildDirectory.dir("extra/$gwtModule").get().asFile
                
                val sourceSets = project.extensions.getByType<SourceSetContainer>()
                val gwtSourceSet = sourceSets.getByName("gwt")
                val mainSourceSet = sourceSets.getByName("main")
                val modulesSourceSet = sourceSets.getByName("modules")

                inputs.files(gwtSourceSet.java.srcDirs)
                inputs.dir(gwtSourceSet.output.resourcesDir!!)
                outputs.dir(buildDir)

                // Workaround for incremental build issues in early Gradle versions (GRADLE-1483).
                // In Kotlin DSL, outputs.upToDateSpec is read-only and cannot be easily reassigned.
                // We preserve the documentation of the workaround here, but rely on modern Gradle's internal checks.
                // Original Groovy: outputs.upToDateSpec = new org.gradle.api.specs.AndSpec()

                doFirst {
                    println("======================================================")
                    println("Building GWT resources for $gwtModule")
                    println("======================================================")
                    if (buildDir.exists()) {
                        project.delete(buildDir)
                    }
                    buildDir.mkdirs()
                }

                mainClass.set("com.google.gwt.dev.Compiler")

                classpath = project.files(
                    // Java source core
                    mainSourceSet.java.srcDirs,
                    // Java source gwt
                    gwtSourceSet.java.srcDirs,
                    // Java source modules
                    modulesSourceSet.java.srcDirs,
                    // Generated resources
                    gwtSourceSet.output.resourcesDir,
                    // Generated classes
                    gwtSourceSet.java.classesDirectory,
                    // Dependencies
                    gwtSourceSet.compileClasspath
                )

                val isDraft = project.hasProperty("gwtDraft")
                if (isDraft) {
                    println("Using GWT draft mode for module $gwtModule")
                    args = listOf(
                        gwtModule,
                        // Your GWT module
                        "-war", buildDir.toString(),
                        "-logLevel", "ERROR",
                        "-localWorkers", "2",
                        "-style", "obfuscated",
                        "-extra", extraDir.toString(),
                        "-draftCompile",
                        // Speeds up compile with 25%
                        "-setProperty", "locale=en"
                    )
                } else {
                    args = listOf(
                        gwtModule,
                        // Your GWT module
                        "-war", buildDir.toString(),
                        "-logLevel", "ERROR",
                        "-localWorkers", "2",
                        "-style", "obfuscated",
                        "-extra", extraDir.toString(),
                        "-strict"
                    )
                }
                
                jvmArgs("-Dgwt.jjs.permutationWorkerFactory=com.google.gwt.dev.ThreadedPermutationWorkerFactory")
                maxHeapSize = extension.maxHeapSize.get()
            }

            // Configure 'resourcesJar' to wait for GWT compilation and include its output files
            project.tasks.matching { it.name == "resourcesJar" }.all {
                val jarTask = this as Jar
                jarTask.dependsOn("gwt_$gwtModule")
                
                if (gwtModule != "org.opencms.ui.WidgetSet") {
                    jarTask.from(project.layout.buildDirectory.dir("gwt/$gwtModule")) {
                        exclude("**/WEB-INF/**")
                        into("OPENCMS/gwt")
                    }
                } else {
                    jarTask.from(project.layout.buildDirectory.dir("gwt/org.opencms.ui.WidgetSet")) {
                        include("org.opencms.ui.WidgetSet/**")
                        into("VAADIN/widgetsets")
                    }
                }
            }
        }
    }

    /**
     * Registers standard GWT artifact tasks like 'gwtJar', 'javadocGwt', and 'sourcesJarGwt'.
     */
    private fun registerGwtArtifactTasks(project: Project, extension: OpenCmsExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val gwtSourceSet = sourceSets.getByName("gwt")
        val mainSourceSet = sourceSets.getByName("main")

        project.tasks.apply {
            register<Jar>("gwtJar") {
                dependsOn("jar")
                from(gwtSourceSet.output)
                from(gwtSourceSet.java.srcDirs) { include("**/*.java") }
                from(mainSourceSet.java.srcDirs) {
                    include("**/shared/**")
                    include("**/gwt/CmsRpcException.java")
                    include("**/ade/detailpage/CmsDetailPageInfo.java")
                    include("**/db/CmsResourceState.java")
                    include("**/jsp/CmsContainerJsonKeys.java")
                    include("**/util/CmsPair.java")
                    include("**/util/CmsDefaultSet.java")
                    include("**/xml/content/CmsXmlContentProperty.java")
                    include("**/workplace/editors/CmsTinyMceToolbarHelper.java")
                }
                includeEmptyDirs = false
                archiveFileName.set("opencms-gwt.jar")
                archiveBaseName.set("opencms-gwt")
                exclude("**/.gitignore")
            }

            register<Javadoc>("javadocGwt") {
                dependsOn("jar")
                source = gwtSourceSet.allJava
                classpath = gwtSourceSet.compileClasspath
                setDestinationDir(project.layout.buildDirectory.dir("docs/javadocGwt").get().asFile)

                doLast {
                    project.copy {
                        from("${project.projectDir}/doc/javadoc/logos")
                        into(project.layout.buildDirectory.dir("docs/javadocGwt/logos"))
                    }
                }

                (options as StandardJavadocDocletOptions).apply {
                    memberLevel = JavadocMemberLevel.PROTECTED
                    charSet = "UTF-8"
                    isAuthor = true
                    isVersion = true
                    source = extension.javaVersion.get().toString()
                    isLinkSource = false
                    docTitle = "OpenCms GWT Components API, version ${extension.productVersion.get()}"
                    windowTitle = "OpenCms GWT Components API, version ${extension.productVersion.get()}"
                    header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
                    isUse = true
                }
            }

            register<Jar>("javadocJarGwt") {
                dependsOn("javadocGwt")
                archiveClassifier.set("javadoc")
                from(project.layout.buildDirectory.dir("docs/javadocGwt"))
                archiveBaseName.set("opencms-gwt")
            }

            register<Jar>("sourcesJarGwt") {
                from(gwtSourceSet.java.srcDirs)
                from(mainSourceSet.java.srcDirs) {
                    include("**/shared/**")
                    include("**/gwt/CmsRpcException.java")
                    include("**/ade/detailpage/CmsDetailPageInfo.java")
                    include("**/db/CmsResourceState.java")
                    include("**/jsp/CmsContainerJsonKeys.java")
                    include("**/util/CmsPair.java")
                    include("**/util/CmsDefaultSet.java")
                    include("**/xml/content/CmsXmlContentProperty.java")
                    include("**/workplace/editors/CmsTinyMceToolbarHelper.java")
                }
                includeEmptyDirs = false
                archiveClassifier.set("sources")
                archiveBaseName.set("opencms-gwt")
            }
        }
    }

    /**
     * Registers the 'testGwt' task for running GWT unit tests.
     */
    private fun registerTestGwtTask(project: Project, extension: OpenCmsExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val testGwtSourceSet = sourceSets.getByName("testGwt")

        project.tasks.register<Test>("testGwt") {
            dependsOn("compileTestGwtJava")
            
            classpath = testGwtSourceSet.runtimeClasspath
            // Add source directories to classpath as GWT tests often need them
            classpath += project.files(
                "${project.projectDir}/src",
                "${project.projectDir}/src-gwt",
                "${project.projectDir}/test-gwt"
            )

            useJUnit()
            filter {
                includeTestsMatching("org.opencms.client.test.AllTests")
            }
            setScanForTestClasses(false)
            testClassesDirs = project.files(testGwtSourceSet.java.classesDirectory)
            
            systemProperty("gwt.args", "-logLevel WARN -setProperty locale=en")
            systemProperty("java.awt.headless", "true")
            maxHeapSize = extension.maxHeapSize.get()
            
            testLogging.showStandardStreams = true
            ignoreFailures = true
        }
    }
}
