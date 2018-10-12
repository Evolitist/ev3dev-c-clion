package com.evolitist.ev3c.component

import com.google.common.collect.ImmutableSet
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.SshSession
import com.intellij.ssh.channels.SftpChannel
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.SwingUtilities

class Ev3devConnector(private val project: Project) : ProjectComponent {
    private val connectorThread = Thread {
        val conn = ConnectionBuilder("192.168.0.1", 22)
                .withUsername("robot")
                .withPassword("maker")
        val address = InetAddress.getByName("192.168.0.1")
        while (shouldRun) {
            while (!address.isReachable(250));
            state = State.CONNECTING
            fireChangeEvent()
            try {
                session = conn.connect()
                stateName = conn.execBuilder("hostname").execute().inputStream.bufferedReader().readLine()
                state = State.CONNECTED
                fireChangeEvent()
                sftp = conn.openSftpChannel()
                fireSftpUpdateEvent()
                while (session!!.isConnected && shouldRun);
                stateName = null
                if (!session!!.isConnected) {
                    sftp = null
                    fireSftpUpdateEvent()
                    session = null
                    state = State.DISCONNECTED
                    fireChangeEvent()
                }
            } catch (e: Exception) {
                state = State.ERROR
                fireChangeEvent()
            }
        }
    }

    @Volatile
    private var shouldRun = true

    @Volatile
    private var stateName: String? = null

    @Volatile
    var session: SshSession? = null
        private set

    @Volatile
    var sftp: SftpChannel? = null
        private set

    @Volatile
    var state = State.DISCONNECTED

    private val listeners = AtomicReference(ImmutableSet.of<() -> Unit>())
    private val sftpListeners = AtomicReference(ImmutableSet.of<(SftpChannel?) -> Unit>())

    override fun initComponent() {
        connectorThread.start()
    }

    override fun projectOpened() {}
    override fun projectClosed() {}

    override fun disposeComponent() {
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

    enum class State(val icon: Icon, val title: String) {
        DISCONNECTED(AllIcons.RunConfigurations.TestIgnored, "<no device>"),
        CONNECTING(AllIcons.RunConfigurations.TestNotRan, "<connecting>"),
        CONNECTED(AllIcons.RunConfigurations.TestPassed, "<unknown>"),
        ERROR(AllIcons.RunConfigurations.TestError, "<error>");
    }
}