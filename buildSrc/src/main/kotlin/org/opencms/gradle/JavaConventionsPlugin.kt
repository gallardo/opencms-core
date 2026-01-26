package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.*

/**
 * Hardened Java Conventions Plugin for OpenCms.
 * 
 * Replaces 'opencms.java-conventions.gradle.kts' with a formal Kotlin class.
 */
class JavaConventionsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = registerExtension(project)
        
        applyCorePlugins(project)
        configureRepositories(project)
        configureJavaToolchain(project, extension)
        configureSourceSets(project)
        configureConfigurations(project)
        configureEclipse(project)
        registerTasks(project, extension)
    }

    private fun registerExtension(project: Project): OpenCmsExtension {
        return project.extensions.create<OpenCmsExtension>("opencms", project)
    }

    private fun applyCorePlugins(project: Project) {
        project.plugins.apply("java-library")
        project.plugins.apply("eclipse")
    }

    private fun configureRepositories(project: Project) {
        project.repositories {
            mavenLocal()
            if (project.hasProperty("additional_repositories")) {
                val repos = project.property("additional_repositories") as String
                repos.split(";").forEach { repo ->
                    maven { url = project.uri(repo) }
                }
            }
            mavenCentral()
            maven { url = project.uri("https://maven.restlet.talend.com") }
            maven { url = project.uri("https://maven.vaadin.com/vaadin-addons") }
        }
    }

    private fun configureJavaToolchain(project: Project, extension: OpenCmsExtension) {
        project.configure<JavaPluginExtension> {
            sourceCompatibility = extension.javaVersion.get()
            targetCompatibility = extension.javaVersion.get()
        }
    }

    private fun configureSourceSets(project: Project) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        sourceSets.apply {
            getByName("main") {
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
            getByName("test") {
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
    }

    private fun configureConfigurations(project: Project) {
        project.configurations.apply {
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

            val implementation = getByName("implementation")
            val compile = getByName("compile")
            implementation.extendsFrom(compile)

            create("modulesCompile") {
                description = "used to compile the modules classes"
                extendsFrom(compile)
            }
            val modulesCompile = getByName("modulesCompile")
            getByName("modulesImplementation").extendsFrom(modulesCompile)

            create("moduleDeps") {
                description = "additional dependencies required by external modules"
            }

            create("testCompile") {
                description = "legacy test compile"
            }.extendsFrom(modulesCompile)

            getByName("testImplementation").extendsFrom(getByName("testCompile"))
            getByName("testImplementation").extendsFrom(getByName("setupImplementation"))

            create("gwtCompile") {
                description = "needed to generate the GWT JavaScript resources"
                extendsFrom(modulesCompile)
            }
            getByName("gwtImplementation").extendsFrom(getByName("gwtCompile"))

            create("testGwtCompile") {
                description = "needed to run GWT test cases"
                extendsFrom(getByName("gwtCompile"))
            }
            getByName("testGwtImplementation").extendsFrom(getByName("testGwtCompile"))

            getByName("testGwtRuntimeClasspath").resolutionStrategy {
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
            getByName("setupImplementation").extendsFrom(getByName("setupCompile"))
        }
    }

    private fun configureEclipse(project: Project) {
        project.configure<EclipseModel> {
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
                plusConfigurations.addAll(project.configurations.matching { it.name.endsWith("Compile") })
                isDownloadSources = true
            }
        }
    }

    private fun registerTasks(project: Project, extension: OpenCmsExtension) {
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        
        project.tasks.apply {
            register<Copy>("copyDeps") {
                from(project.configurations.getByName("distribution"))
                into(project.layout.buildDirectory.dir("deps"))
            }

            register<Copy>("copyCompileDeps") {
                from(project.configurations.getByName("compile"))
                into(project.layout.buildDirectory.dir("deps"))
            }

            register<Jar>("setupJar") {
                dependsOn("jar")
                from(sourceSets.getByName("setup").output)
                archiveFileName.set("opencms-setup.jar")
                archiveBaseName.set("opencms-setup")
                group = "OpenCms core JARs"
                exclude("**/.gitignore")
            }

            register<Javadoc>("javadocSetup") {
                dependsOn("jar")
                source = sourceSets.getByName("setup").allJava
                classpath = sourceSets.getByName("setup").compileClasspath
                setDestinationDir(project.layout.buildDirectory.dir("docs/javadocSetup").get().asFile)

                doLast {
                    project.copy {
                        from("${project.projectDir}/doc/javadoc/logos")
                        into(project.layout.buildDirectory.dir("docs/javadocSetup/logos"))
                    }
                }

                (options as StandardJavadocDocletOptions).apply {
                    memberLevel = JavadocMemberLevel.PROTECTED
                    charSet = "UTF-8"
                    isAuthor = true
                    isVersion = true
                    links("false")
                    source = extension.javaVersion.get().toString()
                    windowTitle = "OpenCms Setup API, version ${extension.productVersion.get()}"
                    docTitle = "OpenCms Setup API, version ${extension.productVersion.get()}"
                    header = "<script type=\"text/javascript\"> if (window.location.href.indexOf(\"overview-frame\") == -1) { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.alkacon.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/Alkacon.svg\\\" /></a>\"); } else { document.write(\"<a id=\\\"brandingLink\\\" target=\\\"_blank\\\" href=\\\"http://www.opencms.com\\\"><img border=\\\"0\\\" id=\\\"brandingPic\\\" src=\\\"{@docRoot}/logos/OpenCms.svg\\\" /></a>\"); }</script>"
                }
            }

            register<Jar>("javadocJarSetup") {
                dependsOn("javadocSetup")
                archiveClassifier.set("javadoc")
                from(project.layout.buildDirectory.dir("docs/javadocSetup"))
                archiveBaseName.set("opencms-setup")
            }

            register<Jar>("sourcesJarSetup") {
                from(sourceSets.getByName("setup").java.srcDirs)
                archiveClassifier.set("sources")
                archiveBaseName.set("opencms-setup")
            }
        }
    }
}
