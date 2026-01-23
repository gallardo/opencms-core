
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("java-library")
}

// Module Packaging related properties
val modulesList: String = project.findProperty("modules_list")?.toString() ?: ""
val allModuleNames = modulesList.split(",").map { it.trim() }.filter { it.isNotEmpty() }
project.extensions.extraProperties.set("allModuleNames", allModuleNames)

val modulesDistsDir = layout.buildDirectory.dir("modulesZip").get().asFile
project.extensions.extraProperties.set("modulesDistsDir", modulesDistsDir)

// modulesJar task
tasks.register<Jar>("modulesJar") {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("modules").output)
    archiveFileName.set("opencms-modules.jar")
    archiveBaseName.set("opencms-modules")
    group = "OpenCms core JARs"
    exclude("**/.gitignore")
}

// Module and Javadoc Tasks Generation
tasks.register<Javadoc>("javadocModules") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val ocmsVersion = project.extensions.extraProperties.let { 
        if (it.has("ocmsVersion")) it.get("ocmsVersion") else project.version 
    }
    
    doLast {
        project.copy {
            from("${project.projectDir}/doc/javadoc/logos")
            into(layout.buildDirectory.dir("docs/javadocModules/logos"))
        }
    }

    val javaTargetVersion = project.findProperty("java_target_version")?.toString() ?: "11"

    (options as StandardJavadocDocletOptions).apply {
        memberLevel = JavadocMemberLevel.PROTECTED
        charSet = "UTF-8"
        isAuthor = true
        isVersion = true
        links("false")
        source = javaTargetVersion
        docTitle = "OpenCms Workplace Modules API, version $ocmsVersion"
        windowTitle = "OpenCms Workplace Modules API, version $ocmsVersion"
        header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
    }
    source = sourceSets.getByName("modules").allJava
    classpath = sourceSets.getByName("modules").compileClasspath
    setDestinationDir(layout.buildDirectory.dir("docs/javadocModules").get().asFile)
}

tasks.register<Jar>("javadocJarModules") {
    dependsOn("javadocModules")
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("docs/javadocModules"))
    archiveBaseName.set("opencms-modules")
}

tasks.register<Jar>("sourcesJarModules") {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("modules").java.srcDirs)
    archiveClassifier.set("sources")
    archiveBaseName.set("opencms-modules")
}

var preserveLibModulesVar = ""
val localeModulesVar = mutableListOf<String>()

// Iterate all available modules and create the required tasks
allModuleNames.forEach { moduleName ->
    val moduleFolder = project.file("modules/$moduleName")
    val propertyFile = project.file("${moduleFolder}/module.properties")
    var workplacelocalization: String? = null
    
    if (propertyFile.exists()) {
        val moduleProperties = Properties()
        FileInputStream(propertyFile).use { moduleProperties.load(it) }
        workplacelocalization = moduleProperties.getProperty("workplacelocalization")
    }

    val manifestFile = project.file("${moduleFolder}/resources/manifest.xml")
    val moduleDependencies = mutableListOf<String>()
    var moduleVersion = project.version.toString()

    if (manifestFile.exists()) {
        val parsedManifest = groovy.util.XmlParser().parse(manifestFile)
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
    }

    if (workplacelocalization != null) {
        localeModulesVar.add(moduleName)
        preserveLibModulesVar += "$moduleName,"
    }

    tasks.register<Zip>("dist_$moduleName") {
        group = "OpenCms module ZIPs"
        
        val effectiveVersion = if (project.hasProperty("noVersion")) "" else moduleVersion
        archiveVersion.set(effectiveVersion)
        archiveBaseName.set(moduleName)
        destinationDirectory.set(modulesDistsDir)

        // excluding jars from modules, jars will be placed in the WEB-INF lib folder through the moduleDeps configuration
        from("${moduleFolder}/resources") {
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
        tasks.register<Jar>("jar_$moduleName") {
            group = "OpenCms module JARs"
            manifest {
                attributes(
                    "Implementation-Title" to "Alkacon OpenCms",
                    "Implementation-Version" to moduleVersion,
                    "OpenCms-Localization" to localization
                )
            }
            from("$moduleFolder/resources/system/workplace/locales/$localization/messages")
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

        tasks.named<Zip>("dist_$moduleName") {
            dependsOn("jar_$moduleName")
        }
    }
}

// Pass gathered data to project extensions for WAR/Updater tasks
project.extensions.extraProperties.set("localeModules", localeModulesVar)
project.extensions.extraProperties.set("preserveLibModules", preserveLibModulesVar)

tasks.register("allModules") {
    dependsOn(tasks.matching { it.name.startsWith("dist_") })
    doLast {
        println("======================================================")
        println("Done building modules")
        println("======================================================")
    }
}
