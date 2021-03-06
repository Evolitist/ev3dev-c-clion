package com.evolitist.ev3c.component

import com.google.common.collect.ImmutableSet
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.SshPasswordProvider
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.process.SshExecProcess
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

class Ev3devConnector {
    private val deviceSelection = AtomicReference(DeviceSelection.EMPTY)
    @Volatile
    private var conn: ConnectionBuilder? = null
    @Volatile
    var addresses: Set<InetAddress> = setOf(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 0, 1)))
        private set
    @Volatile
    var reachableAddresses: Set<InetAddress> = mutableSetOf()
        private set
    private val connectorThread = Thread {
        var newAddresses: Set<InetAddress>
        while (shouldRun) {
            try {
                addresses = addresses + InetAddress.getAllByName("ev3dev.local")
                newAddresses = addresses.filter { it.isReachable(50) }.toSet()
                if (reachableAddresses != newAddresses) {
                    reachableAddresses = newAddresses
                    refreshDeviceSelection(reachableAddresses.sortedBy { it.hostAddress })
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
    var sftp: SftpChannel? = null
        private set

    private val listeners = AtomicReference(ImmutableSet.of<() -> Unit>())
    private val sftpListeners = AtomicReference(ImmutableSet.of<(SftpChannel?) -> Unit>())

    init {
        if (!connectorThread.isAlive) {
            connectorThread.start()
        }
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
    private fun refreshDeviceSelection(addresses: List<InetAddress>) {
        deviceSelection.updateAndGet { old -> old.withDevices(addresses) }
        fireChangeEvent()
    }

    fun connectTo(device: InetAddress?) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Connecting...", true) {
            private var shouldRun = true
            private var tries = 0

            override fun run(p0: ProgressIndicator) {
                tries = 0
                runInternal(p0)
            }

            private fun runInternal(p0: ProgressIndicator) {
                try {
                    if (device != null) {
                        conn = ConnectionBuilder(device.hostAddress, 22)
                            .withUsername("robot")
                            .withPassword("maker")
                            .withSshPasswordProvider(object : SshPasswordProvider {
                                override fun askUserForPassword(message: String) = arrayOf("maker")
                                override fun getPassphrase() = "maker"
                                override fun getPassword() = "maker"
                                override fun promptYesNo(message: String) = true
                                override fun showMessage(message: String) {}
                                override fun promptKeyboardInteractive(
                                    destination: String?,
                                    name: String?,
                                    instruction: String?,
                                    prompt: Array<out String>,
                                    echo: BooleanArray
                                ) = arrayOf("maker")
                            })
                        sftp = conn!!.openSftpChannel()
                    } else {
                        sftp = null
                        conn = null
                    }
                    fireSftpUpdateEvent()
                } catch (e: Exception) {
                    if (shouldRun) {
                        if (tries < 3) {
                            tries++
                            runInternal(p0)
                        } else {
                            throw e
                        }
                    }
                }
            }

            override fun onCancel() {
                super.onCancel()
                shouldRun = false
            }
        })
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
            for (listener in sftpListeners.get()) {
                try {
                    listener.invoke(sftp)
                } catch (e: Exception) {
                }
            }
        }
    }

    operator fun invoke(command: String, timeout: Int = 0): SshExecProcess {
        return conn!!.execBuilder(command).execute(timeout)
    }

    companion object {
        @JvmStatic
        fun getInstance(): Ev3devConnector = ServiceManager.getService(Ev3devConnector::class.java)
    }
}

internal class DeviceSelection private constructor(val devices: List<InetAddress>, val selection: InetAddress?) {
    fun withDevices(newDevices: List<InetAddress>): DeviceSelection {
        //val newList = newDevices.filter { it is Inet4Address }
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
