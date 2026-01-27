package org.opencms.gradle

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.io.FileInputStream
import java.util.Properties
import java.io.File

/**
 * Hardened Distribution Conventions Plugin for OpenCms.
 * 
 * This plugin handles the final packaging of OpenCms:
 * - 'war': The main web application archive.
 * - 'updater': The upgrade package for existing installations.
 * - 'bindist': The binary distribution containing the WAR and documentation.
 */
class DistributionConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure the base plugin is applied
        project.plugins.apply(OpenCmsBasePlugin::class.java)
        
        val extension = project.extensions.getByType<OpenCmsExtension>()
        
        project.plugins.apply("java-library")

        registerWarTask(project, extension)
        registerUpdaterTask(project, extension)
        registerBinDistTask(project, extension)
    }

    private fun registerWarTask(project: Project, extension: OpenCmsExtension) {
        project.tasks.register<Zip>("war") {
            group = "OpenCms distribution"
            description = "Creates the OpenCms web application archive (WAR)."
            
            dependsOn("setupJar", "resourcesJar", "modulesJar", "allModules")
            if (project.findProject(":extmodules") != null) {
                dependsOn(":extmodules:bindist")
            }

            archiveFileName.set("opencms.war")
            archiveBaseName.set("opencms-webapp")
            archiveExtension.set("war")

            // Base webapp content (excluding sensitive/filtered files)
            from("${project.projectDir}/webapp") {
                exclude("**/setupdata/**/*.jar")
                exclude("**/updatedata/**")
                exclude("**/workplaceThemes/**")
                exclude("**/classes/META-INF/**")
                exclude("**/solr/**")
                exclude("**/spellcheck/**")
                exclude("**/*.html")
                exclude("**/*.properties")
                exclude("**/*.txt")
                exclude("**/*.xml")
            }

            // SOLR config (copy without filtering to avoid corruption)
            from("${project.projectDir}/webapp/WEB-INF/solr") {
                into("/WEB-INF/solr/")
            }

            // Filtered webapp content
            from("${project.projectDir}/webapp") {
                exclude("**/updatedata/**")
                exclude("**/workplaceThemes/**")
                exclude("**/solr/**")
                exclude("**/spellcheck/**")
                exclude("WEB-INF/sun-jaxws.xml")
                include("**/*.html")
                include("**/*.properties")
                include("**/*.txt")
                include("**/*.xml")
                
                val tokens = getBaseTokens(project, extension) + mapOf(
                    "COMMENT_UPDATER_START" to "",
                    "COMMENT_UPDATER_END" to "",
                    "COMMENT_WAR_START" to "<!--",
                    "COMMENT_WAR_END" to "-->"
                )
                filter(ReplaceTokens::class, "tokens" to tokens)
            }

            // Libraries from build directory
            from(project.layout.buildDirectory.dir("libs")) {
                include("*.jar")
                exclude("opencms-gwt*.jar")
                exclude("opencms-test*.jar")
                exclude("*-sources.jar")
                exclude("*-javadoc.jar")
                into("/WEB-INF/lib")
            }

            // Dependencies from configurations
            into("/WEB-INF/lib") { from(project.configurations.getByName("distribution")) }
            into("/WEB-INF/lib") { from(project.configurations.getByName("moduleDeps")) }
            into("/WEB-INF/lib/apis") { from(project.configurations.getByName("usedApis")) }

            // Database drivers
            project.fileTree("webapp/WEB-INF/setupdata/database") { include("**/*.jar") }.forEach { driverJar ->
                from(driverJar.path) { into("/WEB-INF/lib") }
            }

            // Core modules (filtered)
            val modulesDistsDir = project.extensions.extraProperties.get("modulesDistsDir") as File
            val localeModules = project.extensions.extraProperties.get("localeModules") as List<*>
            
            from(modulesDistsDir) {
                into("/WEB-INF/packages/modules")
                localeModules.forEach { localeModule ->
                    exclude("${localeModule}*.zip")
                }
            }

            // JNI libraries
            from("${project.projectDir}/lib/jni") { into("/WEB-INF/lib/jni") }

            // External modules (if present)
            val extModulesProject = project.findProject(":extmodules")
            if (extModulesProject != null) {
                // Note that the module deps do not need to be copied
                // since they are contained in configurations.moduleDeps
                val extModulesDir = extModulesProject.layout.buildDirectory.dir("modules").get().asFile
                from("${extModulesDir}/libs") {
                    include("*.jar")
                    into("/WEB-INF/lib")
                }
                from(extModulesProject.layout.buildDirectory.dir("modulesZip").get().asFile) {
                    into("/WEB-INF/packages/modules")
                }
            }
        }
    }

    private fun registerUpdaterTask(project: Project, extension: OpenCmsExtension) {
        project.tasks.register<Zip>("updater") {
            group = "OpenCms distribution"
            description = "Creates the OpenCms upgrade package."

            dependsOn("setupJar", "resourcesJar", "modulesJar", "allModules")
            if (project.findProject(":extmodules") != null) {
                dependsOn(":extmodules:bindist")
            }
            dependsOn(project.tasks.matching { it.name.startsWith("dist_") })

            archiveBaseName.set("opencms-upgrade-to")

            // adds empty JAR files for all deprecated or updated libs
            val buildProps = Properties()
            val buildDefaultProps = project.file("build-default.properties")
            if (buildDefaultProps.exists()) {
                FileInputStream(buildDefaultProps).use { buildProps.load(it) }
                val jarsToRemove = buildProps.getProperty("updater.jars.remove") ?: ""
                jarsToRemove.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { jar ->
                    from("${project.projectDir}/webapp/WEB-INF/updatedata/empty.jar") {
                        into("/WEB-INF/lib")
                        rename("empty\\.jar", jar)
                    }
                }
            }

            // only process the text files with the ReplaceTokens filter, copy everything else in a second from clause
            from("${project.projectDir}/webapp") {
                include("WEB-INF/updatedata/**/*.ori")
                include("WEB-INF/updatedata/**/*.html")
                include("WEB-INF/updatedata/**/*.xml")
                include("WEB-INF/updatedata/**/*.txt")
                include("WEB-INF/updatedata/**/*.jsp")
                exclude("WEB-INF/updatedata/config/opencms.xml")
                exclude("WEB-INF/updatedata/readme.txt")
                exclude("WEB-INF/updatedata/empty.jar")
                
                val preserveLibModules = project.extensions.extraProperties.get("preserveLibModules") as String
                val tokens = getBaseTokens(project, extension) + mapOf(
                    "PRESERVE_LIB_MODULES" to preserveLibModules
                )
                filter(ReplaceTokens::class, "tokens" to tokens)
            }

            // Static updatedata and metadata
            from("${project.projectDir}/webapp/WEB-INF") {
                into("/WEB-INF")
                include("opencms.tld")
                exclude("web.xml")
                include("cmsshell.sh")
                include("classes/META-INF/persistence.xml")
                include("classes/ehcache.xml")
                include("classes/repository.properties")
                include("sun-jaxws.xml")
                include("spellcheck/**")
                include("git-scripts/**")
            }

            // web.xml with updater-specific comments
            from("${project.projectDir}/webapp/WEB-INF") {
                into("/WEB-INF")
                include("web.xml")
                filter(ReplaceTokens::class, "tokens" to mapOf(
                    "COMMENT_UPDATER_START" to "<!-- ",
                    "COMMENT_UPDATER_END" to "-->",
                    "COMMENT_WAR_START" to "",
                    "COMMENT_WAR_END" to ""
                ))
            }

            // Web resources
            from("${project.projectDir}/webapp/resources") {
                into("/resources")
            }

            // Other updatedata files
            from("${project.projectDir}/webapp") {
                include("WEB-INF/updatedata/**")
                exclude("WEB-INF/updatedata/**/*.ori")
                exclude("WEB-INF/updatedata/**/*.html")
                exclude("WEB-INF/updatedata/**/*.xml")
                exclude("WEB-INF/updatedata/**/*.txt")
                exclude("WEB-INF/updatedata/**/*.jsp")
                exclude("WEB-INF/updatedata/config/opencms.xml")
                exclude("WEB-INF/updatedata/readme.txt")
                exclude("WEB-INF/updatedata/empty.jar")
            }

            from("${project.projectDir}/webapp/WEB-INF/classes/log4j2.xml") {
                into("/WEB-INF/classes")
            }

            from("${project.projectDir}/webapp/WEB-INF/updatedata/config/opencms.xml") {
                into("/WEB-INF/config")
            }

            // Config defaults
            val defaults = listOf(
                "opencms-workplace.xml",
                "opencms-vfs.xml",
                "opencms-importexport.xml",
                "opencms-system.xml",
                "opencms-search.xml"
            )
            defaults.forEach { cfg ->
                from("${project.projectDir}/webapp/WEB-INF/config/$cfg") {
                    into("/WEB-INF/config/defaults")
                }
            }

            // Libraries
            from(project.layout.buildDirectory.dir("libs")) {
                include("*.jar")
                exclude("opencms-test*.jar")
                exclude("opencms-gwt*.jar")
                exclude("*-sources.jar")
                exclude("*-javadoc.jar")
                into("/WEB-INF/lib")
            }

            into("/WEB-INF/lib") {
                from(project.configurations.getByName("distribution"))
            }

            // Module ZIPs
            val modulesDistsDir = project.extensions.extraProperties.get("modulesDistsDir") as File
            val localeModules = project.extensions.extraProperties.get("localeModules") as List<*>
            
            from(modulesDistsDir) {
                into("/WEB-INF/updatedata/modules")
                localeModules.forEach { localeModule ->
                    exclude("${localeModule}*.zip")
                }
                exclude("*mercury*.zip")
            }

            // Database drivers
            project.fileTree("webapp/WEB-INF/setupdata/database") { include("**/*.jar") }.forEach { driverJar ->
                from(driverJar.path) { into("/WEB-INF/lib") }
            }

            // SOLR update config
            from("${project.projectDir}/webapp/WEB-INF/solr") {
                into("/WEB-INF/solr-update/")
            }

            // Readme with version replacement
            from("${project.projectDir}/webapp/WEB-INF/updatedata/readme.txt") {
                into("/")
                filter(ReplaceTokens::class, "tokens" to getBaseTokens(project, extension))
            }
        }
    }

    private fun registerBinDistTask(project: Project, extension: OpenCmsExtension) {
        project.tasks.register<Zip>("bindist") {
            group = "OpenCms distribution"
            description = "Creates the final binary distribution (WAR + Meta-docs)."
            
            dependsOn("war")
            archiveBaseName.set("opencms")
            
            from(project.layout.buildDirectory.file("distributions/opencms.war"))
            from(project.projectDir) {
                include("INSTALL.md")
                include("LICENSE")
                include("README.md")
                include("history.txt")
                filter(ReplaceTokens::class, "tokens" to getBaseTokens(project, extension))
            }
        }
    }

    /**
     * Common tokens used for ReplaceTokens filtering across different tasks.
     */
    private fun getBaseTokens(project: Project, extension: OpenCmsExtension): Map<String, String> {
        return mapOf(
            "OPENCMS_VERSION_NUMBER" to extension.productVersion.get(),
            "OPENCMS_VERSION" to "${extension.productName.get()} ${extension.productVersion.get()}",
            "DRIVERS_VFS" to (project.findProperty("drivers_vfs")?.toString() ?: ""),
            "DRIVERS_PROJECT" to (project.findProperty("drivers_project")?.toString() ?: ""),
            "DRIVERS_USER" to (project.findProperty("drivers_user")?.toString() ?: ""),
            "DRIVERS_HISTORY" to (project.findProperty("drivers_history")?.toString() ?: ""),
            "DRIVERS_CONFIGURATION" to (project.findProperty("drivers_configuration")?.toString() ?: ""),
            "DRIVERS_SUBSCRIPTION" to (project.findProperty("drivers_subscription")?.toString() ?: ""),
            "ADDITIONAL_DB_POOLS" to (project.findProperty("db_additional_pools")?.toString() ?: ""),
            "OPENCMS_CONFIGURATION" to (project.findProperty("opencms_configuration")?.toString() ?: ""),
            "RUNTIME_INFO" to (project.findProperty("system_runtimeinfo")?.toString() ?: "")
        )
    }
}
