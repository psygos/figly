plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// Dev tool: render the golden weeks to SVG plates + ASCII for visual tuning.
tasks.register<JavaExec>("renderGoldens") {
    group = "figly"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.figly.core.RenderGoldens")
    args(project.findProperty("plateOut")?.toString() ?: "/tmp/figly-plates")
}
