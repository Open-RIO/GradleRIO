package edu.wpi.first.gradlerio.test

import edu.wpi.first.gradlerio.wpi.simulation.SimulationExtension
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Internal

@CompileStatic
class NativeSimulationTask extends DefaultTask {
    void validate(List<String> binaryTasks) {
        def simTask = project.extensions.getByType(SimulationExtension).cppSimTask
        if (simTask != "") {
            this.dependsOn(simTask)
            return
        }
        if (binaryTasks.size() == 0) {
            logger.error("No Binary Simulation tasks found.");
        } else if (binaryTasks.size() == 1) {
            this.dependsOn(binaryTasks.first())
        } else {
            logger.error("More than one binary simulation task was found.")
            logger.error("Please set the `cppSimTask` variable in the `sim` block in your `build.gradle`.")
        }
    }
}
