val out = "${rootDir}/../out"

tasks.register<Exec>("gen") {
  commandLine("./genlog.rb", "-o", buildDir, "-s", "${rootDir}/..", "-r", "${rootDir}/../rules/calendar.grc")
  inputs.files(fileTree("${rootDir}/..") { include("data*/*") }, fileTree("${rootDir}/../rules") { include("*") }, "genlog.rb")
  outputs.files("${buildDir}/data.js")
}

tasks.register<Copy>("make") {
  dependsOn(":ruby:gen")
  from(buildDir)
  into(out)
}
