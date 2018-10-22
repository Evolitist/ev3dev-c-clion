package com.evolitist.ev3c.action

import com.evolitist.ev3c.component.Ev3devConnector
import com.intellij.execution.Platform
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.cidr.CidrBundle
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import com.jetbrains.cidr.cpp.execution.build.CMakeTargetAction
import org.jetbrains.concurrency.runAsync
import java.io.File

class DeployProgramAction : CMakeTargetAction("Deploy", null, AllIcons.Nodes.Deploy) {
    private fun isProjectCompatible(project: Project?) = PropertiesComponent.getInstance(project).getValue("ev3cLibraryType") != null

    override fun update(e: AnActionEvent) {
        val project = AnAction.getEventProject(e)
        e.presentation.isEnabled = project != null && isAvailable(project)
        e.presentation.isVisible = project != null && CMakeWorkspace.getInstance(project).isInitialized && isProjectCompatible(project)
    }

    override fun doBuild(p0: Project, p1: CMakeAppRunConfiguration.BuildAndRunConfigurations) {
        runAsync {
            val connector = Ev3devConnector.getInstance(p0)
            val sftp = connector.sftp ?: return@runAsync
            CMakeBuild.build(p0, p1).get()
            val messagesWindow = ToolWindowManager.getInstance(p0).getToolWindow(ToolWindowId.MESSAGES_WINDOW)
            val contents = messagesWindow.contentManager
            val console = ContainerUtil.find(contents.contents) { s ->
                s.displayName == CidrBundle.message("build.logToolWindowName", emptyArray<Any>())
            }!!.component as ConsoleViewImpl
            Thread.sleep(500)
            console.print("\nSending program to ev3dev device...\n", ConsoleViewContentType.NORMAL_OUTPUT)
            try {
                sftp.uploadFileOrDir(File("${p0.basePath}${Platform.current().fileSeparator}cmake-build-debug${Platform.current().fileSeparator}${p0.name}"), "/home/robot", p0.name)
                console.print("Setting permissions...\n", ConsoleViewContentType.NORMAL_OUTPUT)
                connector("chmod +x ~/${p0.name}").waitFor()
                console.print("File upload complete\n", ConsoleViewContentType.NORMAL_OUTPUT)
            } catch (e: Exception) {
                console.print("Didn't find connected ev3dev device\n", ConsoleViewContentType.ERROR_OUTPUT)
            }
        }
    }
}