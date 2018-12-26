package com.evolitist.ev3c.component

import com.google.common.collect.ImmutableSet
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.process.SshExecProcess
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.SwingUtilities

class Ev3devConnector(private val project: Project) {
    private val deviceSelection = AtomicReference(DeviceSelection.EMPTY)
    @Volatile
    private var conn: ConnectionBuilder? = null
    @Volatile
    var addresses: List<InetAddress> = emptyList()
        private set
    private val connectorThread = Thread {
        var newAddresses: List<InetAddress>
        while (shouldRun) {
            try {
                newAddresses = InetAddress.getAllByName("ev3dev.local").toList()
                if (addresses != newAddresses) {
                    addresses = newAddresses
                    refreshDeviceSelection()
                }
            } catch (e: Exception) {

            } finally {
                Thread.sleep(1000)
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

    fun getConnectedDevices(): Collection<InetAddress> {
        return deviceSelection.get().devices
    }

    fun getSelectedDevice(): InetAddress? {
        return deviceSelection.get().selection
    }

    fun setSelectedDevice(device: InetAddress?) {
        deviceSelection.updateAndGet { old -> old.withSelection(device?.hostAddress) }
        fireChangeEvent()
    }

    @Synchronized
    private fun refreshDeviceSelection() {
        deviceSelection.updateAndGet { old -> old.withDevices(addresses) }
        fireChangeEvent()
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
        return conn!!.execBuilder(command).execute(timeout)
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

internal class DeviceSelection private constructor(val devices: List<InetAddress>, val selection: InetAddress?) {
    val sftpChannel: SftpChannel? by lazy {
        if (selection != null)
            ConnectionBuilder(selection.hostAddress, 22)
                    .withUsername("robot")
                    .withPassword("maker")
                    .openSftpChannel()
        else
            null
    }

    fun withDevices(newList: List<InetAddress>): DeviceSelection {
        val newDevices = if (SystemInfo.isWindows)
            newList.filter { it is Inet6Address }
        else
            newList.filter { it is Inet4Address }
        val selectedId = selection?.hostAddress
        val selectedDevice = findById(newDevices, selectedId)
        val selectionOrDefault = selectedDevice.orElse(if (newDevices.isNotEmpty()) newDevices[0] else null)
        return DeviceSelection(List(newDevices.size) { newDevices[it] }, selectionOrDefault)
    }

    fun withSelection(id: String?): DeviceSelection {
        return DeviceSelection(devices, findById(devices, id).orElse(selection))
    }

    companion object {
        val EMPTY = DeviceSelection(listOf(), null)

        private fun findById(candidates: List<InetAddress>, id: String?): Optional<InetAddress> {
            return if (id == null) Optional.empty() else candidates.stream().filter { d -> d.hostAddress == id }.findFirst()
        }
    }
}
