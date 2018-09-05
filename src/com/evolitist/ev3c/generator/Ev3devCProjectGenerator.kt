package com.evolitist.ev3c.generator

import com.evolitist.ev3c.USED_LIBRARY_TYPE
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CMakeProjectGenerator
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.settings.CMakeProjectSettings
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JLabel
import javax.swing.JPanel

class Ev3devCProjectGenerator : CMakeProjectGenerator() {
    private var usedLibraryType = "static"

    override fun getName() = "ev3dev C program"

    override fun createSourceFiles(projectName: String, projectRootDir: VirtualFile): Array<VirtualFile> = arrayOf(
            createProjectFileWithContent(projectRootDir, "main.c", "#include \"rbc.h\"\n\ntask main() {\n\n}\n")
    )

    override fun getCMakeFileContent(projectName: String): String {
        val ev3clink = if (usedLibraryType == "static") "-l:libev3dev-c.a" else "-lev3dev-c"
        return "cmake_minimum_required(VERSION 3.5.1)\n" +
                "project($projectName C)\n" +
                "\n" +
                "include_directories(/usr/local/include)\n" +
                "link_directories(/usr/local/lib)\n" +
                "\n" +
                "set(CMAKE_C_STANDARD 99)\n" +
                "set(CMAKE_C_COMPILER /usr/bin/arm-linux-gnueabi-gcc)\n" +
                "set(CMAKE_C_LINK_EXECUTABLE \"arm-linux-gnueabi-gcc <LINK_LIBRARIES> <OBJECTS> -o <TARGET> $ev3clink -lpthread -lm\")\n" +
                "\n" +
                "add_executable($projectName main.c)\n" +
                "\n" +
                "add_custom_command(TARGET $projectName POST_BUILD COMMAND sshpass -p 'maker' scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $projectName robot@192.168.0.1:~)"
    }

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: CMakeProjectSettings, module: Module) {
        super.generateProject(project, baseDir, settings, module)
        project.putUserData(USED_LIBRARY_TYPE, usedLibraryType)
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
            /*add(Box.createVerticalGlue())
            add(JLabel("Some EV3 selection & configuration stuff will be here."))
            add(JLabel("Or it won't. Haven't decided on layout yet."))
            add(Box.createVerticalGlue())*/
        }
    }
}