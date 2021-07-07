package edu.wpi.first.gradlerio.test

import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import jaci.gradle.nativedeps.DelegatedDependencySet
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.Clang
import edu.wpi.first.gradlerio.wpi.simulation.SimulationExtension

import java.nio.file.Paths

@CompileStatic
class NativeExternalSimulationTask extends ExternalSimulationTask {
    @Internal
    List<NativeExecutableBinarySpec> exeBinaries = []
    @Internal
    List<NativeTestSuiteBinarySpec> testBinaries = []

    @OutputFile
    File outfile = new File(project.rootProject.buildDir, "${ExternalSimulationMergeTask.CONTAINER_FOLDER}/${project.name}_native.json")

    @TaskAction
    void create() {
        def cfgs = []
        SimulationExtension simExtension = project.extensions.getByType(SimulationExtension)
        def extensions = TestPlugin.getHALExtensions(project)
        for (NativeExecutableBinarySpec binary : exeBinaries) {
            def cfg = [:]
            def installTask = (InstallExecutable)binary.tasks.install
            cfg['name'] = "${binary.component.name} (in project ${project.name})".toString()
            cfg['extensions'] = extensions
            cfg['launchfile'] = Paths.get(installTask.installDirectory.asFile.get().toString(), 'lib', installTask.executableFile.asFile.get().name).toString()
            cfg['clang'] = binary.toolChain in Clang
            cfg['env'] = simExtension.environment

            def srcpaths = []
            def headerpaths = []
            def libpaths = []
            def libsrcpaths = []
            def debugpaths = []
            binary.inputs.withType(HeaderExportingSourceSet) { HeaderExportingSourceSet ss ->
                srcpaths += ss.source.srcDirs
                srcpaths += ss.exportedHeaders.srcDirs
            }
            binary.libs.each { NativeDependencySet ds ->
                headerpaths += ds.includeRoots
                libpaths += ds.runtimeFiles.files
                if (ds instanceof DelegatedDependencySet) {
                    libsrcpaths += (ds as DelegatedDependencySet).getSourceFiles()
                    debugpaths += (ds as DelegatedDependencySet).getDebugFiles()
                }
            }

            cfg['srcpaths'] = (srcpaths as List<File>).collect { it.absolutePath }
            cfg['headerpaths'] = (headerpaths as List<File>).collect { it.absolutePath }
            cfg['libpaths'] = (libpaths as List<File>).collect { it.absolutePath }
            cfg['debugpaths'] = (debugpaths as List<File>).collect { it.absolutePath }
            cfg['libsrcpaths'] = (libsrcpaths as List<File>).collect { it.absolutePath }

            cfgs << cfg
        }

        for (NativeTestSuiteBinarySpec binary : testBinaries) {
            def cfg = [:]
            def installTask = (InstallExecutable)binary.tasks.install
            cfg['name'] = "${binary.component.name} (in project ${project.name}, test suite)".toString()
            cfg['extensions'] = extensions
            cfg['launchfile'] = Paths.get(installTask.installDirectory.asFile.get().toString(), 'lib', installTask.executableFile.asFile.get().name).toString()
            cfg['clang'] = binary.toolChain in Clang
            cfg['env'] = simExtension.environment

            def srcpaths = []
            def headerpaths = []
            def libpaths = []
            def libsrcpaths = []
            def debugpaths = []
            binary.inputs.withType(HeaderExportingSourceSet) { HeaderExportingSourceSet ss ->
                srcpaths += ss.source.srcDirs
                srcpaths += ss.exportedHeaders.srcDirs
            }
            binary.libs.each { NativeDependencySet ds ->
                headerpaths += ds.includeRoots
                libpaths += ds.runtimeFiles.files
                if (ds instanceof DelegatedDependencySet) {
                    libsrcpaths += (ds as DelegatedDependencySet).getSourceFiles()
                    debugpaths += (ds as DelegatedDependencySet).getDebugFiles()
                }
            }

            cfg['srcpaths'] = (srcpaths as List<File>).collect { it.absolutePath }
            cfg['headerpaths'] = (headerpaths as List<File>).collect { it.absolutePath }
            cfg['libpaths'] = (libpaths as List<File>).collect { it.absolutePath }
            cfg['debugpaths'] = (debugpaths as List<File>).collect { it.absolutePath }
            cfg['libsrcpaths'] = (libsrcpaths as List<File>).collect { it.absolutePath }

            cfgs << cfg
        }

        def gbuilder = new GsonBuilder()
        gbuilder.setPrettyPrinting()
        def json = gbuilder.create().toJson(cfgs)

        outfile.parentFile.mkdirs()
        outfile.text = json
    }

    @Input
    boolean includeTestBinaries = true

}
