package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*

/**
 * Hardened UI Conventions Plugin for OpenCms.
 * 
 * This plugin handles Sass compilation for the workplace theme and fonts.
 * It replaces 'opencms.ui-conventions.gradle.kts'.
 */
class UiConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure the base plugin is applied
        project.plugins.apply(OpenCmsBasePlugin::class.java)
        
        val extension = project.extensions.getByType<OpenCmsExtension>()
        
        project.plugins.apply("java-library")

        registerSassTasks(project, extension)
        configureResourcesJarDependency(project)
    }

    /**
     * Registers 'workplaceTheme' and 'opencmsFonts' tasks for Sass compilation.
     */
    private fun registerSassTasks(project: Project, extension: OpenCmsExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        // Sass compiler needs the gwt classpath which contains vaadin-sass and dependencies
        val gwtClasspath = sourceSets.getByName("gwt").compileClasspath

        project.tasks.register<JavaExec>("workplaceTheme") {
            doFirst {
                val outputFile = project.layout.buildDirectory.file("workplaceThemes/opencms/styles.css").get().asFile
                println("======================================================")
                println("Building workplace theme")
                println("Generating ${outputFile.path}")
                println("======================================================")
                
                val dir = outputFile.parentFile
                if (dir.exists()) {
                    project.delete(dir)
                }
                dir.mkdirs()
            }

            mainClass.set("com.vaadin.sass.SassCompiler")
            classpath = gwtClasspath

            args = listOf(
                "${project.projectDir}/webapp/workplaceThemes/VAADIN/themes/opencms/styles.scss",
                project.layout.buildDirectory.file("workplaceThemes/opencms/styles.css").get().asFile.toString()
            )
            maxHeapSize = extension.maxHeapSize.get()
        }

        project.tasks.register<JavaExec>("opencmsFonts") {
            doFirst {
                val outputFile = project.layout.buildDirectory.file("workplaceThemes/opencmsFonts/opencmsFonts.css").get().asFile
                println("======================================================")
                println("Building OpenCms fonts CSS")
                println("Generating ${outputFile.path}")
                println("======================================================")
                
                val dir = outputFile.parentFile
                if (dir.exists()) {
                    project.delete(dir)
                }
                dir.mkdirs()
            }

            mainClass.set("com.vaadin.sass.SassCompiler")
            classpath = gwtClasspath

            args = listOf(
                "${project.projectDir}/webapp/workplaceThemes/VAADIN/themes/opencms/opencmsFonts.scss",
                project.layout.buildDirectory.file("workplaceThemes/opencmsFonts/opencmsFonts.css").get().asFile.toString()
            )
            maxHeapSize = extension.maxHeapSize.get()
        }
    }

    /**
     * Configures 'resourcesJar' to depend on the Sass compilation tasks.
     */
    private fun configureResourcesJarDependency(project: Project) {
        // Configure 'resourcesJar' to wait for Sass compilation and include its output files
        project.tasks.matching { it.name == "resourcesJar" }.all {
            dependsOn("workplaceTheme", "opencmsFonts")
        }
    }
}
