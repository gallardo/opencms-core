plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

gradlePlugin {
    plugins {
        register("base") {
            id = "opencms.base"
            implementationClass = "org.opencms.gradle.OpenCmsBasePlugin"
        }
        register("javaConventions") {
            id = "opencms.java-conventions"
            implementationClass = "org.opencms.gradle.JavaConventionsPlugin"
        }
        register("testingConventions") {
            id = "opencms.testing-conventions"
            implementationClass = "org.opencms.gradle.TestingConventionsPlugin"
        }
        register("gwtConventions") {
            id = "opencms.gwt-conventions"
            implementationClass = "org.opencms.gradle.GwtConventionsPlugin"
        }
        register("uiConventions") {
            id = "opencms.ui-conventions"
            implementationClass = "org.opencms.gradle.UiConventionsPlugin"
        }
        register("modulePackaging") {
            id = "opencms.module-packaging"
            implementationClass = "org.opencms.gradle.ModulePackagingPlugin"
        }
    }
}
