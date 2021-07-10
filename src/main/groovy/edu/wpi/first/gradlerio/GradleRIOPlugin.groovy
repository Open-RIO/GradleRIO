package edu.wpi.first.gradlerio

import edu.wpi.first.gradlerio.caching.WrapperInspector
import edu.wpi.first.gradlerio.frc.FRCPlugin
import edu.wpi.first.gradlerio.frc.RoboRIO
import edu.wpi.first.gradlerio.ide.ClionPlugin
import edu.wpi.first.gradlerio.frcvision.FRCVisionPlugin
import edu.wpi.first.gradlerio.ide.IDEPlugin
import edu.wpi.first.gradlerio.test.TestPlugin
import edu.wpi.first.gradlerio.wpi.WPIPlugin
import groovy.transform.CompileStatic
import jaci.gradle.EmbeddedTools
import jaci.gradle.deploy.target.discovery.TargetDiscoveryTask
import jaci.gradle.log.ETLoggerFactory
import org.apache.log4j.Logger
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.execution.MultipleBuildFailures
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.nativeplatform.tasks.AbstractLinkTask
import org.gradle.util.GUtil

@CompileStatic
class GradleRIOPlugin implements Plugin<Project> {
    // Necessary to have access to project.configurations and such in the RuleSource
    class ProjectWrapper {
        Project project

        ProjectWrapper(Project project) { this.project = project }
    }

    static boolean _registered_build_finished = false;

    void apply(Project project) {
        // These configurations only act for the JAVA portion of GradleRIO
        // Native libraries have their own dependency management system
        project.configurations.maybeCreate("nativeLib")
        project.configurations.maybeCreate("nativeZip")
        
        project.configurations.maybeCreate("nativeVisionLib")
        project.configurations.maybeCreate("nativeVisionZip")

        project.configurations.maybeCreate("nativeDesktopLib")
        project.configurations.maybeCreate("nativeDesktopZip")

        project.configurations.maybeCreate("nativeRaspbianLib")
        project.configurations.maybeCreate("nativeRaspbianZip")

        project.configurations.maybeCreate("nativeAarch64BionicLib")
        project.configurations.maybeCreate("nativeAarch64BionicZip")

        project.pluginManager.apply(EmbeddedTools)
        project.pluginManager.apply(FRCPlugin)
        project.pluginManager.apply(FRCVisionPlugin)
        project.pluginManager.apply(WPIPlugin)
        project.pluginManager.apply(ClionPlugin)
        project.pluginManager.apply(IDEPlugin)
        project.pluginManager.apply(TestPlugin)

        project.extensions.add('projectWrapper', new ProjectWrapper(project))

        project.tasks.register("downloadAll", DownloadAllTask, { DownloadAllTask t ->
            t.group = "GradleRIO"
            t.description = "Download all dependencies that may be used by this project"
        } as Action<DownloadAllTask>)

        project.tasks.withType(Wrapper).configureEach { Wrapper wrapper ->
            if (!project.hasProperty('no-gradlerio-wrapper')) {
                wrapper.setDistributionPath('permwrapper/dists')
                wrapper.setArchivePath('permwrapper/dists')
            }
        }

        disableCacheCleanup(project)

        project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
            try {
                if (!project.hasProperty("skip-inspector"))
                    inspector(project)
            } catch (Exception e) {
                Logger.getLogger(this.class).info("Inspector failed: ${e.message}")
            }
            ensureSingletons(project, graph)
        }

