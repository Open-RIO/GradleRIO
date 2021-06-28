package edu.wpi.first.gradlerio.frc

import com.google.gson.GsonBuilder
import edu.wpi.first.toolchain.NativePlatforms
import edu.wpi.first.toolchain.ToolchainExtension
import edu.wpi.first.toolchain.roborio.RoboRioToolchainPlugin
import groovy.transform.CompileStatic
import jaci.gradle.PathUtils
import javax.inject.Inject
import jaci.gradle.ActionWrapper
import jaci.gradle.deploy.artifact.BinaryLibraryArtifact
import jaci.gradle.deploy.artifact.NativeArtifact
import jaci.gradle.deploy.context.DeployContext
import jaci.gradle.deploy.sessions.IPSessionController
import jaci.gradle.nativedeps.DelegatedDependencySet
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.process.ExecSpec

import java.nio.file.Paths

@CompileStatic
class FRCNativeArtifact extends NativeArtifact {

    @Inject
    FRCNativeArtifact(String name, Project project) {
        super(name, project)
        targetPlatform = NativePlatforms.roborio

        predeploy << new ActionWrapper({ DeployContext ctx ->
            def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
            ctx.execute(". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t 2> /dev/null")
            ctx.execute("rm -f \"${binFile}\"")
        })

        postdeploy << new ActionWrapper({ DeployContext ctx ->
            def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
            ctx.execute("chmod +x /home/lvuser/robotCommand; chown lvuser /home/lvuser/robotCommand")
            ctx.execute("chmod +x \"${binFile}\"; chown lvuser \"${binFile}\"")
            // Make user program setuid admin. This lets the user program set RT
            // thread priorities after it calls setuid(0).
            ctx.execute("chown admin \"${binFile}\"")
            ctx.execute("chmod u+s \"${binFile}\"")
            ctx.execute("sync")
            ctx.execute("ldconfig")
            ctx.execute(". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t -r 2> /dev/null")
        })

        buildType = '<<GR_AUTO>>'
    }

    List<String> arguments = []
    boolean debug = false
    int debugPort = 8348

    def robotCommand = { DeployContext ctx, FRCNativeArtifact self ->
        "${self.debug ? "gdbserver host:${self.debugPort}" : ''} \"<<BINARY>>\" ${self.arguments.join(" ")}"
    }

    NativeBinarySpec _bin

    @Override
    String getBuildType() {
        def sup = super.getBuildType()
        if (!sup.equals('<<GR_AUTO>>'))
            return sup

        return debug ? 'debug' : 'release'
    }

    @Override
    void configureLibsArtifact(BinaryLibraryArtifact bla) {
        super.configureLibsArtifact(bla)
        bla.setDirectory(FRCPlugin.LIB_DEPLOY_DIR)
        bla.postdeploy << new ActionWrapper({ DeployContext ctx ->
            FRCPlugin.ownDirectory(ctx, FRCPlugin.LIB_DEPLOY_DIR)
            ctx.execute('ldconfig')
        })
    }

