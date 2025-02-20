val rules = "work"
val data = "data/2025_02.log"
//val data = "data*/*.log"

val out = "${rootDir}/../out"

tasks.register<Compile>("filter_work") {
  inputs.files(fileTree("${rootDir}/..") { include("data/*") }, "filter_work.rb")
  command("./filter_work.rb -i \$source -o \$target")
  transform {
    it.replace("/data", "/work")
  }
}

tasks.register<Exec>("gen") {
  commandLine("./genlog.rb", "-o", buildDir, "-r", "${rootDir}/../rules/${rules}.grc", "-s", "${rootDir}/../${data}")
  inputs.files(fileTree("${rootDir}/..") { include("data*/*") }, fileTree("${rootDir}/../rules") { include("*") }, "genlog.rb")
  outputs.files("${buildDir}/data.js")
}

tasks.register<Copy>("make") {
  dependsOn(":ruby:filter_work")
  dependsOn(":ruby:gen")
  from(buildDir)
  into(out)
}
