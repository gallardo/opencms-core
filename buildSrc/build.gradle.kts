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
    }
}
