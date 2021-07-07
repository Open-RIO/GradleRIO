package edu.wpi.first.gradlerio.wpi.simulation

import groovy.transform.CompileStatic
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.NamedDomainObjectContainer

@CompileStatic
class SimulationExtension {
    Map<String, String> environment = [:]
    String cppSimTask = ""

    void envVar(String name, String value) {
        environment[name] = value
    }


    private NamedDomainObjectContainer<HalSimExtension> extensions

    @Inject
    SimulationExtension(Project project) {
        extensions = project.objects.domainObjectContainer(HalSimExtension)
    }
}
