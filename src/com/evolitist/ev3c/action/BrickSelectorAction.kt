package com.evolitist.ev3c.action

import com.evolitist.ev3c.component.Ev3devConnector
import com.evolitist.ev3c.defaultLibLocation
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import java.util.*
import java.util.Collections.synchronizedList

class BrickSelectorAction : AnAction() {
    private val knownProjects = synchronizedList(ArrayList<Project>())

    override fun actionPerformed(p0: AnActionEvent) {
        println(defaultLibLocation())
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
                e.presentation.icon = connector.state.icon
                e.presentation.text = connector.getStateName()
            }
            e.presentation.icon = connector.state.icon
            e.presentation.text = connector.getStateName()
        }
    }

    override fun displayTextInToolbar() = true
    override fun useSmallerFontForTextInToolbar() = true
}