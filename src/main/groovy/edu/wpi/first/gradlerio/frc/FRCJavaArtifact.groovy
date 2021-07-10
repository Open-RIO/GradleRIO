package edu.wpi.first.gradlerio.frc

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
class FRCJavaArtifact extends JavaArtifact {

    @Inject
    FRCJavaArtifact(String name, Project project) {
        super(name, project)
        setJar('jar')

        predeploy << new ActionWrapper({ DeployContext ctx ->
            ctx.execute(". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t 2> /dev/null")
        })

        postdeploy << new ActionWrapper({ DeployContext ctx ->
            def binFile = PathUtils.combine(ctx.workingDir, filename ?: file.get().name)
            ctx.execute("chmod +x /home/lvuser/robotCommand; chown lvuser /home/lvuser/robotCommand")
            ctx.execute("chmod +x \"${binFile}\"; chown lvuser \"${binFile}\"")
            ctx.execute("sync")
            ctx.execute(". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t -r 2> /dev/null")
        });
    }

    List<String> jvmArgs = []
    List<String> arguments = []
    boolean debug = false
    int debugPort = 8349
    String debugFlags = "-XX:+UsePerfData -agentlib:jdwp=transport=dt_socket,address=0.0.0.0:${debugPort},server=y,suspend=y"

    def robotCommand = {
        "/usr/local/frc/JRE/bin/java -XX:+UseConcMarkSweepGC -Djava.library.path=${FRCPlugin.LIB_DEPLOY_DIR} -Djava.lang.invoke.stringConcat=BC_SB ${jvmArgs.join(" ")} ${debug ? debugFlags : ""} -jar \"<<BINARY>>\" ${arguments.join(" ")}"
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
                ctx.execute("echo 'exec ${rCmd}' > /home/lvuser/robotCommand")
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
