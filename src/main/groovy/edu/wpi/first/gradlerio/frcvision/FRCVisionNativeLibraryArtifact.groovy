package edu.wpi.first.gradlerio.frcvision

import edu.wpi.first.toolchain.NativePlatforms
import groovy.transform.CompileStatic
import jaci.gradle.ActionWrapper
import javax.inject.Inject
import jaci.gradle.deploy.artifact.NativeArtifact
import jaci.gradle.deploy.context.DeployContext
import org.gradle.api.Project

@CompileStatic
class FRCVisionNativeLibraryArtifact extends NativeArtifact {

  @Inject
  FRCVisionNativeLibraryArtifact(String name, Project project) {
    super(name, project)
    targetPlatform = NativePlatforms.raspbian

    directory = FRCVisionPlugin.LIB_DEPLOY_DIR

    postdeploy << new ActionWrapper({ DeployContext ctx ->
      FRCVisionPlugin.ownDirectory(ctx, FRCVisionPlugin.LIB_DEPLOY_DIR)
      ctx.execute("ldconfig")
    })
  }

}