    @Override
    void deploy(DeployContext ctx) {
        super.deploy(ctx)

        if (robotCommand) {
            String rCmd = null
            if (robotCommand instanceof Closure)
                rCmd = (robotCommand as Closure).call([ctx, this]).toString()
            else if (robotCommand instanceof String)
                rCmd = (robotCommand as String)

            if (rCmd != null) {
                def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
                rCmd = rCmd.replace('<<BINARY>>', binFile)
                ctx.execute("echo '${rCmd}' > /home/lvuser/robotCommand")
            }
        }
        def isWin = OperatingSystem.current().isWindows()

        def filebasename = "${name}_${ctx.deployLocation.target.name}"
        def conffile = new File(project.buildDir, "debug/${filebasename}.debugconfig")
        def gdbfile = new File(project.buildDir, "debug/${filebasename}.gdbcommands")
        def cmdfile = new File(project.buildDir, "debug/${filebasename}${isWin ? ".bat" : ""}")

        if (debug) {
            conffile.parentFile.mkdirs()

            ctx.logger.withLock {
                ctx.logger.log("====================================================================")
                ctx.logger.log("DEBUGGING ACTIVE ON PORT ${debugPort}!")
                ctx.logger.log("Launch debugger with ${isWin ? "" : "./"}${Paths.get(project.rootDir.toURI()).relativize(Paths.get(cmdfile.toURI())).toString()}")
                ctx.logger.log("====================================================================")
            }

            // Setup

            def srcpaths = []
            def headerpaths = []
            def libpaths = []
            def debugpaths = []
            def libsrcpaths = []

            _bin.inputs.withType(HeaderExportingSourceSet) { HeaderExportingSourceSet ss ->
                srcpaths += ss.source.srcDirs
                srcpaths += ss.exportedHeaders.srcDirs
            }
            _bin.libs.each { NativeDependencySet ds ->
                headerpaths += ds.includeRoots
                libpaths += ds.runtimeFiles.files
                if (ds instanceof DelegatedDependencySet) {
                    libsrcpaths += (ds as DelegatedDependencySet).getSourceFiles()
                    debugpaths += (ds as DelegatedDependencySet).getDebugFiles()
                }
            }

            if (ctx.controller instanceof IPSessionController) {
                def ip = (IPSessionController)ctx.controller
                def filepath = file.get().absolutePath.replaceAll("\\\\", "/")
                def target = ip.host + ":" + debugPort
//                def toolchainD = project.plugins.getPlugin(WPIToolchainPlugin.class).discoverRoborioToolchain()

                def toolchainD = project.extensions.getByType(ToolchainExtension).toolchainDescriptors.getByName(RoboRioToolchainPlugin.toolchainName).discover()
                def gdbpath = toolchainD.gdbFile().get().absolutePath
                def sysroot = toolchainD.sysroot().orElse(null).absolutePath

                // .debugconfig

                def dbcfg = [
                        launchfile : filepath,
                        target     : target,
                        gdb        : gdbpath,
                        sysroot    : sysroot,
                        srcpaths   : (srcpaths as List<File>).collect { it.absolutePath },
                        headerpaths: (headerpaths as List<File>).collect { it.absolutePath },
                        libpaths   : (libpaths as List<File>).collect { it.absolutePath },
                        debugpaths : (debugpaths as List<File>).collect {it.absolutePath },
                        libsrcpaths: (libsrcpaths as List<File>).collect { it.absolutePath },
                        arch       : "elf32-littlearm",
                        component  : this.component
                ]

                def gbuilder = new GsonBuilder()
                gbuilder.setPrettyPrinting()
                conffile.text = gbuilder.create().toJson(dbcfg)

                // .gdbcommands

                def init_commands = [
                        'set gnutarget elf32-littlearm',
                        "file ${filepath}".toString(),
                        "target remote ${target}".toString()
                ] as List<String>

                if (sysroot != null) init_commands += ["set sysroot \"${sysroot}\""] as List<String>
                init_commands += srcpaths.collect { "dir \"${it}\"" } as List<String>
                init_commands += headerpaths.collect { "dir \"${it}\"" } as List<String>

                def cmdline = [
                        gdbpath, "-ix=\"${gdbfile.absolutePath.replaceAll("\\\\", "/")}\""
                ]

                gdbfile.text = init_commands.join('\n')
                cmdfile.text = cmdline.join(" ")

                if (OperatingSystem.current().isUnix()) {
                    project.exec { ExecSpec spec ->
                        spec.commandLine "chmod"
                        spec.args("0755", cmdfile.absolutePath)
                    }
                }
            } else {
                ctx.logger.log("Session Controller isn't IP Compatible. No debug file written.")
            }
        } else {
            // Not debug, clear debug files
            if (conffile.exists()) conffile.delete()
            if (gdbfile.exists()) gdbfile.delete()
            if (cmdfile.exists()) cmdfile.delete()
        }
    }
}
