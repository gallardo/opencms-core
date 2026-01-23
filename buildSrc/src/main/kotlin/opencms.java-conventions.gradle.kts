
plugins {
    id("java-library")
    id("eclipse")
}

// Repositories
repositories {
    mavenLocal()
    if (project.hasProperty("additional_repositories")) {
        val repos = project.property("additional_repositories") as String
        repos.split(";").forEach { repo ->
            maven { url = uri(repo) }
        }
    }
    mavenCentral()
    maven {
        url = uri("https://maven.restlet.talend.com")
    }
    maven {
        url = uri("https://maven.vaadin.com/vaadin-addons")
    }
}

// Java Toolchain Configuration
java {
    val targetVer = project.findProperty("java_target_version")?.toString() ?: "1.8"
    sourceCompatibility = JavaVersion.toVersion(targetVer)
    targetCompatibility = JavaVersion.toVersion(targetVer)
}

// SourceSets
sourceSets {
    main {
        java.srcDirs("src")
        resources.srcDirs("src")
    }
    create("modules") {
        java.srcDirs("src-modules")
        resources.srcDirs("src-modules")
    }
    create("gwt") {
        java {
            srcDirs("src-gwt")
            exclude("**/super_src/**")
        }
        resources {
            srcDirs("src-gwt")
            exclude("**/super_src/**")
        }
    }
    create("setup") {
        java.srcDirs("src-setup")
        resources.srcDirs("src-setup")
    }
    test {
        java {
            srcDirs("test")
            exclude("data/**")
        }
        resources {
            srcDirs("test")
            exclude("data/**")
        }
    }
    create("testGwt") {
        java {
            srcDirs("test-gwt")
            exclude("**/super_src/**")
        }
        resources {
            srcDirs("test-gwt")
            exclude("**/super_src/**")
        }
    }
}

// Configurations
configurations {
    create("distribution") {
        description = "libraries included into the opencms distribution"
    }

    val api = getByName("api")
    val distribution = getByName("distribution")
    api.extendsFrom(distribution)
    
    create("usedApis") {
        description = "APIs that should normally be provided by the servlet container, but are also needed for the CmsShell, so they're bundled in the WAR." 
    }
    val usedApis = getByName("usedApis")

    create("compile") {
        description = "used to compile the opencms.jar and the modules jars"
        extendsFrom(distribution)
        extendsFrom(usedApis)
    }

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    val implementation = getByName("implementation")
    val compile = getByName("compile")
    implementation.extendsFrom(compile)

    create("modulesCompile") {
        description = "used to compile the modules classes"
        extendsFrom(compile)
    }
    val modulesCompile = getByName("modulesCompile")

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    val modulesImplementation = getByName("modulesImplementation")
    modulesImplementation.extendsFrom(modulesCompile)

    create("moduleDeps") {
        description = "additional dependencies required by external modules"
    }

    create("testCompile") {
         description = "legacy test compile"
}
    val testCompile = getByName("testCompile")
    testCompile.extendsFrom(modulesCompile)

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    val testImplementation = getByName("testImplementation")
    val setupImplementation = getByName("setupImplementation") 
    testImplementation.extendsFrom(testCompile)
    testImplementation.extendsFrom(setupImplementation)

    create("gwtCompile") {
        description = "needed to generate the GWT JavaScript resources"
        extendsFrom(modulesCompile)
    }
    val gwtCompile = getByName("gwtCompile")

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    val gwtImplementation = getByName("gwtImplementation")
    gwtImplementation.extendsFrom(gwtCompile)

    create("testGwtCompile") {
        description = "needed to run GWT test cases"
        extendsFrom(gwtCompile)
    }
    val testGwtCompile = getByName("testGwtCompile")

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    val testGwtImplementation = getByName("testGwtImplementation")
    testGwtImplementation.extendsFrom(testGwtCompile)

    // Fix the jetty incompatibility for the version from distribution used by Solr and the one for the GWT test cases.
    val testGwtRuntimeClasspath = getByName("testGwtRuntimeClasspath")
     testGwtRuntimeClasspath.resolutionStrategy {
            force("org.eclipse.jetty:jetty-client:9.4.50.v20221201")
            force("org.eclipse.jetty:jetty-http:9.4.50.v20221201")
            force("org.eclipse.jetty:jetty-io:9.4.50.v20221201")
            force("org.eclipse.jetty:jetty-util:9.4.50.v20221201")
            force("org.eclipse.jetty.http2:http2-common:9.4.50.v20221201")
            force("org.eclipse.jetty.http2:http2-client:9.4.50.v20221201")
            force("org.eclipse.jetty.http2:http2-http-client-transport:9.4.50.v20221201")
        }

    create("setupCompile") {
        extendsFrom(modulesCompile)
    }
    val setupCompile = getByName("setupCompile")

    // Default configuration of the java-library plugin
    // It corresponds to our compile configuration, that's why we have to use extendsFrom here
    setupImplementation.extendsFrom(setupCompile)
}

// Eclipse
eclipse {
    classpath {
        file {
            whenMerged {
                val classpath = this as org.gradle.plugins.ide.eclipse.model.Classpath
                classpath.entries.forEach { entry ->
                    if (entry is org.gradle.plugins.ide.eclipse.model.SourceFolder) {
                         if (entry.path.contains("gwt")) {
                            entry.entryAttributes["test"] = "true"
                        }
                        if (entry.path == "src") {
                            entry.output = "bin/main"
                            entry.entryAttributes["test"] = "false"
                        }
                    }
                }
            }
        }
        plusConfigurations.addAll(configurations.matching { it.name.endsWith("Compile") })
        isDownloadSources = true
    }
}

// Copy Tasks (Generic)
tasks.register<Copy>("copyDeps") {
    from(configurations.getByName("distribution"))
    into(layout.buildDirectory.dir("deps"))
}

tasks.register<Copy>("copyCompileDeps") {
    from(configurations.getByName("compile"))
    into(layout.buildDirectory.dir("deps"))
}

// Setup Tasks
tasks.register<Jar>("setupJar") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("setup").output)
    archiveFileName.set("opencms-setup.jar")
    archiveBaseName.set("opencms-setup")
    group = "OpenCms core JARs"
    exclude("**/.gitignore")
}

tasks.register<Javadoc>("javadocSetup") {
    dependsOn("jar")
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    source = sourceSets.getByName("setup").allJava
    classpath = sourceSets.getByName("setup").compileClasspath
    setDestinationDir(layout.buildDirectory.dir("docs/javadocSetup").get().asFile)
    
    doLast {
        project.copy {
            from("${project.projectDir}/doc/javadoc/logos")
            into(layout.buildDirectory.dir("docs/javadocSetup/logos"))
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
        windowTitle = "OpenCms Setup API, version ${project.version}"
        docTitle = "OpenCms Setup API, version ${project.version}"
        header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
    }
}

tasks.register<Jar>("javadocJarSetup") {
    dependsOn("javadocSetup")
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("docs/javadocSetup"))
    archiveBaseName.set("opencms-setup")
}

tasks.register<Jar>("sourcesJarSetup") {
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    from(sourceSets.getByName("setup").java.srcDirs)
    archiveClassifier.set("sources")
    archiveBaseName.set("opencms-setup")
}
