package com.evolitist.ev3c

import com.evolitist.ev3c.toolwindow.Ev3devFilesToolWindowFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ssh.RemoteFileObject
import com.jetbrains.cidr.toolchains.OSType
import java.io.File
import javax.swing.Icon

fun String.run(pi: ProgressIndicator? = null): ProcessOutput {
    val process = CapturingProcessHandler(GeneralCommandLine("bash", "-c", "cd && $this"))
    return if (pi != null) {
        process.runProcessWithProgressIndicator(pi)
    } else {
        process.runProcess()
    }
}

fun File.translateToWSL(): String {
    return when (OSType.getCurrent()) {
        OSType.WIN -> {
            val wslPath = canonicalPath
                    .decapitalize()
                    .replaceFirst(":", "")
                    .replace("\\", "/")
            "/mnt/$wslPath"
        }
        else -> absolutePath
    }
}

fun RemoteFileObject.wrap() = Ev3devFilesToolWindowFactory.SftpFile(this)

inline fun action(title: String, crossinline action: (AnActionEvent) -> Unit): AnAction {
    return object : AnAction(title) {
        override fun actionPerformed(p0: AnActionEvent) = action(p0)
        override fun displayTextInToolbar() = true
    }
}

inline fun iconAction(icon: Icon, title: String? = null, crossinline action: (AnActionEvent) -> Unit): AnAction {
    return object : AnAction(title, null, icon) {
        override fun actionPerformed(p0: AnActionEvent) = action(p0)
        override fun displayTextInToolbar() = title != null
    }
}
