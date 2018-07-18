package com.evolitist.ev3c

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.cidr.toolchains.OSType
import java.io.File

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
