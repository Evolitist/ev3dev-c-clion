package com.evolitist.ev3c.action

import com.evolitist.ev3c.run
import com.evolitist.ev3c.translateToWSL
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.util.io.HttpRequests
import java.io.File

class InstallCLibraryAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        //TODO: launch some sort of a "library selection wizard"
        runBackgroundableTask("Installing library...", event.project, true) {
            //TODO: check if libraries are already installed
            val json = HttpRequests.request(RELEASE_URL)
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
            if (lastId < currentId || lastTempFile == null) {
                tempFile = File.createTempFile("ev3dev-c-release", ".zip")
                tempFile.deleteOnExit()
                lastTempFile = tempFile.absolutePath
                HttpRequests.request(downloadUrl)
                        .connectTimeout(0)
                        .readTimeout(0)
                        .redirectLimit(10)
                        .saveToFile(tempFile, it)
                if (it.isCanceled) return@runBackgroundableTask
            } else {
                tempFile = File(lastTempFile)
            }

            "unzip ${tempFile.translateToWSL()}".run(it)
            if (it.isCanceled) {
                "rm -rf lib/ include/".run()
                return@runBackgroundableTask
            }
            "cp -f lib/* /usr/local/lib/".run(it)
            if (it.isCanceled) {
                "rm -rf lib/ include/".run()
                return@runBackgroundableTask
            }
            "cp -f include/* /usr/local/include/".run(it)
            if (it.isCanceled) {
                "rm -rf lib/ include/".run()
                return@runBackgroundableTask
            }
            "ldconfig".run(it)
            if (it.isCanceled) {
                "rm -rf lib/ include/".run()
                return@runBackgroundableTask
            }
            "rm -rf lib/ include/".run(it)
            ApplicationManager.getApplication().invokeLater {
                val notification = notificationGroup.createNotification(
                        "Library installed!", NotificationType.INFORMATION)
                Notifications.Bus.notify(notification, event.project)
            }
        }
    }

    companion object {
        private val notificationGroup = NotificationGroup.balloonGroup("ev3dev")
        private const val RELEASE_URL = "https://api.github.com/repos/Evolitist/ev3dev-c/releases/latest"
        private var lastId = 0
        private var lastTempFile: String? = null
    }
}