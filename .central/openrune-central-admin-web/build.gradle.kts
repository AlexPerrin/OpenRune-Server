plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("22.12.0")
    npmVersion.set("10.9.0")
    download.set(true)
    nodeProjectDir.set(projectDir)
}

tasks.named("npm_run_build") {
    inputs.dir("src")
    inputs.files("index.html", "vite.config.ts", "tsconfig.json", "tsconfig.app.json", "package.json")
    outputs.dir("dist")
}

tasks.register("buildAdminWeb") {
    group = "build"
    description = "Builds the React admin SPA into dist/ (optional; not invoked by Central server build/run)"
    dependsOn("npm_run_build")
}
