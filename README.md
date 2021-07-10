![CI](https://github.com/wpilibsuite/GradleRIO/workflows/CI/badge.svg)

# GradleRIO
GradleRIO is a powerful Gradle Plugin that allows teams competing in the FIRST
robotics competition to produce and build their code.

![](img/tty.gif)

GradleRIO works with Java and C++ (and others!), on Windows, Mac and Linux. GradleRIO automatically fetches WPILib, Tools, and Vendor Libraries.

For 2019+, GradleRIO is the official build system for the _FIRST_ Robotics Competition! The officially supported IDE is Visual Studio Code (VS Code), using the [WPILib Extension](https://github.com/wpilibsuite/vscode-wpilib).

frc-docs is the best place for documentation: https://docs.wpilib.org/en/stable/

Other IDEs like IntelliJ IDEA, Eclipse, Visual Studio, and CLion are also supported, unofficially. You may also use this tool exclusively from the command line, allowing use of any IDE or text editor (like Sublime Text, Atom or Vim).

## Getting Started - Creating a new project
### With Visual Studio Code (recommended)
For getting started with VS Code, please see the frc-docs documentation:
https://docs.wpilib.org/en/stable/docs/zero-to-robot/introduction.html
### Without Visual Studio Code
Follow the installation instructions on frc-docs: https://docs.wpilib.org/en/stable/docs/zero-to-robot/step-2/wpilib-setup.html
_Note that the offline installer isn't required, but will save you a ton of time and is highly recommended. You can deselect the option of VS Code if you wish._

**WPILibUtility Standalone Project Builder**
WPILib provides a standalone project builder that provides the same interface as VS Code, without having to use VS Code.

If you've used the installer, find and run `wpilibutility` in `C:\Users\Public\wpilib\2021\utility` (windows), or `~/wpilib/2021/utility`(mac/linux). Note that mac users will have to extract the .tar.gz file, then run.
Alternatively, download it from the VSCode-WPILib releases, extract it, and run it: https://github.com/wpilibsuite/vscode-wpilib/releases

Use the WPILib Utility whenever you want to create a new project.

**GradleRIO Example Project**
Go to the latest release on GitHub: https://github.com/wpilibsuite/GradleRIO/releases.
Download the .zip file corresponding to your language and extract it.

## Adding Vendor Libraries
### With Visual Studio Code
Open the command palette with CTRL + SHIFT + P, or by clicking the WPILib icon.
Open `WPILib: Manage Vendor Libraries`, `Install new libraries (online)`, and paste the vendor-provided JSON url.

### Without Visual Studio Code
Create a folder `vendordeps` in your project directory if it doesn't already exist.
Download the JSON file from the vendor-provided URL, and save it to the `vendordeps` folder.
This can be done by running `./gradlew vendordep --url=<vendor url here>` in a project.

## Commands
Windows Users: It is recommended to use Powershell instead of CMD. You can switch to powershell with `powershell`

### General
- `./gradlew build` will build your robot code (and run unit tests if present).
- `./gradlew deploy` will build and deploy your code.
- `./gradlew riolog` will display the RoboRIO console output on your computer (run with `-Pfakeds` if you don't have a driverstation connected).

- `./gradlew installRoboRioToolchain` will install the C++ Toolchains for your system (required for C++).

### Tools
- `./gradlew Glass` will launch Glass, a data visualization tool similar to the SimGUI.
- `./gradlew ShuffleBoard` will launch Shuffleboard, the 2018 replacement for SmartDashboard.
- `./gradlew SmartDashboard` will launch Smart Dashboard (note: SmartDashboard is legacy software, use ShuffleBoard instead!).
- `./gradlew RobotBuilder` will launch Robot Builder, a tool for generating robot projects and source files.
- `./gradlew RobotBuilder-Old` will launch the old version of Robot Builder, a tool for generating robot projects and source files with the old command framework.
- `./gradlew OutlineViewer` will launch Outline Viewer, for viewing NetworkTables.
- `./gradlew PathWeaver` will launch PathWeaver, a tool for generating motion profiles using WPILib's trajectories and splines.

**At Competition? Connected to the Robot?** Run with the `--offline` flag. e.g. `./gradlew deploy --offline`

## IDE Support
### Visual Studio Code:
VS Code is fully supported by GradleRIO for FRC. To use it, use the WPILib VS Code extension. See frc-docs for instructions.

### IntelliJ IDEA:
_IntelliJ IDEA support is unofficial in the FRC sense, but is well supported by the Gradle team. CSA Support isn't guaranteed, so make sure you're prepared to fix any issues yourself if you're at an event._

To import a gradle project into IntelliJ IDEA please do **one** of the following:
- In the welcome screen click `Import Project` and select the `build.gradle` file of the project.
- Click `Open` on the welcome screen or `File - Open` while you have another project open and select the `build.gradle` file of the project. IntelliJ will then prompt you if you would like to open it as a project, click `Open as Project`

IntelliJ may ask to import the Gradle project in the bottom right of the IDE, simple click `Import Changes` to import it.

Please see the IntelliJ IDEA help page on gradle for help: https://www.jetbrains.com/help/idea/gradle.html

### Eclipse
_Eclipse support is unofficial in the FRC sense, but is well supported by the Gradle team. CSA Support isn't guaranteed, so make sure you're prepared to fix any issues yourself if you're at an event. **Eclipse is only supported for JAVA (not C++)**_

First install buildship, the gradle plugin made by Eclipse for the Eclipse IDE. Installation instructions can be found here: https://github.com/eclipse/buildship/blob/master/docs/user/Installation.md

Once installed, navigate to `File - Import… - Gradle` and select Gradle Project.

Press the `Next >` button, then specify the root directory of the project.
Press `Finish` once to finish the import, and `Finish` again to confirm it.

Please see the buildship github page for help (specifically the user documentation and the forums): https://github.com/eclipse/buildship

### Visual Studio 2017 Community / Full (not Visual Studio Code)
_VS2017 support is unofficial in the FRC sense, but is well supported by the Gradle team. CSA Support isn't guaranteed, so make sure you're prepared to fix any issues yourself if you're at an event._

To start with, you must apply the `visual-studio` plugin to build.gradle. In your `build.gradle`, put the following code in the `plugins {}` block.
```gradle
plugins {
    id 'visual-studio'
}
```

Finally, you can generate and open your solution with the following command:
- `./gradlew openVisualStudio` will generate IDE files for VS2017 (C++) and open Visual Studio.

Please see the gradle guide on building native software for help: https://docs.gradle.org/current/userguide/native_software.html#native_binaries:visual_studio

## Upgrading
To upgrade your Gradle project (and GradleRio plugin) from one year to the next follow the [Importing a Gradle Project](https://docs.wpilib.org/en/stable/docs/software/vscode-overview/importing-gradle-project.html) instructions in the WPILib Documentation.

For mid-season updates to GradleRio, edit build.gradle and replace the version in the plugin line (only change the GradleRIO line):
```gradle
plugins {
    // ... other plugins ...
    id "edu.wpi.first.GradleRIO" version "REPLACE ME WITH THE LATEST VERSION"
}
```

The latest version can be obtained from here: https://plugins.gradle.org/plugin/edu.wpi.first.GradleRIO
