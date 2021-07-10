package edu.wpi.first.gradlerio.frcvision

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import jaci.gradle.deploy.target.RemoteTarget

@CompileStatic
@InheritConstructors
abstract class FRCVisionCompatibleTarget extends RemoteTarget { }
