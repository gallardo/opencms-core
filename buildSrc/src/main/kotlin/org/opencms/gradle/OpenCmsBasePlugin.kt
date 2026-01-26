package org.opencms.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * Base plugin for OpenCms build system.
 * 
 * Its primary responsibility is to register the 'opencms' DSL extension
 * and ensure all common build parameters are initialized.
 */
class OpenCmsBasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register the self-initializing extension
        // Any other plugin that needs 'opencms { }' will apply this plugin first.
        project.extensions.create<OpenCmsExtension>("opencms", project)
    }
}
