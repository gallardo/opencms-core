
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("java-library")
}

// GWT related properties
val gwtProps = Properties()
val propsFile = project.file("src-gwt/gwt-modules.properties")
if (propsFile.exists()) {
    FileInputStream(propsFile).use { gwtProps.load(it) }
}
val gwtModuleNames = gwtProps.getProperty("gwtmodules") ?: ""
project.extensions.extraProperties.set("gwtModuleNames", gwtModuleNames)

val max_heap_size = project.findProperty("max_heap_size") ?: "1024m"

// GWT Module Tasks Generation
gwtModuleNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { gwtModule ->
    tasks.register<JavaExec>("gwt_$gwtModule") {
        dependsOn("gwtClasses")
        val buildDir = layout.buildDirectory.dir("gwt/$gwtModule").get().asFile
        val extraDir = layout.buildDirectory.dir("extra/$gwtModule").get().asFile
        
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
            // to clean the output directory, delete it first
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
        maxHeapSize = max_heap_size.toString()
    }

    // Restore the dependency and configuration on resourcesJar
    tasks.matching { it.name == "resourcesJar" }.all {
        val jarTask = this as Jar
        jarTask.dependsOn("gwt_$gwtModule")
        
        if (gwtModule != "org.opencms.ui.WidgetSet") {
            jarTask.from(layout.buildDirectory.dir("gwt/$gwtModule")) {
                exclude("**/WEB-INF/**")
                into("OPENCMS/gwt")
            }
        } else {
             jarTask.from(layout.buildDirectory.dir("gwt/org.opencms.ui.WidgetSet")) {
                include("org.opencms.ui.WidgetSet/**")
                into("VAADIN/widgetsets")
            }
        }
    }
}

// GWT Main Jars and Docs
tasks.register<Jar>("gwtJar") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val gwtSourceSet = sourceSets.getByName("gwt")
    val mainSourceSet = sourceSets.getByName("main")
    
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

tasks.register<Javadoc>("javadocGwt") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val gwtSourceSet = sourceSets.getByName("gwt")
    
    val ocmsVersion = project.extensions.extraProperties.let { 
        if (it.has("ocmsVersion")) it.get("ocmsVersion") else project.version 
    }
    val javaTargetVersion = project.findProperty("java_target_version")?.toString() ?: "11"
    
    doLast {
        project.copy {
            from("${project.projectDir}/doc/javadoc/logos")
            into(layout.buildDirectory.dir("docs/javadocGwt/logos"))
        }
    }

    (options as StandardJavadocDocletOptions).apply {
        memberLevel = JavadocMemberLevel.PROTECTED
        charSet = "UTF-8"
        isAuthor = true
        isVersion = true
        links("false")
        source = javaTargetVersion
        windowTitle = "OpenCms GWT Components API, version $ocmsVersion"
        docTitle = "OpenCms GWT Components API, version $ocmsVersion"
        header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
    }
    source = gwtSourceSet.allJava
    classpath = gwtSourceSet.compileClasspath
    setDestinationDir(layout.buildDirectory.dir("docs/javadocGwt").get().asFile)
}

tasks.register<Jar>("javadocJarGwt") {
    dependsOn("javadocGwt")
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("docs/javadocGwt"))
    archiveBaseName.set("opencms-gwt")
}

tasks.register<Jar>("sourcesJarGwt") {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    val gwtSourceSet = sourceSets.getByName("gwt")
    val mainSourceSet = sourceSets.getByName("main")
    
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
