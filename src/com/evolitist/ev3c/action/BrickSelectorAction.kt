package com.evolitist.ev3c.action

import com.evolitist.ev3c.component.Ev3devConnector
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import java.util.*
import java.util.Collections.synchronizedList
import javax.swing.JComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.Ev3devIcons
import java.net.InetAddress
import sun.audio.AudioDevice.device
import sun.audio.AudioDevice.device

class BrickSelectorAction : ComboBoxAction() {
    private val knownProjects = synchronizedList(ArrayList<Project>())
    private var selectedDeviceAction: Ev3devDeviceAction? = null
    val actions: MutableList<AnAction> = mutableListOf()

    override fun createPopupActionGroup(p0: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(actions)
    }

    override fun update(e: AnActionEvent) {
        if (e.place != ActionPlaces.MAIN_TOOLBAR && e.place != ActionPlaces.NAVIGATION_BAR_TOOLBAR) {
            return
        }
        val project = AnAction.getEventProject(e)
        e.presentation.isEnabled = project != null && CMakeAppRunConfiguration.getSelectedConfigurationAndTarget(project) != null
        project ?: return
        val check = PropertiesComponent.getInstance(project).getValue("ev3cLibraryType")
        e.presentation.isVisible = CMakeWorkspace.getInstance(project).isInitialized && check != null
        if (check != null) {
            Ev3devConnector.getInstance(project)
        }
        if (!knownProjects.contains(project)) {
            knownProjects += project
            Disposer.register(project, Disposable {
                knownProjects -= project
                Ev3devConnector.getInstance(project).dispose()
            })
            val connector = Ev3devConnector.getInstance(project)
            connector.addListener {
                update(project, e.presentation)
            }
            update(project, e.presentation)
        }
    }

    private fun update(project: Project, presentation: Presentation) {
        actions.clear()
        val service = Ev3devConnector.getInstance(project)
        val devices = service.getConnectedDevices()
        devices.forEach {
            actions.add(Ev3devDeviceAction(it))
        }
        if (actions.isEmpty()) {
            actions.add(Ev3devEmptyAction("<empty>"))
        }
        actions.add(Separator())
        actions.add(Ev3devManualAddAction())
        selectedDeviceAction = null
        val selectedDevice = service.getSelectedDevice()
        actions.asSequence()
                .filterIsInstance(Ev3devDeviceAction::class.java)
                .filter { it.device == selectedDevice }
                .forEach { action ->
                    selectedDeviceAction = action
                    val template = action.templatePresentation
                    presentation.icon = template.icon
                    presentation.text = action.device.canonicalHostName
                    presentation.isEnabled = true
                    return
        }
        if (devices.isEmpty()) {
            presentation.text = "<no devices>"
            presentation.icon = null
        } else {
            presentation.text = null
        }
    }

    override fun displayTextInToolbar() = true
    override fun useSmallerFontForTextInToolbar() = true

    internal class Ev3devDeviceAction(val device: InetAddress) : AnAction("${device.hostName} (${device.hostAddress})", null, Ev3devIcons.EV3) {
        fun deviceName(): String {
            return "${device.hostName} (${device.hostAddress})"
        }

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            Ev3devConnector.getInstance(project).setSelectedDevice(device)
        }
    }

    private class Ev3devEmptyAction internal constructor(message: String) : AnAction(message, null, null), AnAction.TransparentUpdate {
        init {
            templatePresentation.isEnabled = false
            templatePresentation.isVisible = false
        }

        override fun actionPerformed(p0: AnActionEvent) {}
    }

    internal class Ev3devManualAddAction : AnAction("Add undetected device...") {
        override fun actionPerformed(p0: AnActionEvent) {

        }
    }
}