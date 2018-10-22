package com.evolitist.ev3c.component

import com.google.common.collect.ImmutableSet
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.process.SshExecProcess
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.SwingUtilities

class Ev3devConnector(private val project: Project) {
    private val address = InetAddress.getByName("192.168.0.1")
    private val conn = ConnectionBuilder("192.168.0.1", 22)
            .withUsername("robot")
            .withPassword("maker")
    private val connectorThread = Thread {
        while (shouldRun) {
            while (!address.isReachable(250));
            state = State.CONNECTING
            fireChangeEvent()
            try {
                sftp = conn.openSftpChannel()
                stateName = conn.execBuilder("hostname").execute().inputStream.bufferedReader().readLine()
                state = State.CONNECTED
                fireChangeEvent()
                fireSftpUpdateEvent()
                while (sftp!!.isConnected && address.isReachable(250) && shouldRun);
                stateName = null
                if (!sftp!!.isConnected || !address.isReachable(250)) {
                    state = State.DISCONNECTED
                    fireChangeEvent()
                    sftp = null
                    fireSftpUpdateEvent()
                }
            } catch (e: Exception) {
                stateName = null
                state = State.ERROR
                fireChangeEvent()
                sftp = null
                fireSftpUpdateEvent()
                Thread.sleep(2000)
                state = State.DISCONNECTED
                fireChangeEvent()
            }
        }
    }

    @Volatile
    private var shouldRun = true

    @Volatile
    private var stateName: String? = null

    @Volatile
    var sftp: SftpChannel? = null
        private set

    @Volatile
    var state = State.DISCONNECTED

    private val listeners = AtomicReference(ImmutableSet.of<() -> Unit>())
    private val sftpListeners = AtomicReference(ImmutableSet.of<(SftpChannel?) -> Unit>())

    init {
        connectorThread.start()
    }

    fun dispose() {
        shouldRun = false
        if (connectorThread.isAlive) {
            connectorThread.join(1000)
        }
    }

    fun addListener(callback: () -> Unit) {
        listeners.updateAndGet { old ->
            val changed = ArrayList(old)
            changed.add(callback)
            ImmutableSet.copyOf(changed)
        }
    }

    fun addSftpListener(callback: (SftpChannel?) -> Unit) {
        sftpListeners.updateAndGet { old ->
            val changed = ArrayList(old)
            changed.add(callback)
            ImmutableSet.copyOf(changed)
        }
        callback.invoke(sftp)
    }

    private fun fireChangeEvent() {
        SwingUtilities.invokeLater {
            if (project.isDisposed) return@invokeLater
            for (listener in listeners.get()) {
                try {
                    listener.invoke()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun fireSftpUpdateEvent() {
        SwingUtilities.invokeLater {
            if (project.isDisposed) return@invokeLater
            for (listener in sftpListeners.get()) {
                try {
                    listener.invoke(sftp)
                } catch (e: Exception) {
                }
            }
        }
    }

    fun getStateName() = stateName ?: state.title

    operator fun invoke(command: String, timeout: Int = 0): SshExecProcess {
        return conn.execBuilder(command).execute(timeout)
    }

    enum class State(val icon: Icon, val title: String) {
        DISCONNECTED(AllIcons.RunConfigurations.TestIgnored, "<no device>"),
        CONNECTING(AllIcons.RunConfigurations.TestNotRan, "<connecting>"),
        CONNECTED(AllIcons.RunConfigurations.TestPassed, "<unknown>"),
        ERROR(AllIcons.RunConfigurations.TestError, "<error>");
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): Ev3devConnector = ServiceManager.getService(project, Ev3devConnector::class.java)
    }
}