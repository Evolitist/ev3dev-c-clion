package com.evolitist.ev3c.action

import com.evolitist.ev3c.component.Ev3devConnector
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import icons.Ev3devIcons
import java.net.InetAddress
import java.util.*
import java.util.Collections.synchronizedList
import javax.swing.JComponent

class BrickSelectorAction : ComboBoxAction() {
    private val knownProjects = synchronizedList(ArrayList<Project>())
    private var selectedDeviceAction: Ev3devDeviceAction? = null
    private val actions: MutableList<AnAction> = mutableListOf()

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
            Ev3devConnector.getInstance()
        }
        if (!knownProjects.contains(project)) {
            knownProjects += project
            Disposer.register(project, Disposable {
                knownProjects -= project
                if (knownProjects.isEmpty()) {
                    Ev3devConnector.getInstance().dispose()
                }
            })
            val connector = Ev3devConnector.getInstance()
            connector.addListener {
                update(e.presentation)
            }
            update(e.presentation)
        }
    }

    private fun update(presentation: Presentation) {
        actions.clear()
        val service = Ev3devConnector.getInstance()
        val devices = service.getConnectedDevices()
        devices.forEach {
            actions.add(Ev3devDeviceAction(it))
        }
        if (actions.isEmpty()) {
            actions.add(Ev3devEmptyAction("<empty>"))
        }
        actions.add(Separator())
        actions.add(Ev3devManualAddAction())
        val selectedDevice = service.getSelectedDevice()
        if (selectedDevice != selectedDeviceAction?.device) {
            service.connectTo(selectedDevice)
        }
        selectedDeviceAction = null
        actions.asSequence()
                .filterIsInstance(Ev3devDeviceAction::class.java)
                .filter { it.device == selectedDevice }
                .forEach { action ->
                    selectedDeviceAction = action
                    val template = action.templatePresentation
                    presentation.icon = template.icon
                    presentation.text = action.device.hostAddress//action.device.hostName.split(".")[0]
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
    override fun shouldShowDisabledActions() = true

    override fun getPreselectCondition(): Condition<AnAction> {
        return Condition { action -> action == selectedDeviceAction }
    }

    internal class Ev3devDeviceAction(val device: InetAddress) : AnAction("${device.hostName} (${device.hostAddress})", null, Ev3devIcons.EV3) {
        override fun actionPerformed(e: AnActionEvent) {
            Ev3devConnector.getInstance().setSelectedDevice(device)
        }
    }

    private class Ev3devEmptyAction internal constructor(message: String) : AnAction(message, null, null), TransparentUpdate {
        init {
            templatePresentation.isEnabled = false
        }

        override fun actionPerformed(p0: AnActionEvent) {}
    }

    internal class Ev3devManualAddAction : AnAction("Add undetected device...") {
        override fun actionPerformed(p0: AnActionEvent) {

        }
    }
}