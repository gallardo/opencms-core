package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.kotlin.dsl.*
import java.io.FileInputStream
import java.io.File
import java.util.Properties

/**
 * Hardened Module Packaging Plugin for OpenCms.
 * 
 * This plugin handles the generation of module-specific JARs and ZIPs,
 * including manifest parsing and localization support.
 */
class ModulePackagingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure the base plugin is applied
        project.plugins.apply(OpenCmsBasePlugin::class.java)
        
        val extension = project.extensions.getByType<OpenCmsExtension>()
        
        project.plugins.apply("java-library")

        val modulesList = project.findProperty("modules_list")?.toString() ?: ""
        val allModuleNames = modulesList.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Export for other plugins (legacy support)
        project.extensions.extraProperties.set("allModuleNames", allModuleNames)

        val modulesDistsDir = project.layout.buildDirectory.dir("modulesZip").get().asFile
        project.extensions.extraProperties.set("modulesDistsDir", modulesDistsDir)

        registerGeneralModuleTasks(project, extension)
        registerPerModuleTasks(project, extension, allModuleNames, modulesDistsDir)

        // Contribute module static resources to the global resourcesJar (if it exists)
        project.tasks.matching { it.name == "resourcesJar" }.configureEach {
            val jarTask = this as Jar
            allModuleNames.forEach { moduleName ->
                jarTask.from("${project.projectDir}/modules/${moduleName}/static") {
                    into("OPENCMS")
                }
            }
        }
    }

    private fun registerGeneralModuleTasks(project: Project, extension: OpenCmsExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val modulesSourceSet = sourceSets.getByName("modules")

        project.tasks.apply {
            register<Jar>("modulesJar") {
                from(modulesSourceSet.output)
                archiveFileName.set("opencms-modules.jar")
                archiveBaseName.set("opencms-modules")
                group = "OpenCms core JARs"
                exclude("**/.gitignore")
            }

            register<Javadoc>("javadocModules") {
                dependsOn("jar")
                
                doLast {
                    project.copy {
                        from("${project.projectDir}/doc/javadoc/logos")
                        into(project.layout.buildDirectory.dir("docs/javadocModules/logos"))
                    }
                }

                (options as StandardJavadocDocletOptions).apply {
                    memberLevel = JavadocMemberLevel.PROTECTED
                    charSet = "UTF-8"
                    isAuthor = true
                    isVersion = true
                    links("false")
                    source = extension.javaVersion.get().toString()
                    docTitle = "OpenCms Workplace Modules API, version ${extension.productVersion.get()}"
                    windowTitle = "OpenCms Workplace Modules API, version ${extension.productVersion.get()}"
                    header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
                }
                source = modulesSourceSet.allJava
                classpath = modulesSourceSet.compileClasspath
                setDestinationDir(project.layout.buildDirectory.dir("docs/javadocModules").get().asFile)
            }

            register<Jar>("javadocJarModules") {
                dependsOn("javadocModules")
                archiveClassifier.set("javadoc")
                from(project.layout.buildDirectory.dir("docs/javadocModules"))
                archiveBaseName.set("opencms-modules")
            }

            register<Jar>("sourcesJarModules") {
                from(modulesSourceSet.java.srcDirs)
                archiveClassifier.set("sources")
                archiveBaseName.set("opencms-modules")
            }
        }
    }

    private fun registerPerModuleTasks(
        project: Project, 
        extension: OpenCmsExtension, 
        allModuleNames: List<String>, 
        modulesDistsDir: File
    ) {
        val localeModules = mutableListOf<String>()
        val preserveLibModules = StringBuilder()
        
        val xmlParser = groovy.util.XmlParser()

        allModuleNames.forEach { moduleName ->
            val moduleFolder = project.file("modules/$moduleName")
            val propertyFile = File(moduleFolder, "module.properties")
            var workplacelocalization: String? = null
            
            if (propertyFile.exists()) {
                val moduleProperties = Properties()
                propertyFile.inputStream().use { moduleProperties.load(it) }
                workplacelocalization = moduleProperties.getProperty("workplacelocalization")
            }

            val manifestFile = project.file("${moduleFolder}/resources/manifest.xml")
            // TODO: Review if moduleDependencies is actually needed (it seems unused in upstream)
            val moduleDependencies = mutableListOf<String>()
            var moduleVersion = extension.productVersion.get()

            if (manifestFile.exists()) {
                try {
                    val parsedManifest = xmlParser.parse(manifestFile)
                    val moduleNode = parsedManifest.children().filterIsInstance<groovy.util.Node>().find { it.name() == "module" }

                    if (moduleNode != null) {
                        val depList = moduleNode.children().filterIsInstance<groovy.util.Node>().find { it.name() == "dependencies" }
                        depList?.children()?.filterIsInstance<groovy.util.Node>()?.forEach { dep ->
                            if (dep.name() == "dependency") {
                                moduleDependencies.add(dep.attribute("name").toString())
                            }
                        }

                        val versionNode = moduleNode.children().filterIsInstance<groovy.util.Node>().find { it.name() == "version" }
                        if (versionNode != null) {
                            moduleVersion = versionNode.text()
                        }
                    }
                } catch (e: Exception) {
                    project.logger.warn("Failed to parse manifest for module $moduleName: ${e.message}")
                }
            }

            if (workplacelocalization != null) {
                localeModules.add(moduleName)
                preserveLibModules.append(moduleName).append(",")
            }

            project.tasks.register<Zip>("dist_$moduleName") {
                group = "OpenCms module ZIPs"
                
                val effectiveVersion = if (project.hasProperty("noVersion")) "" else moduleVersion
                archiveVersion.set(effectiveVersion)
                archiveBaseName.set(moduleName)
                destinationDirectory.set(modulesDistsDir)

                // excluding jars from modules, jars will be placed in the WEB-INF lib folder through the moduleDeps configuration
                from(File(moduleFolder, "resources")) {
                    exclude("**/lib*/*.jar")
                }

                // TODO: set the module version to match the manifest.xml
                doFirst {
                    println("======================================================")
                    println("Building ZIP for $moduleName version $moduleVersion")
                    println("======================================================")
                }
                
                doLast {
                    val copyTarget = project.findProperty("module_copy_target")?.toString()
                    if (!copyTarget.isNullOrEmpty() && project.file(copyTarget).exists()) {
                        println("copying ${archiveFile.get().asFile} to $copyTarget")
                        project.copy {
                            from(archiveFile)
                            into(copyTarget)
                        }
                    }
                }
            }

            if (workplacelocalization != null) {
                val localization = workplacelocalization
                project.tasks.register<Jar>("jar_$moduleName") {
                    group = "OpenCms module JARs"
                    manifest {
                        attributes(
                            "Implementation-Title" to "Alkacon OpenCms",
                            "Implementation-Version" to moduleVersion,
                            "OpenCms-Localization" to localization
                        )
                    }
                    from(File(moduleFolder, "resources/system/workplace/locales/$localization/messages"))
                    archiveFileName.set("$moduleName.jar")
                    archiveBaseName.set(moduleName)
                    exclude("**/.gitignore")
                    
                    doFirst {
                        println("======================================================")
                        println("Building $moduleName.jar including localization $localization")
                        println("======================================================")
                    }

                    doLast {
                        val updateTarget = project.findProperty("tomcat_update_target")?.toString()
                        if (!updateTarget.isNullOrEmpty() && project.file(updateTarget).exists()) {
                            println("copying ${archiveFile.get().asFile} to $updateTarget")
                            project.copy {
                                from(archiveFile)
                                into(updateTarget)
                            }
                        }
                    }
                }

                project.tasks.named<Zip>("dist_$moduleName") {
                    dependsOn("jar_$moduleName")
                }
            }
        }

        // Pass gathered data to project extensions for WAR/Updater tasks
        project.extensions.extraProperties.set("localeModules", localeModules)
        project.extensions.extraProperties.set("preserveLibModules", preserveLibModules.toString())

        project.tasks.register("allModules") {
            dependsOn(project.tasks.matching { it.name.startsWith("dist_") })
            doLast {
                println("======================================================")
                println("Done building modules")
                println("======================================================")
            }
        }
    }
}
