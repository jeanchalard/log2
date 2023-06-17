tasks.register("make") {
  dependsOn(":js:make")
  dependsOn(":html:make")
  dependsOn(":ruby:make")
}
