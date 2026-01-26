package org.opencms.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.JavaVersion
import java.io.File
import java.util.Properties
import javax.inject.Inject

/**
 * Global configuration for the OpenCms build system.
 * 
 * This extension provides a type-safe way to configure global build parameters in `build.gradle`.
 * It also encapsulates the logic for resolving property defaults from the project and 
 * version.properties files.
 * 
 * Example usage in `build.gradle`:
 * ```kotlin
 * opencms {
 *     productName.set("OpenCms")
 *     productVersion.set("11.0")
 *     javaVersion.set(JavaVersion.VERSION_11)
 * }
 * ```
 */
abstract class OpenCmsExtension @Inject constructor(private val project: Project) {

    /**
     * The product name. Used in JAR manifests and Documentation titles.
     * Defaults to the 'product_name' project property or "OpenCms".
     *
     * Example: Becomes "Alkacon OpenCms" in Javadoc titles.
     */
    abstract val productName: Property<String>

    /**
     * The product version. Centralizes versioning for all artifacts.
     * Defaults to the 'opencms_version' project property or the value in 'version.properties'.
     * 
     * Example: Used as 'Implementation-Version' in JAR manifests and replaced as 
     * '@OPENCMS_VERSION_NUMBER@' in web.xml and other templates.
     */
    abstract val productVersion: Property<String>
    
    /**
     * The Java version used for compilation and compatibility across all projects.
     * Defaults to the 'java_target_version' project property or [JavaVersion.VERSION_11].
     */
    abstract val javaVersion: Property<JavaVersion>

    /**
     * Controls whether Javadoc tasks are registered and executed.
     * Useful for speeding up CI builds by setting it to 'false' (via 'skip_javadoc' property).
     */
    abstract val addJavadoc: Property<Boolean>

    /**
     * Max heap size for memory-intensive tasks like GWT compilation or unit tests.
     * Format: "1024m", "2g", etc.
     * Defaults to the 'max_heap_size' project property or "1024m".
     */
    abstract val maxHeapSize: Property<String>

    /**
     * Whether an external 'version.properties' file is being used.
     */
    abstract val useExternalVersion: Property<Boolean>

    /**
     * The location where an external version.properties file is expected.
     */
    private val externalVersionFile: File
        get() = project.layout.buildDirectory.file("../version.properties").get().asFile

    init {
        // Resolve Version from properties file
        val versionProps = loadVersionProperties()
        val resolvedVersion = project.findProperty("opencms_version")?.toString() 
            ?: versionProps.getProperty("version.number") 
            ?: project.version.toString()

        productVersion.convention(resolvedVersion)
        
        // Use the helper property to check for existence
        useExternalVersion.convention(externalVersionFile.exists())

        // Sync the official project version with the resolved OpenCms version
        project.version = resolvedVersion
        
        // Resolve Product Name
        val name = project.findProperty("product_name")?.toString() ?: "OpenCms"
        productName.convention(name)

        // Resolve Java Version
        val javaTarget = project.findProperty("java_target_version")?.toString() ?: "11"
        javaVersion.convention(JavaVersion.toVersion(javaTarget))

        // Resolve Javadoc flag
        val skipJavadoc = project.findProperty("skip_javadoc")?.toString()?.toBoolean() ?: false
        addJavadoc.convention(!skipJavadoc)

        // Resolve Max Heap Size
        val maxHeap = project.findProperty("max_heap_size")?.toString() ?: "1024m"
        maxHeapSize.convention(maxHeap)
    }

    /**
     * Locates and loads the version.properties file (external or internal).
     */
    private fun loadVersionProperties(): Properties {
        val props = Properties()
        
        // Use the helper property here as well
        val propFile = if (externalVersionFile.exists()) {
            externalVersionFile
        } else {
            // Fallback to internal file
            project.file("src/org/opencms/main/version.properties")
        }

        if (!propFile.exists()) {
            throw org.gradle.api.GradleException(
                "Critical Error: 'version.properties' not found at ${propFile.absolutePath}. " +
                "The build cannot proceed without version information."
            )
        }

        propFile.inputStream().use { props.load(it) }
        return props
    }
}
