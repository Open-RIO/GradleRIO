package jaci.openrio.gradle.wpi.toolchain

import groovy.transform.CompileStatic
import jaci.openrio.gradle.GradleRIOPlugin
import jaci.openrio.gradle.wpi.toolchain.install.*
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.tasks.AbstractLinkTask
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.platform.base.BinaryTasks
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.ExecSpec

import java.nio.file.Paths

@CompileStatic
class WPIToolchainPlugin implements Plugin<Project> {
    static List<AbstractToolchainInstaller> toolchainInstallers = [
        new WindowsToolchainInstaller(),
        new MacOSToolchainInstaller(),
        new LinuxToolchainInstaller()
    ]

    @Override
    void apply(Project project) {
        def rootInstallTask = project.task("installToolchain") { Task task ->
            task.group = "GradleRIO"
            task.description = "Install the C++ FRC Toolchain for this system"

            task.doLast {
                AbstractToolchainInstaller installer = getActiveInstaller()

                if (installer == null) {
                    throw new NoToolchainInstallersException("Cannot install Toolchain! (No Installers for this Platform)")
                } else {
                    installer.installToolchain(task.project)
                }
            }
        }
    }

    public static AbstractToolchainInstaller getActiveInstaller() {
        def toolchains = toolchainInstallers.findAll { t ->
            return t.installable()
        }
        if (toolchains.empty) return null;
        return toolchains.first()
    }

    public static File getSysroot() {
        return getActiveInstaller().sysrootLocation()
    }

    public static URL toolchainDownloadURL(String file) {
        return new URL("http://first.wpi.edu/FRC/roborio/toolchains/${file}")
    }

    public static File toolchainDownloadDirectory() {
        return new File(GradleRIOPlugin.globalDirectory, "cache/toolchains/download")
    }

    public static File toolchainExtractDirectory() {
        return new File(GradleRIOPlugin.globalDirectory, "cache/toolchains/extract")
    }

    public static File toolchainInstallDirectory() {
        return new File(GradleRIOPlugin.globalDirectory, "toolchains")
    }

    static class WPIToolchainRules extends RuleSource {
        @Mutate
        void addToolchains(NativeToolChainRegistryInternal toolChainRegistry, ServiceRegistry serviceRegistry) {
//            def fileResolver = serviceRegistry.get(FileResolver.class)
//            def execActionFactory = serviceRegistry.get(ExecActionFactory.class)
//            def compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory.class)
//            def instantiator = serviceRegistry.get(Instantiator.class)
//            def buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor.class)
//            def metaDataProviderFactory = serviceRegistry.get(CompilerMetaDataProviderFactory.class)
//            def workerLeaseService = serviceRegistry.get(WorkerLeaseService.class)
//
//            toolChainRegistry.registerFactory(WPIRoboRioGcc.class, { String name ->
//                return instantiator.newInstance(WPIRoboRioGcc.class, instantiator, name, buildOperationExecutor, OperatingSystem.current(), fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory, workerLeaseService)
//            })
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final ExecActionFactory execActionFactory = serviceRegistry.get(ExecActionFactory.class);
            final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final BuildOperationExecutor buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor.class);
            final CompilerMetaDataProviderFactory metaDataProviderFactory = serviceRegistry.get(CompilerMetaDataProviderFactory.class);
            final WorkerLeaseService workerLeaseService = serviceRegistry.get(WorkerLeaseService.class);
            final SystemLibraryDiscovery standardLibraryDiscovery = serviceRegistry.get(SystemLibraryDiscovery.class);

            toolChainRegistry.registerFactory(WPIRoboRioGcc.class, new NamedDomainObjectFactory<WPIRoboRioGcc>() {
                public WPIRoboRioGcc create(String name) {
                    return instantiator.newInstance(WPIRoboRioGcc.class, instantiator, name, buildOperationExecutor, OperatingSystem.current(), fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory, workerLeaseService, standardLibraryDiscovery);
                }
            });
            toolChainRegistry.registerDefaultToolChain('roborioGcc', WPIRoboRioGcc)
        }

        @BinaryTasks
        void createNativeStripTasks(final ModelMap<Task> tasks, final ProjectLayout projectLayout, final NativeBinarySpec binary) {
            def project = (Project) projectLayout.projectIdentifier
            WPIRoboRioGcc gcc = null
            if (binary.toolChain instanceof WPIRoboRioGcc) {
                gcc = (WPIRoboRioGcc)binary.toolChain
            } else {
                return
            }
            Task rawLinkTask = null
            if (binary instanceof SharedLibraryBinarySpec) {
                rawLinkTask = ((SharedLibraryBinarySpec)binary).tasks.link
            } else if (binary instanceof NativeExecutableBinarySpec) {
                rawLinkTask = ((NativeExecutableBinarySpec)binary).tasks.link
            }
            if (!(rawLinkTask instanceof AbstractLinkTask)) {
                return
            }
            AbstractLinkTask linkTask = (AbstractLinkTask)rawLinkTask

            linkTask.doLast {
                def mainFile = linkTask.linkedFile.get().asFile

                if (mainFile.exists()) {
                    def mainFileStr = mainFile.toString()
                    def debugFile = mainFileStr + '.debug'

                    def path = Paths.get(WPIToolchainPlugin.getSysroot().toString(), 'bin', gcc.prefix).toString()

                    project.exec { ExecSpec ex ->
                        ex.commandLine path + 'objcopy', '--only-keep-debug', mainFileStr, debugFile
                    }
                    project.exec { ExecSpec ex ->
                        ex.commandLine path + 'strip', '-g', mainFileStr
                    }
                    project.exec { ExecSpec ex ->
                        ex.commandLine path + 'objcopy', "--add-gnu-debuglink=$debugFile", mainFileStr
                    }
                }
            }
        }
    }
}
