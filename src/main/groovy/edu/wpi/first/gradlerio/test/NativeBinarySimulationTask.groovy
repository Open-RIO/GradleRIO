package edu.wpi.first.gradlerio.test

import edu.wpi.first.gradlerio.ExternalLaunchTask
import edu.wpi.first.gradlerio.test.TestPlugin
import groovy.transform.CompileStatic
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.tasks.InstallExecutable

@CompileStatic
class NativeBinarySimulationTask extends ExternalLaunchTask {
    @Internal
    NativeExecutableBinarySpec binary

    @TaskAction
    void run() {
        def installTask = binary.tasks.withType(InstallExecutable).first()
        def dir = new File(installTask.installDirectory.asFile.get(), "lib")

        environment.putAll(TestPlugin.getSimLaunchEnv(project, dir.absolutePath))

        workingDir = dir
        launch("\"${installTask.runScriptFile.get().asFile.absolutePath}\"")
    }

}
