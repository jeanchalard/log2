val out = "${rootDir}/../out"

tasks.register<Copy>("assemble") {
  from(".") {
    include("*.html", "*.css")
  }
  into(buildDir)
}

tasks.register<Copy>("make") {
  dependsOn(":html:assemble")
  from(buildDir)
  into(out)
}
