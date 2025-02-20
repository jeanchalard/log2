import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.Internal

open class Compile : DefaultTask() {
  @InputFiles val sourceFiles = project.objects.fileCollection()
  @Internal var transform : ((String) -> String)? = null
  @Internal var command : String? = null

  @TaskAction
  fun exec() {
    val cmd = command
    val trf = transform
    if (null == cmd) throw GradleException("Command must be specified for Compile")
    if (null == trf) throw GradleException("Transform must be specified for Compile")
    inputs.files.forEach { sourceFile ->
      val targetFile = File(trf.invoke(sourceFile.path))
      if (!targetFile.exists() || sourceFile.lastModified() > targetFile.lastModified()) {
        val actualCommand = cmd.replace("\$target", targetFile.absolutePath).replace("\$source", sourceFile.absolutePath)
        println(actualCommand)
        val processBuilder = ProcessBuilder(*actualCommand.split(" ").toTypedArray())
        processBuilder.directory(project.projectDir)
        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
          val errorStream = process.errorStream.bufferedReader()
          val errorText = errorStream.use { it.readText() }
          throw GradleException("Command failed: ${actualCommand}\n${errorText}")
        }
      }
    }
  }

  fun sources(vararg files : String) : Compile {
    sourceFiles.from(files)
    return this // For chaining
  }

  fun transform(t : (String) -> String) : Compile {
    transform = t
    return this // For chaining
  }

  fun command(cmd : String) : Compile {
    command = cmd
    return this // For chaining
  }
}
