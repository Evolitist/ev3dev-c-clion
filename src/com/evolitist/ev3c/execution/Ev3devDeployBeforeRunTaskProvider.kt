package com.evolitist.ev3c.execution

import com.evolitist.ev3c.component.Ev3devConnector
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.Platform
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.cidr.CidrBundle
import com.jetbrains.cidr.cpp.execution.build.CMakeTargetAction
import java.io.File
import javax.swing.Icon

class Ev3devDeployBeforeRunTaskProvider : BeforeRunTaskProvider<Ev3devDeployBeforeRunTaskProvider.Ev3devDeployBeforeRunTask>() {
    override fun getIcon(): Icon = CMakeTargetAction.Install.ICON

    override fun getName() = "Deploy to ev3dev"

    override fun getId() = ID

    override fun createTask(p0: RunConfiguration) = if (p0 is Ev3devRunConfiguration) Ev3devDeployBeforeRunTask() else null

    override fun isConfigurable() = false

    override fun executeTask(p0: DataContext?, p1: RunConfiguration, p2: ExecutionEnvironment, p3: Ev3devDeployBeforeRunTask): Boolean {
        val connector = Ev3devConnector.getInstance(p1.project)
        val sftp = connector.sftp ?: return false
        val messagesWindow = ToolWindowManager.getInstance(p1.project).getToolWindow(ToolWindowId.MESSAGES_WINDOW)
        val contents = messagesWindow.contentManager
        val console = ContainerUtil.find(contents.contents) { s ->
            s.displayName == CidrBundle.message("build.logToolWindowName", emptyArray<Any>())
        }!!.component as ConsoleViewImpl
        Thread.sleep(500)
        val file = File("${p1.project.basePath}${Platform.current().fileSeparator}cmake-build-debug${Platform.current().fileSeparator}${p1.project.name}")
        if (!file.exists()) {
            console.print("\nCouldn't find file to upload!\n", ConsoleViewContentType.ERROR_OUTPUT)
            return false
        }
        console.print("\nSending program to ev3dev device...\n", ConsoleViewContentType.NORMAL_OUTPUT)
        try {
            sftp.uploadFileOrDir(file, "/home/robot", p1.project.name)
            console.print("Setting permissions...\n", ConsoleViewContentType.NORMAL_OUTPUT)
            connector("chmod +x ~/${p1.project.name}").waitFor()
            console.print("File upload complete\n", ConsoleViewContentType.NORMAL_OUTPUT)
        } catch (e: Exception) {
            console.print("Didn't find connected ev3dev device\n", ConsoleViewContentType.ERROR_OUTPUT)
            return false
        }
        return true
    }

    companion object {
        @JvmStatic
        val ID: Key<Ev3devDeployBeforeRunTask> = Key.create("Ev3dev.Deploy")
    }

    class Ev3devDeployBeforeRunTask : BeforeRunTask<Ev3devDeployBeforeRunTask>(ID) {
        init {
            isEnabled = true
        }
    }
}