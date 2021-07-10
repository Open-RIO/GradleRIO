package edu.wpi.first.gradlerio.frcvision

import groovy.transform.CompileStatic
import jaci.gradle.EmbeddedTools
import jaci.gradle.deploy.DeployExtension
import jaci.gradle.deploy.artifact.Artifact
import jaci.gradle.ActionWrapper
import jaci.gradle.deploy.artifact.ArtifactDeployTask
import jaci.gradle.deploy.artifact.CommandArtifact
import jaci.gradle.deploy.artifact.JavaArtifact
import jaci.gradle.deploy.context.DeployContext
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.model.ModelMap
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.platform.base.BinaryTasks
import edu.wpi.first.gradlerio.frc.ConfigurationArtifact

import java.util.function.Function

@CompileStatic
class FRCVisionPlugin implements Plugin<Project> {

    Project project

    public static final String LIB_DEPLOY_DIR = '/usr/local/frc/third-party/lib'

    @Override
    void apply(Project project) {
        this.project = project
        project.pluginManager.apply(EmbeddedTools)

        project.afterEvaluate {
            addNativeLibraryArtifacts(project)
        }

        def deployExtension = project.extensions.getByType(DeployExtension)
        def artifactExtensionAware = deployExtension.artifacts as ExtensionAware
        def targetExtensionAware = deployExtension.targets as ExtensionAware
        def artifactExtension = deployExtension.artifacts
        def targetExtension = deployExtension.targets

        artifactExtensionAware.extensions.add('frcvisionJavaArtifact', { String name, Closure closure->
            return artifactExtension.artifact(name, FRCVisionJavaArtifact, new ActionWrapper(closure))
        })

        artifactExtensionAware.extensions.add('frcvisionNativeArtifact', { String name, Closure closure->
            return artifactExtension.artifact(name, FRCVisionNativeArtifact, new ActionWrapper(closure))
        })

        artifactExtensionAware.extensions.add('frcvisionNativeLibraryArtifact', { String name, Closure closure->
            return artifactExtension.artifact(name, FRCVisionNativeLibraryArtifact, new ActionWrapper(closure))
        })

        targetExtensionAware.extensions.add('pi', { String name, Closure closure ->
            targetExtension.target(name, Pi, new ActionWrapper(closure))
        })

        targetExtensionAware.extensions.add('frcvisionCompatibleTarget', { String name, Closure closure ->
            targetExtension.target(name, FRCVisionCompatibleTarget, new ActionWrapper(closure))
        })
    }

    public static void ownDirectory(DeployContext ctx, String directory) {
        ctx.execute("sudo chmod -R 777 \"$directory\" || true; sudo chown -R pi:pi \"$directory\"")
    }

    public static DeployExtension deployExtension(Project project) {
        return project.extensions.getByType(DeployExtension)
    }

    public static void allFrcVisionTargets(DeployExtension ext, Artifact artifact) {
        ext.targets.withType(FRCVisionCompatibleTarget).all { Pi r ->
            artifact.targets << r.name
        }
    }

    void addNativeLibraryArtifacts(Project project) {
        // Note: These include JNI. Actual native c/c++ is done through EmbeddedTools
        def nativeLibs = project.configurations.getByName('nativeVisionLib')
        def nativeZips = project.configurations.getByName('nativeVisionZip')

        def dext = deployExtension(project)
        dext.artifacts.artifact('nativeVisionLibs', ConfigurationArtifact) { ConfigurationArtifact artifact ->
            allFrcVisionTargets(dext, artifact)
            artifact.configuration = nativeLibs
            artifact.zipped = false
        }

        dext.artifacts.artifact('nativeVisionZip', ConfigurationArtifact) { ConfigurationArtifact artifact ->
            allFrcVisionTargets(dext, artifact)
            artifact.configuration = nativeZips
            artifact.zipped = true
            artifact.filter = { PatternFilterable pat ->
                pat.include('*.so*', 'lib/*.so', 'java/lib/*.so', 'linux/raspbian/shared/*.so', 'linuxraspbian/**/*.so', '**/libopencv*.so.*')
            }
        }
    }

    static class FRCVisionRules extends RuleSource {
        @BinaryTasks
        void createNativeLibraryDeployTasks(final ModelMap<Task> tasks, final ExtensionContainer ext, final NativeBinarySpec binary) {
            def deployExt = ext.getByType(DeployExtension)
            def artifacts = deployExt.artifacts

            deployExt.artifacts
                    .withType(FRCVisionNativeArtifact)
                    .matching { FRCVisionNativeArtifact art ->
                        art.component.equalsIgnoreCase(binary.component.name) &&
                        art.targetPlatform.equalsIgnoreCase(binary.targetPlatform.name)
                    }
                    .all { FRCVisionNativeArtifact art ->
                        art._bin = binary
                    }
        }
    }

}
