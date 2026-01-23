
import org.apache.tools.ant.filters.ReplaceTokens
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("java-library")
}

// Access shared properties from other plugins
val localeModules: List<String> by project.extra
val preserveLibModules: String by project.extra
val modulesDistsDir: java.io.File by project.extra

val productName = project.findProperty("product_name")?.toString() ?: "OpenCms"
val productVersion = project.version.toString()

// Database drivers to be filtered
val drivers_vfs = project.findProperty("drivers_vfs")?.toString() ?: ""
val drivers_project = project.findProperty("drivers_project")?.toString() ?: ""
val drivers_user = project.findProperty("drivers_user")?.toString() ?: ""
val drivers_history = project.findProperty("drivers_history")?.toString() ?: ""
val drivers_configuration = project.findProperty("drivers_configuration")?.toString() ?: ""
val drivers_subscription = project.findProperty("drivers_subscription")?.toString() ?: ""
val db_additional_pools = project.findProperty("db_additional_pools")?.toString() ?: ""
val opencms_configuration = project.findProperty("opencms_configuration")?.toString() ?: ""
val system_runtimeinfo = project.findProperty("system_runtimeinfo")?.toString() ?: ""

tasks.register<Zip>("war") {
    dependsOn("setupJar", "resourcesJar", "modulesJar", "allModules")

    // Conditional dependency if :extmodules exists
    if (project.findProject(":extmodules") != null) {
        dependsOn(":extmodules:bindist")
    }

    archiveFileName.set("opencms.war")
    archiveBaseName.set("opencms-webapp")
    archiveExtension.set("war")

    from("${project.projectDir}/webapp") {
        // exclude the database drivers
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

    // copy SOLR config files separately to avoid the replace filter corrupting them
    from("${project.projectDir}/webapp/WEB-INF/solr") {
        into("/WEB-INF/solr/")
    }

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
        
        filter(ReplaceTokens::class, "tokens" to mapOf(
            "OPENCMS_VERSION_NUMBER" to productVersion,
            "OPENCMS_VERSION" to "$productName $productVersion",
            "DRIVERS_VFS" to drivers_vfs,
            "DRIVERS_PROJECT" to drivers_project,
            "DRIVERS_USER" to drivers_user,
            "DRIVERS_HISTORY" to drivers_history,
            "DRIVERS_CONFIGURATION" to drivers_configuration,
            "DRIVERS_SUBSCRIPTION" to drivers_subscription,
            "ADDITIONAL_DB_POOLS" to db_additional_pools,
            "OPENCMS_CONFIGURATION" to opencms_configuration,
            "RUNTIME_INFO" to system_runtimeinfo,
            "COMMENT_UPDATER_START" to "",
            "COMMENT_UPDATER_END" to "",
            "COMMENT_WAR_START" to "<!--",
            "COMMENT_WAR_END" to "-->"
        ))
    }

    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("opencms-gwt*.jar")
        exclude("opencms-test*.jar")
        exclude("*-sources.jar")
        exclude("*-javadoc.jar")
        into("/WEB-INF/lib")
    }

    val configurations = project.configurations
    into("/WEB-INF/lib") { from(configurations.getByName("distribution")) }
    into("/WEB-INF/lib") { from(configurations.getByName("moduleDeps")) }
    into("/WEB-INF/lib/apis") { from(configurations.getByName("usedApis")) }

    project.fileTree("webapp/WEB-INF/setupdata/database") { include("**/*.jar") }.forEach { driverJar ->
        // copy the database drivers into the lib folder
        from(driverJar.path) { into("/WEB-INF/lib") }
    }

    from(modulesDistsDir) {
        into("/WEB-INF/packages/modules")
        localeModules.forEach { localeModule ->
            exclude("${localeModule}*.zip")
        }
    }

    from("${project.projectDir}/lib/jni") { into("/WEB-INF/lib/jni") }

    val extModulesProject = project.findProject(":extmodules")
    if (extModulesProject != null) {
        val extModulesDir = extModulesProject.layout.buildDirectory.dir("modules").get().asFile
        from("${extModulesDir}/libs") {
            include("*.jar")
            into("/WEB-INF/lib")
        }
        from("${extModulesProject.layout.buildDirectory.dir("modulesZip").get().asFile}") {
            into("/WEB-INF/packages/modules")
        }
    }
}

tasks.register<Zip>("updater") {
    dependsOn("setupJar", "resourcesJar", "modulesJar", "allModules")
    
    if (project.findProject(":extmodules") != null) {
        dependsOn(":extmodules:bindist")
    }

    dependsOn(tasks.matching { it.name.startsWith("dist_") })

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
        
        filter(ReplaceTokens::class, "tokens" to mapOf(
            "OPENCMS_VERSION_NUMBER" to productVersion,
            "OPENCMS_VERSION" to "$productName $productVersion",
            "PRESERVE_LIB_MODULES" to preserveLibModules
        ))
    }

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

    from("${project.projectDir}/webapp/resources") {
        into("/resources")
        include("*")
        include("**/*")
    }

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

    from(layout.buildDirectory.dir("libs")) {
        include("*.jar")
        exclude("opencms-test*.jar")
        exclude("opencms-gwt*.jar")
        exclude("*-sources.jar")
        exclude("*-javadoc.jar")
        into("/WEB-INF/lib")
    }

    into("/WEB-INF/lib") {
        from(configurations.getByName("distribution"))
    }

    from(modulesDistsDir) {
        into("/WEB-INF/updatedata/modules")
        localeModules.forEach { localeModule ->
            exclude("${localeModule}*.zip")
        }
        exclude("*mercury*.zip")
    }

    project.fileTree("webapp/WEB-INF/setupdata/database") { include("**/*.jar") }.forEach { driverJar ->
        // copy the database drivers into the lib folder
        from(driverJar.path) { into("/WEB-INF/lib") }
    }

    from("${project.projectDir}/webapp/WEB-INF/solr") {
        into("/WEB-INF/solr-update/")
        include("*")
        include("**/*")
    }

    from("${project.projectDir}/webapp/WEB-INF/updatedata/readme.txt") {
        into("/")
        filter(ReplaceTokens::class, "tokens" to mapOf(
            "OPENCMS_VERSION_NUMBER" to productVersion,
            "OPENCMS_VERSION" to "$productName $productVersion"
        ))
    }
}

tasks.register<Zip>("bindist") {
    dependsOn("war")
    archiveBaseName.set("opencms")
    from(layout.buildDirectory.file("distributions/opencms.war"))
    from(project.projectDir) {
        include("INSTALL.md")
        include("LICENSE")
        include("README.md")
        include("history.txt")
        filter(ReplaceTokens::class, "tokens" to mapOf(
            "OPENCMS_VERSION_NUMBER" to productVersion,
            "OPENCMS_VERSION" to "$productName $productVersion"
        ))
    }
}
