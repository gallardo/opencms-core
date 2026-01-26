package org.opencms.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.JavaVersion
import javax.inject.Inject

/**
 * Global configuration for the OpenCms build system.
 * 
 * This extension provides a type-safe way to configure global build parameters in `build.gradle`.
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
     * Defaults to "OpenCms".
     * 
     * Example: Becomes "Alkacon OpenCms" in Javadoc titles.
     */
    abstract val productName: Property<String>

    /**
     * The product version. Centralizes versioning for all artifacts.
     * 
     * Example: Used as 'Implementation-Version' in JAR manifests and replaced as 
     * '@OPENCMS_VERSION_NUMBER@' in web.xml and other templates.
     */
    abstract val productVersion: Property<String>
    
    /**
     * The Java version used for compilation and compatibility across all projects.
     * Defaults to [JavaVersion.VERSION_11].
     */
    abstract val javaVersion: Property<JavaVersion>

    /**
     * Controls whether Javadoc tasks are registered and executed.
     * Useful for speeding up CI builds by setting it to 'false'.
     */
    abstract val addJavadoc: Property<Boolean>

    /**
     * Max heap size for memory-intensive tasks like GWT compilation or unit tests.
     * Format: "1024m", "2g", etc.
     */
    abstract val maxHeapSize: Property<String>

    init {
        productName.convention("OpenCms")
        productVersion.convention(project.version.toString())
        javaVersion.convention(JavaVersion.VERSION_11)
        addJavadoc.convention(true)
        maxHeapSize.convention("1024m")
    }
}
