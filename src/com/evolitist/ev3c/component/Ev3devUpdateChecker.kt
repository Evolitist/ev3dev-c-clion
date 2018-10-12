package com.evolitist.ev3c.component

import com.evolitist.ev3c.action.InstallCLibraryAction
import com.evolitist.ev3c.action.RELEASE_URL
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.util.io.HttpRequests
import org.jetbrains.concurrency.runAsync
import java.awt.event.MouseEvent
import javax.swing.JOptionPane

class Ev3devUpdateChecker : ProjectComponent {
    private lateinit var props: PropertiesComponent

    override fun initComponent() {
        props = PropertiesComponent.getInstance()
    }

    override fun projectOpened() {
        runAsync {
            var success = false
            while (!success) {
                try {
                    checkUpdate()
                } catch (e: Exception) {
                } finally {
                    success = true
                }
            }
        }
    }

    private fun checkUpdate() {
        val json = HttpRequests.request(RELEASE_URL)
                .connectTimeout(300000)
                .readTimeout(300000)
                .redirectLimit(10)
                .readString()
        val data = Gson().fromJson(json, JsonElement::class.java).asJsonObject
        val currentId = data.get("id").asInt
        val lastId = props.getInt("lastCLibId", 0)
        if (lastId < currentId) {
            val version = data.get("tag_name").asString
            val pubDateTime = data.get("published_at").asString
            val pubDate = pubDateTime.substringBefore("T").split("-").reversed().joinToString(".")
            val pubTime = pubDateTime.substringAfter("T").substringBeforeLast(":")
            val notification = Notification(
                    "Ev3dev.LibUpdate",
                    AllIcons.General.Information,
                    "Library update",
                    "$version, $pubDate $pubTime",
                    "<html>ev3dev C library is ready to <a href=\"update\">update</a>.</html>",
                    NotificationType.INFORMATION
            ) { notification, _ ->
                props.setValue("lastCLibId", currentId, 0)
                val ie = MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1)
                ActionManager.getInstance().tryToExecute(InstallCLibraryAction(), ie, null, null, true)
                notification.hideBalloon()
            }
            ApplicationManager.getApplication().invokeLater {
                Notifications.Bus.notify(notification)
            }
        }
    }

    override fun projectClosed() {
    }

    override fun disposeComponent() {
    }
}