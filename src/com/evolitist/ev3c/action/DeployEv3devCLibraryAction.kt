package com.evolitist.ev3c.action

import com.evolitist.ev3c.component.Ev3devConnector
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
import java.io.File
import java.util.concurrent.TimeUnit

class DeployEv3devCLibraryAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        //TODO: launch some sort of a "library selection wizard"
        ProgressManager.getInstance().run(DeployLibraryTask(event.project))
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = "static" != PropertiesComponent.getInstance(project).getValue("ev3cLibraryType", "shared")
    }

    class DeployLibraryTask(project: Project?) : Task.Backgroundable(project, "Deploying library...", true, PerformInBackgroundOption.DEAF) {
        override fun run(it: ProgressIndicator) {
            val conn = project?.getComponent(Ev3devConnector::class.java) ?: return
            val sftp = conn.sftp ?: return
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

            it.text = "Connecting to robot..."
            if (it.isCanceled) return
            it.text = "Uploading library..."
            sftp.uploadFileOrDir(tempFile, "/home/robot/", "release.zip")
            if (it.isCanceled) return
            it.text = "Installing library..."
            conn("unzip release.zip")
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return
            conn("echo maker | sudo -S cp -f lib/* /usr/local/lib/")
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return
            conn("echo maker | sudo -S cp -f include/* /usr/local/include/")
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return
            conn("echo maker | sudo -S ldconfig")
                    .waitFor(10, TimeUnit.SECONDS)
            it.text = "Cleaning up..."
            conn("rm -rf release.zip include/ lib/")
                    .waitFor(10, TimeUnit.SECONDS)
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            ApplicationManager.getApplication().invokeLater {
                JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                        "Library deployed!", null, JBColor(12250810, 3359022), null
                ).createBalloon().show(
                        RelativePoint.getCenterOf(statusBar.component),
                        Balloon.Position.above
                )
            }
        }
    }
}