        if (!_registered_build_finished) {
            project.gradle.buildFinished { BuildResult result ->
                _registered_build_finished = false
                if (result.failure != null) {
                    try {
                        checkBuildFailed(project, result)
                    } catch (Throwable t) {
                        // Don't fail the build if we, for some reason, screw up
                        println("Error during build failure checking: ${t.getClass()} ${t.getMessage()}")
                    }
                }
            }
        }
        _registered_build_finished = true
    }

    private static final String GRADLERIO_DISABLE_CACHE_CLEANUP_PROPERTY = "gradlerio.disable.cache.cleanup"
    private static final String CACHE_CLEANUP_PROPERTY = "org.gradle.cache.cleanup"

    // Write to ~/.gradle/gradle.properties, to disable cache cleanup. Cache cleanup
    // has the possibility to make dependencies 'go missing' if left unattended for a long time.
    // There's a chance this could happen during competition.
    void disableCacheCleanup(Project project) {
        def logger = ETLoggerFactory.INSTANCE.create("GR_CACHECLEANUP")
        if (project.findProperty(GRADLERIO_DISABLE_CACHE_CLEANUP_PROPERTY) == "false") {
            logger.logErrorHead("Warning! You have the property gradlerio.disable.cache.cleanup set to false")
            logger.logError("This can cause issues when going to competition, since this means your dependencies can be deleted unwillingly after a month.")
            logger.logError("Remove this from your gradle.properties file unless you know what you're doing.")
            logger.logError("Note, this may result in you not being able to deploy code at competition")
            return
        }

        try {
            // TODO: Issue #6084 on gradle/gradle, this may not work in 5.1 or all use cases
            def gradleProperties = new File("${project.gradle.gradleUserHomeDir}/gradle.properties")
            if (gradleProperties.isFile()) {
                Properties props = GUtil.loadProperties(gradleProperties)
                String cleanup = props.getProperty(CACHE_CLEANUP_PROPERTY)
                if (cleanup == null || !cleanup.equals("false")) {
                    logger.info("Disabling Gradle auto cache cleanup...")
                    props.setProperty(CACHE_CLEANUP_PROPERTY, "false")
                    logger.info("Saving gradle.properties...")
                    GUtil.saveProperties(props, gradleProperties)
                    logger.info("Done!")
                }
            }
        } catch (e) {
            logger.logError("Could not disable gradle cache cleanup. Run with --info for more information.")
            logger.info("${e.class}: ${e.message}")
            logger.info(e.stackTrace.join("\n"))
        }
    }

    void inspector(Project project) {
        def logger = ETLoggerFactory.INSTANCE.create("GR_INSPECTOR")
        project.allprojects.each { Project proj ->
            if (!project.hasProperty("skip-inspector-${WrapperInspector.NAME}")) {
                logger.info("Running ${WrapperInspector.NAME} inspector on project ${project.path}")
                WrapperInspector.run(project, logger)
            }
        }
    }

    void ensureSingletons(Project project, TaskExecutionGraph graph) {
        def allTasks = graph.getAllTasks()
        def visited = [] as Set<String>

        // Go in reverse - only use the latest version in the task graph (not earliest)
        allTasks.reverseEach { Task t ->
            if (t instanceof SingletonTask) {
                String singletonName = (t as SingletonTask).singletonName()
                if (visited.add(singletonName)) {
                    Logger.getLogger(this.class).info("Singleton Task Using: ${t} for ${singletonName}")
                } else {
                    Logger.getLogger(this.class).info("Singleton Task Disabling: ${t} for ${singletonName}")
                    t.setEnabled(false)
                }
            }
        }
    }

    void checkBuildFailed(Project project, BuildResult result) {
        def logger = ETLoggerFactory.INSTANCE.create("GR_BUILDFAILED_CHECKER")

        // Check if a we've failed due to a dependency error. If so, emit a warning that you require an
        // internet connection.

        def rootExceptions = [] as List<? extends Throwable>
        def exceptions = [] as List<? extends Throwable>

        if (result.failure instanceof MultipleBuildFailures) {
            ((MultipleBuildFailures)result.failure).causes.each { Throwable t ->
                rootExceptions.add(t)
            }
        } else {
            rootExceptions.add(result.failure)
        }

        rootExceptions.each { Throwable root ->
            def ex = root
            while (ex != null) {
                if (!exceptions.contains(ex))
                    exceptions.add(ex)
                logger.info("Build Exception: ${ex.getClass()} -> ${ex.getMessage()}")
                ex = ex.cause
            }
        }

        // An array of string hashcodes makes sure we don't "overprint" errors.
        def reasons = [] as Set<Integer>

        exceptions.each { Throwable t ->
            if (t instanceof ArtifactResolveException) {
                if (reasons.add("ArtifactResolve".hashCode())) {
                    // Encourage user to run downloadAll task to prepare dependencies.
                    // ./gradlew deploy -PdeployDry will also work, but for safety we should encourage all downloads
                    // in case requirements change at competition.
                    logger.logErrorHead("Dependency Error!")
                    logger.logError("GradleRIO detected this build failed due to missing dependencies!")
                    logger.logError("Try again with `./gradlew downloadAll` whilst connected to the internet (not the robot!)")
                    logger.logError("If the error persists, ensure you are not behind a firewall / proxy server (common in schools)")
                }
            }

            if (t instanceof TaskExecutionException) {
                TaskExecutionException tee = (TaskExecutionException) t
                def task = tee.task
                logger.info("Exception Task: ${task.getClass()} -> ${task.getName()}")

                if (task instanceof TargetDiscoveryTask) {
                    def target = ((TargetDiscoveryTask)task).target
                    if (reasons.add("Target${target.name}".hashCode())) {
                        logger.logErrorHead("Missing Target!")
                        if (target instanceof RoboRIO) {
                            logger.logErrorHead("=============================================")
                            logger.logErrorHead("Are you connected to the robot, and is it on?")
                            logger.logErrorHead("=============================================")
                        }
                        logger.logError("GradleRIO detected this build failed due to not being able to find \"${target.name}\"!")
                        logger.logError("Scroll up in this error log for more information.")
                    }
                } else if (task instanceof AbstractNativeCompileTask || task instanceof AbstractCompile) {
                    def reasonID = task.name
                    if (task instanceof AbstractNativeCompileTask) {
                        def typedTask = (AbstractNativeCompileTask)task
                        def indexOfPlatform = reasonID.indexOf(typedTask.targetPlatform.get().name.capitalize())
                        reasonID = reasonID.substring(0, indexOfPlatform < 0 ? reasonID.length() : indexOfPlatform)
                        logger.info("ReasonID: ${reasonID}")
                    }

                    if (reasons.add("Compile${reasonID}".hashCode())) {
                        logger.logErrorHead("Compilation Error!")
                        logger.logError("GradleRIO detected this build failed due to a Compile Error (${reasonID}).")
                        logger.logError("Check that all your files are saved, then scroll up in this log for more information.")
                    }
                } else if (task instanceof AbstractLinkTask) {
                    def reasonID = task.name

                    def typedTask = (AbstractLinkTask)task
                    def indexOfPlatform = reasonID.indexOf(typedTask.targetPlatform.get().name.capitalize())
                    reasonID = reasonID.substring(0, indexOfPlatform < 0 ? reasonID.length() : indexOfPlatform)
                    logger.info("ReasonID: ${reasonID}")

                    if (reasons.add("Link${reasonID}".hashCode())) {
                        logger.logErrorHead("Linker Error!")
                        logger.logError("GradleRIO detected this build failed due to a Linker Error (${reasonID}).")
                        logger.logError("Check that all your files are saved, then scroll up in this log for more information.")
                    }
                }
            }
        }
    }

    static Closure javaManifest(String robotMainClass) {
        return { DefaultManifest mf ->
            mf.attributes 'Main-Class': robotMainClass
        }
    }

    // ~/.gradle
    static File getGlobalDirectory() {
        return new File("${System.getProperty('user.home')}/.gradle", "gradlerio")
    }
}
