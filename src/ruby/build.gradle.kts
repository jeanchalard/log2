val rules = "work"
val data = "data/2023_09*"

val out = "${rootDir}/../out"

tasks.register<Exec>("gen") {
  commandLine("./genlog.rb", "-o", buildDir, "-r", "${rootDir}/../rules/${rules}.grc", "-s", "${rootDir}/../${data}")
  inputs.files(fileTree("${rootDir}/..") { include("data*/*") }, fileTree("${rootDir}/../rules") { include("*") }, "genlog.rb")
  outputs.files("${buildDir}/data.js")
}

tasks.register<Copy>("make") {
  dependsOn(":ruby:gen")
  from(buildDir)
  into(out)
}
