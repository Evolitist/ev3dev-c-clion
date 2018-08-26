package com.evolitist.ev3c.action

import com.evolitist.ev3c.run
import com.evolitist.ev3c.translateToWSL
import com.google.gson.Gson
import com.google.gson.JsonElement
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
import java.io.File

class InstallCLibraryAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        //TODO: launch some sort of a "library selection wizard"
        ProgressManager.getInstance().run(InstallLibraryTask(event.project))
    }

    class InstallLibraryTask(project: Project?) : Task.Backgroundable(project, "Installing library...", true, PerformInBackgroundOption.DEAF) {
        override fun run(it: ProgressIndicator) {
            it.text = "Getting release info..."
            //TODO: check if libraries are already installed
            val json = HttpRequests.request(RELEASE_URL)
                    .connectTimeout(0)
                    .readTimeout(0)
                    .redirectLimit(10)
                    .readString(it)
            if (it.isCanceled) return
            it.fraction = 0.05

            val data = Gson().fromJson(json, JsonElement::class.java).asJsonObject
            val currentId = data.get("id").asInt
            val downloadUrl = data.getAsJsonArray("assets")[0].asJsonObject
                    .get("browser_download_url").asString
            val tempFile: File
            if (lastId < currentId || lastTempFile == null) {
                it.text = "Fetching latest release..."
                lastId = currentId
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
            it.fraction = 0.5

            it.text = "Installing library..."
            "unzip ${tempFile.translateToWSL()}".run(it)
            if (it.isCanceled) {
                it.text = "Cleaning up..."
                "rm -rf lib/ include/".run()
                return
            }
            it.fraction = 0.6
            "cp -f lib/* /usr/local/lib/".run(it)
            if (it.isCanceled) {
                it.text = "Cleaning up..."
                "rm -rf lib/ include/".run()
                return
            }
            it.fraction = 0.7
            "cp -f include/* /usr/local/include/".run(it)
            if (it.isCanceled) {
                it.text = "Cleaning up..."
                "rm -rf lib/ include/".run()
                return
            }
            it.fraction = 0.8
            "sudo ldconfig".run(it)
            it.fraction = 0.9
            it.text = "Cleaning up..."
            "rm -rf lib/ include/".run(it)
            it.fraction = 1.0
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
