plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

val externalBuildRoot = providers.gradleProperty("bilitvBuildRoot")
  .orElse(layout.projectDirectory.dir("builds").asFile.absolutePath)
  .get()

layout.buildDirectory.set(file("$externalBuildRoot/root"))

subprojects {
  val projectBuildName = path
    .removePrefix(":")
    .replace(':', '-')
    .ifBlank { "root" }
  layout.buildDirectory.set(file("$externalBuildRoot/$projectBuildName"))
}
