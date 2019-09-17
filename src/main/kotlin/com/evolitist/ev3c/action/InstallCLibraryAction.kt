package com.evolitist.ev3c.action

import com.evolitist.ev3c.defaultIncludeLocation
import com.evolitist.ev3c.defaultLibLocation
import com.evolitist.ev3c.run
import com.evolitist.ev3c.translateToWSL
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.io.HttpRequests
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.Cygwin
import com.jetbrains.cidr.cpp.toolchains.MSVC
import com.jetbrains.cidr.cpp.toolchains.MinGW
import java.io.File
import java.util.zip.ZipInputStream

class InstallCLibraryAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        //TODO: launch some sort of a "library selection wizard"
        if (CPPToolchains.getInstance().defaultToolchain?.toolSet is Cygwin ||
                CPPToolchains.getInstance().defaultToolchain?.toolSet is MSVC) {
            val statusBar = WindowManager.getInstance().getStatusBar(event.project)
            ApplicationManager.getApplication().invokeLater {
                JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                        "Unsupported OS/toolchain!", null, JBColor(0xEE4A4A, 0x412E33), null
                ).createBalloon().show(
                        RelativePoint.getCenterOf(statusBar.component),
                        Balloon.Position.above
                )
            }
        } else {
            ProgressManager.getInstance().run(InstallLibraryTask(event.project))
        }
    }

    class InstallLibraryTask(project: Project?) : Task.Backgroundable(project, "Installing library...", true, PerformInBackgroundOption.DEAF) {
        override fun run(it: ProgressIndicator) {
            it.text = "Getting release info..."
            //TODO: check if libraries are already installed
            val json = HttpRequests.request(RELEASE_URL)
                    .connectTimeout(300000)
                    .readTimeout(300000)
                    .redirectLimit(10)
                    .readString(it)
            if (it.isCanceled) return

            val data = Gson().fromJson(json, JsonElement::class.java).asJsonObject
            val currentId = data.get("id").asInt
            val downloadUrl = data.getAsJsonArray("assets")[0].asJsonObject
                    .get("browser_download_url").asString
            val tempFile: File
            val lastId = PropertiesComponent.getInstance().getInt("lastCLibId", 0)
            if (lastId < currentId || lastTempFile == null) {
                it.text = "Fetching latest release..."
                PropertiesComponent.getInstance().setValue("lastCLibId", currentId, 0)
                tempFile = File.createTempFile("ev3dev-c-release", ".zip")
                tempFile.deleteOnExit()
                lastTempFile = tempFile.absolutePath
                HttpRequests.request(downloadUrl)
                        .connectTimeout(0)
                        .readTimeout(0)
                        .redirectLimit(10)
                        .saveToFile(tempFile, it)
                if (it.isCanceled) return
            } else {
                tempFile = File(lastTempFile)
            }

            it.text = "Installing library..."
            if (CPPToolchains.getInstance().defaultToolchain?.toolSet is MinGW) {
                val buffer = ByteArray(1024)
                val zip = ZipInputStream(tempFile.inputStream())
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast("/")
                        if (name.endsWith(".h")) {
                            val hFile = File(defaultIncludeLocation(), name)
                            hFile.parentFile.mkdirs()
                            val fos = hFile.outputStream()
                            var len = zip.read(buffer)
                            while (len > 0) {
                                fos.write(buffer, 0, len)
                                len = zip.read(buffer)
                            }
                            fos.close()
                        } else {
                            val lFile = File(defaultLibLocation(), name)
                            lFile.parentFile.mkdirs()
                            val fos = lFile.outputStream()
                            var len = zip.read(buffer)
                            while (len > 0) {
                                fos.write(buffer, 0, len)
                                len = zip.read(buffer)
                            }
                            fos.close()
                        }
                    }
                    entry = zip.nextEntry
                }
                zip.closeEntry()
                zip.close()
            } else {
                "unzip ${tempFile.translateToWSL()}".run(it)
                if (it.isCanceled) {
                    it.text = "Cleaning up..."
                    "rm -rf lib/ include/".run()
                    return
                }
                "cp -f lib/* /usr/local/lib/".run(it)
                if (it.isCanceled) {
                    it.text = "Cleaning up..."
                    "rm -rf lib/ include/".run()
                    return
                }
                "cp -f include/* /usr/local/include/".run(it)
                if (it.isCanceled) {
                    it.text = "Cleaning up..."
                    "rm -rf lib/ include/".run()
                    return
                }
                it.text = "Cleaning up..."
                "rm -rf lib/ include/".run(it)
            }
            CMakeWorkspace.getInstance(project).scheduleReload(true)
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            ApplicationManager.getApplication().invokeLater {
                JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                        "Library installed!", null, JBColor(12250810, 3359022), null
                ).createBalloon().show(
                        RelativePoint.getCenterOf(statusBar.component),
                        Balloon.Position.above
                )
            }
        }
    }
}
