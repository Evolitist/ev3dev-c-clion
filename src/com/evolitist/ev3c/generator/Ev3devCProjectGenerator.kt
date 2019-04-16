package com.evolitist.ev3c.generator

import com.evolitist.ev3c.execution.Ev3devRunConfiguration
import com.evolitist.ev3c.execution.Ev3devRunConfigurationType
import com.intellij.codeInspection.ex.BASE_PROFILE
import com.intellij.execution.RunManagerEx
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CMakeProjectGenerator
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.settings.CMakeProjectSettings
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspaceListener
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationManager
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.execution.BuildTargetData
import com.jetbrains.cidr.execution.ExecutableData
import icons.Ev3devIcons
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JLabel
import javax.swing.JPanel

class Ev3devCProjectGenerator : CMakeProjectGenerator() {
    private var usedLibraryType = "static"

    override fun getName() = "ev3dev C program"

    override fun getGroupName() = "ev3dev"

    override fun getLogo() = Ev3devIcons.EV3

    override fun createSourceFiles(projectName: String, projectRootDir: VirtualFile): Array<VirtualFile> = arrayOf(
            createProjectFileWithContent(projectRootDir, "main.c", "#include <rbc.h>\n\ntask main() {\n\n}")
    )

    override fun getCMakeFileContent(projectName: String): String {
        val ev3clink = if (usedLibraryType == "static") "libev3dev-c.a" else "ev3dev-c"
        return "cmake_minimum_required(VERSION 3.5.1)\n" +
                "project($projectName C)\n" +
                "\n" +
                "include_directories(/usr/local/include)\n" +
                "link_directories(/usr/local/lib)" +
                "\n" +
                "set(CMAKE_C_STANDARD 99)\n" +
                "set(CMAKE_C_COMPILER arm-linux-gnueabi-gcc)\n" +
                "\n" +
                "add_executable($projectName main.c)\n" +
                "target_link_libraries($projectName $ev3clink pthread m)"
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: CMakeProjectSettings, module: Module) {
        super.generateProject(project, baseDir, settings, module)

        CMakeRunConfigurationManager.getInstance(project).setShouldGenerateConfigurations(false)
        PropertiesComponent.getInstance(project).setValue("ev3cLibraryType", usedLibraryType)

        val cMakeSettings = CMakeWorkspace.getInstance(project).settings
        val profiles = cMakeSettings.profiles.toMutableList()
        profiles.replaceAll {
            it.withGenerationOptions("-DCMAKE_SYSTEM_NAME=Linux ${it.generationOptions ?: ""}")
        }
        cMakeSettings.profiles = profiles.toList()

        val msgBus = project.messageBus.connect(project)
        msgBus.subscribe(CMakeWorkspaceListener.TOPIC, object : CMakeWorkspaceListener {
            override fun reloadingFinished(canceled: Boolean) {
                if (!canceled) {
                    BASE_PROFILE.modifyProfile {
                        val psi = PsiManager.getInstance(project).findFile(baseDir.findChild("main.c")!!)!!
                        it.disableTool("EndlessLoop", psi.originalElement)
                    }

                    val runManager = RunManagerEx.getInstanceEx(project) as RunManagerImpl
                    runManager.fireBeginUpdate()
                    val runConfHelper = CMakeRunConfigurationType.getHelper(project)
                    runConfHelper.targets.forEach {
                        val buildTargetData = BuildTargetData(it)
                        val runAndConfSettings = runManager.createConfiguration(it.name, Ev3devRunConfigurationType.getInstance().factory)
                        with(runAndConfSettings.configuration as Ev3devRunConfiguration) {
                            targetAndConfigurationData = BuildTargetAndConfigurationData(buildTargetData, runConfHelper.getDefaultConfiguration(it)?.name)
                            if (it.isExecutable) {
                                executableData = Ref.create(ExecutableData(buildTargetData))?.get()
                            }
                        }
                        runManager.addConfiguration(runAndConfSettings, false)
                    }
                    runManager.setOrder(Comparator { o1, o2 ->
                        o1.name.compareTo(o2.name)
                    })
                    runManager.fireEndUpdate()
                    runManager.selectedConfiguration = runManager.allSettings[0]
                }
            }
        })
    }

    override fun getSettingsPanel() = SettingsPanel()

    inner class SettingsPanel : JPanel() {
        init {
            layout = BorderLayout()
            val grid = GridLayoutManager(1, 2)
            val panel = JPanel(grid)
            val constraints = GridConstraints().apply {
                row = 0
                column = 0
                anchor = 8
            }
            val label = JLabel("Library type:")
            panel.add(label, constraints)
            val comboBox = ComboBox(arrayOf("static", "shared"))
            comboBox.selectedItem = usedLibraryType
            comboBox.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) {
                    usedLibraryType = it.item as String
                }
            }
            constraints.column = 1
            panel.add(comboBox, constraints)
            label.setDisplayedMnemonic('i')
            label.labelFor = comboBox
            add(panel, "West")
        }
    }
}