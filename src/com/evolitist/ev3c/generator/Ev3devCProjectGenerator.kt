package com.evolitist.ev3c.generator

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CMakeProjectGenerator
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

class Ev3devCProjectGenerator : CMakeProjectGenerator() {
    override fun getName() = "ev3dev C program"

    override fun createSourceFiles(projectName: String, projectRootDir: VirtualFile): Array<VirtualFile> = arrayOf(
            createProjectFileWithContent(projectRootDir, "main.c", "#include \"rbc.h\"\n\ntask main() {\n\n}\n")
    )

    override fun getCMakeFileContent(projectName: String) = "cmake_minimum_required(VERSION 3.5.1)\n" +
            "project($projectName C)\n" +
            "\n" +
            "include_directories(/usr/local/include)\n" +
            "link_directories(/usr/local/lib)\n" +
            "\n" +
            "set(CMAKE_C_STANDARD 99)\n" +
            "set(CMAKE_C_COMPILER /usr/bin/arm-linux-gnueabi-gcc)\n" +
            "set(CMAKE_C_LINK_EXECUTABLE \"arm-linux-gnueabi-gcc <LINK_LIBRARIES> <OBJECTS> -o <TARGET> -lev3dev-c -lpthread -lm\")\n" +
            "\n" +
            "add_executable($projectName main.c)\n" +
            "\n" +
            "add_custom_command(TARGET $projectName POST_BUILD COMMAND sshpass -p 'maker' scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $projectName robot@192.168.0.1:~)"

    override fun getSettingsPanel() = SettingsPanel()

    inner class SettingsPanel : JPanel() {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(Box.createVerticalGlue())
            add(JLabel("Some EV3 selection & configuration stuff will be here."))
            add(JLabel("Or it won't. Haven't decided on layout yet."))
            add(Box.createVerticalGlue())
        }
    }
}