package com.evolitist.ev3c.execution

import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.Executor
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configuration.ConfigurationFactoryEx
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.RemoteUtil
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfigurationSettingsEditor
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.testing.CidrLauncher
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import icons.Ev3devIcons

class Ev3devRunConfigurationType : CMakeRunConfigurationType("EV3DEV_RUN", "EV3FACTORY", "Ev3dev", "TODO: fill", lazyIcon { Ev3devIcons.EV3 }) {
    override fun createEditor(project: Project): SettingsEditor<out CMakeAppRunConfiguration> {
        return CMakeAppRunConfigurationSettingsEditor(project, getHelper(project))
    }

    override fun getFactory(): ConfigurationFactory {
        return object : ConfigurationFactoryEx<Ev3devRunConfiguration>(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration {
                return createRunConfiguration(project, this)
            }

            override fun getId() = "EV3FACTORY"

            override fun onNewConfigurationCreated(configuration: Ev3devRunConfiguration) {
                super.onNewConfigurationCreated(configuration)
                configuration.setupDefaultTargetAndExecutable()
            }
        }
    }

    override fun createRunConfiguration(p0: Project, p1: ConfigurationFactory): CMakeAppRunConfiguration {
        val conf = Ev3devRunConfiguration(p0, p1, "Ev3dev")
        val task = BeforeRunTaskProvider.getProvider(p0, Ev3devDeployBeforeRunTaskProvider.ID)!!.createTask(conf)!!
        RunManagerEx.getInstanceEx(p0).setBeforeRunTasks(conf, listOf(task), true)
        return conf
    }

    companion object {
        @JvmStatic
        fun getInstance(): Ev3devRunConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(Ev3devRunConfigurationType::class.java)
        }
    }
}

class Ev3devRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
        CMakeAppRunConfiguration(project, factory, name) {

    override fun getState(p0: Executor, p1: ExecutionEnvironment): CidrCommandLineState? {
        return CidrCommandLineState(p1, SSHLauncher(p1.project))
    }

    class SSHLauncher(private val theProject: Project) : CidrLauncher() {
        override fun createDebugProcess(p0: CommandLineState, p1: XDebugSession): CidrDebugProcess {
            TODO("not implemented")
        }

        override fun createProcess(p0: CommandLineState): ProcessHandler {
            val handler = RemoteUtil.createRemoteProcess(
                    CidrToolEnvironment(false),
                    GeneralCommandLine("conrun", "-o", "-e", "/home/robot/${theProject.name}"),
                    RemoteCredentialsHolder().apply {
                        host = "192.168.0.1"
                        port = 22
                        userName = "robot"
                        password = "maker"
                    }, true, true)
            configProcessHandler(handler, false, true, theProject)
            return handler
        }

        override fun getProject() = theProject
    }
}
