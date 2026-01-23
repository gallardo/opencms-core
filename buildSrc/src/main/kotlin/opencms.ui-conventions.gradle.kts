
plugins {
    id("java-library")
}

val max_heap_size = project.findProperty("max_heap_size") ?: "1024m"

// Workplace Theme tasks (Sass compilation)
tasks.register<JavaExec>("workplaceTheme") {
    doFirst {
        println("======================================================")
        println("Building workplace theme")
        println("Generating ${layout.buildDirectory.dir("workplaceThemes/opencms/styles.css").get().asFile}")
        println("======================================================")
        val dir = layout.buildDirectory.dir("workplaceThemes/opencms").get().asFile
        if (dir.exists()) {
            project.delete(dir)
        }
        dir.mkdirs()
    }

    mainClass.set("com.vaadin.sass.SassCompiler")
    // Sass compiler needs the gwt classpath which contains vaadin-sass and dependencies
    classpath = project.extensions.getByType<SourceSetContainer>().getByName("gwt").compileClasspath

    args = listOf(
        "${project.projectDir}/webapp/workplaceThemes/VAADIN/themes/opencms/styles.scss",
        layout.buildDirectory.file("workplaceThemes/opencms/styles.css").get().asFile.toString()
    )
    maxHeapSize = max_heap_size.toString()
}

tasks.register<JavaExec>("opencmsFonts") {
    doFirst {
        println("======================================================")
        println("Building OpenCms fonts CSS")
        println("Generating ${layout.buildDirectory.dir("workplaceThemes/opencmsFonts/opencmsFonts.css").get().asFile}")
        println("======================================================")
        val dir = layout.buildDirectory.dir("workplaceThemes/opencmsFonts").get().asFile
        if (dir.exists()) {
            project.delete(dir)
        }
        dir.mkdirs()
    }

    mainClass.set("com.vaadin.sass.SassCompiler")
    classpath = project.extensions.getByType<SourceSetContainer>().getByName("gwt").compileClasspath

    args = listOf(
        "${project.projectDir}/webapp/workplaceThemes/VAADIN/themes/opencms/opencmsFonts.scss",
        layout.buildDirectory.file("workplaceThemes/opencmsFonts/opencmsFonts.css").get().asFile.toString()
    )
    maxHeapSize = max_heap_size.toString()
}

// Ensure resourcesJar (if present) depends on these
tasks.matching { it.name == "resourcesJar" }.all {
    dependsOn("workplaceTheme", "opencmsFonts")
}
