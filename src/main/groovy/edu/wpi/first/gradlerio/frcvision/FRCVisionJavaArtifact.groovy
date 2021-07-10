package edu.wpi.first.gradlerio.frcvision

import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import javax.inject.Inject
import jaci.gradle.PathUtils
import jaci.gradle.ActionWrapper
import jaci.gradle.deploy.artifact.JavaArtifact
import jaci.gradle.deploy.context.DeployContext
import jaci.gradle.deploy.sessions.IPSessionController
import org.gradle.api.Project

@CompileStatic
class FRCVisionJavaArtifact extends JavaArtifact {

    @Inject
    FRCVisionJavaArtifact(String name, Project project) {
        super(name, project)
        setJar('jar')

        predeploy << new ActionWrapper({ DeployContext ctx ->
            ctx.execute("rw ; sudo svc -d /service/camera")
        })

        postdeploy << new ActionWrapper({ DeployContext ctx ->
            def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
            ctx.execute("chmod +x /home/pi/runCamera")
            ctx.execute("chmod +x \"${binFile}\"")
            ctx.execute("sync")
            ctx.execute("ldconfig")
            ctx.execute("ro")
            ctx.execute("/home/pi/runService")
        });
    }

    List<String> jvmArgs = []
    List<String> arguments = []
    boolean debug = false
    int debugPort = 8349
    String debugFlags = "-XX:+UsePerfData -agentlib:jdwp=transport=dt_socket,address=0.0.0.0:${debugPort},server=y,suspend=y"

    def runCommand = {
        "!/bin/sh\necho \"Waiting 5 seconds...\"\nsleep 5\nexec env LD_LIBRARY_PATH=${FRCVisionPlugin.LIB_DEPLOY_DIR} java -Djava.library.path=${FRCVisionPlugin.LIB_DEPLOY_DIR} ${jvmArgs.join(" ")} ${debug ? debugFlags : ""} -jar \"<<BINARY>>\" ${arguments.join(" ")}"
    }

    @Override
    void deploy(DeployContext ctx) {
        super.deploy(ctx)

        if (runCommand) {
            String rCmd = null
            if (runCommand instanceof Closure)
                rCmd = (runCommand as Closure).call([ctx, this]).toString()
            else if (runCommand instanceof String)
                rCmd = (runCommand as String)

            if (rCmd != null) {
                def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
                rCmd = rCmd.replace('<<BINARY>>', binFile)
                ctx.execute("echo '${rCmd}' > /home/pi/runCamera")
            }
        }
        def conffile = new File(project.buildDir, "debug/${name}_${ctx.deployLocation.target.name}.debugconfig")

        if (debug) {
            conffile.parentFile.mkdirs()

            ctx.logger.withLock {
                ctx.logger.log("====================================================================")
                ctx.logger.log("DEBUGGING ACTIVE ON PORT ${debugPort}!")
                ctx.logger.log("====================================================================")
            }

            if (ctx.controller instanceof IPSessionController) {
                def ip = (IPSessionController)ctx.controller
                def target = ip.getHost() + ":" + debugPort
                def dbcfg = [
                        target   : target,
                        ipAddress: ip.getHost(),
                        port     : debugPort
                ]

                def gbuilder = new GsonBuilder()
                gbuilder.setPrettyPrinting()
                conffile.text = gbuilder.create().toJson(dbcfg)
            } else {
                ctx.logger.log("Session Controller isn't IP Compatible. No debug file written.")
            }
        } else {
            // Not debug, remove debug files
            if (conffile.exists()) conffile.delete()
        }
    }
}
