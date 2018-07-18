package com.evolitist.ev3c.action

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.io.HttpRequests
import java.io.File
import java.util.concurrent.TimeUnit

class DeployEv3devCLibraryAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        //TODO: launch some sort of a "library selection wizard"
        runBackgroundableTask("Deploying library...", event.project, true) {
            //TODO: check if libraries are already installed
            val json = HttpRequests.request(InstallCLibraryAction.RELEASE_URL)
                    .connectTimeout(0)
                    .readTimeout(0)
                    .redirectLimit(10)
                    .readString(it)
            if (it.isCanceled) return@runBackgroundableTask

            val data = Gson().fromJson(json, JsonElement::class.java).asJsonObject
            val currentId = data.get("id").asInt
            val downloadUrl = data.getAsJsonArray("assets")[0].asJsonObject
                    .get("browser_download_url").asString
            val tempFile: File
            if (InstallCLibraryAction.lastId < currentId || InstallCLibraryAction.lastTempFile == null) {
                InstallCLibraryAction.lastId = currentId
                tempFile = File.createTempFile("ev3dev-c-release", ".zip")
                tempFile.deleteOnExit()
                InstallCLibraryAction.lastTempFile = tempFile.absolutePath
                HttpRequests.request(downloadUrl)
                        .connectTimeout(0)
                        .readTimeout(0)
                        .redirectLimit(10)
                        .saveToFile(tempFile, it)
                if (it.isCanceled) return@runBackgroundableTask
            } else {
                tempFile = File(InstallCLibraryAction.lastTempFile)
            }

            val connBuilder = ConnectionBuilder("192.168.0.1", 22)
                    .withUsername("robot")
                    .withPassword("maker")
            if (it.isCanceled) return@runBackgroundableTask
            connBuilder.openSftpChannel().uploadFileOrDir(tempFile, "/home/robot/", "release.zip")
            if (it.isCanceled) return@runBackgroundableTask
            connBuilder.execBuilder("unzip release.zip")
                    .execute()
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return@runBackgroundableTask
            connBuilder.execBuilder("echo maker | sudo -S cp -f lib/* /usr/local/lib/")
                    .execute()
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return@runBackgroundableTask
            connBuilder.execBuilder("echo maker | sudo -S cp -f include/* /usr/local/include/")
                    .execute()
                    .waitFor(10, TimeUnit.SECONDS)
            if (it.isCanceled) return@runBackgroundableTask
            connBuilder.execBuilder("echo maker | sudo -S ldconfig")
                    .execute()
                    .waitFor(10, TimeUnit.SECONDS)
            connBuilder.execBuilder("rm -rf release.zip include/ lib/")
                    .execute()
                    .waitFor(10, TimeUnit.SECONDS)
            val statusBar = WindowManager.getInstance().getStatusBar(event.project)
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
