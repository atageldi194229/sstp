package com.vpn.sstp

import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.vpn.sstp.R
import com.vpn.sstp.layer.*
import com.vpn.sstp.misc.*
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.getBooleanPrefValue
import com.vpn.sstp.preference.accessor.getIntPrefValue
import com.vpn.sstp.preference.accessor.setBooleanPrefValue
import com.vpn.sstp.unit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue


internal class ReconnectionSettings(prefs: SharedPreferences) {
    internal val isEnabled = getBooleanPrefValue(OscPreference.RECONNECTION_ENABLED, prefs)
    private val initialCount = if (isEnabled) getIntPrefValue(OscPreference.RECONNECTION_COUNT, prefs) else 0
    private var currentCount = initialCount
    private val interval = getIntPrefValue(OscPreference.RECONNECTION_INTERVAL, prefs)
    internal val intervalMillis = (interval * 1000).toLong()
    internal val isRetryable: Boolean
        get() = currentCount > 0

    internal fun resetCount() {
        currentCount = initialCount
    }

    internal fun consumeCount() {
        currentCount--
    }

    internal fun generateMessage(): String {
        val triedCount = initialCount - currentCount
        return "Reconnection will be tried (COUNT: $triedCount/$initialCount)"
    }
}


internal class ControlClient(internal val vpnService: SstpVpnService) :
    CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(vpnService.applicationContext)

    internal lateinit var networkSetting: NetworkSetting
    internal lateinit var status: DualClientStatus
    internal lateinit var builder: VpnService.Builder
    internal lateinit var incomingBuffer: IncomingBuffer
    private var observer: NetworkObserver? = null
    internal val controlQueue = LinkedBlockingQueue<Any>()
    internal var logStream: BufferedOutputStream? = null
    internal val reconnectionSettings = ReconnectionSettings(prefs)

    internal lateinit var sslTerminal: SslTerminal
    private lateinit var sstpClient: SstpClient
    private lateinit var pppClient: PppClient
    internal lateinit var ipTerminal: IpTerminal

    private var jobIncoming: Job? = null
    private var jobControl: Job? = null
    private var jobEncapsulate: Job? = null
    private var jobData: Job? = null
    private val isAllJobCompleted: Boolean
        get() {
            arrayOf(jobIncoming, jobControl, jobEncapsulate, jobData).forEach {
                if (it?.isCompleted != true) {
                    return false
                }
            }

            return true
        }

    private val mutex = Mutex()
    private var isClosing = false

    private val handler = CoroutineExceptionHandler { _, exception ->
        if (!isClosing) kill(exception)
    }

    init {
        initialize()
    }

    private fun initialize() {
        networkSetting = NetworkSetting(prefs)
        status = DualClientStatus()
        builder = vpnService.Builder()
        incomingBuffer = IncomingBuffer(networkSetting.BUFFER_INCOMING, this)
        controlQueue.clear()
        isClosing = false
    }

    internal fun kill(exception: Throwable?) {
        launch {
            mutex.withLock {
                if (!isClosing) {
                    isClosing = true
                    controlQueue.add(0)

                    if (exception != null && exception !is SuicideException) {
                        inform("An unexpected event occurred", exception)
                    }

                    // release ConnectivityManager resource
                    observer?.close()

                    // no more packets needed to be retrieved
                    ipTerminal.release()
                    jobData?.cancel()
                    jobEncapsulate?.cancel()

                    // wait until SstpClient.sendLastGreeting() is invoked
                    jobIncoming?.join()

                    // wait until jobControl finishes sending messages
                    withTimeout(10_000) {
                        while (isActive) {
                            if (jobControl?.isCompleted == false) {
                                delay(100)
                            }
                            else break
                        }
                    }

                    // avoid jobControl being stuck with socket
                    sslTerminal.release()

                    // ensure jobControl is completed
                    jobControl?.cancel()


                    if (exception != null && reconnectionSettings.isEnabled) {
                        if (reconnectionSettings.isRetryable) {
                            tryReconnection()
                            return@withLock
                        } else {
                            inform("Exhausted retry counts", null)
                            makeNotification(0, "Failed to reconnect")
                        }
                    }


                    bye()
                }
            }
        }
    }

    private fun bye() {
        inform("Terminate VPN connection", null)
        logStream?.close()
        setBooleanPrefValue(false, OscPreference.HOME_CONNECTOR, prefs)
        vpnService.stopForeground(true)
        vpnService.stopSelf()
    }

    private fun tryReconnection() {
        launch {
            reconnectionSettings.consumeCount()
            makeNotification(0, reconnectionSettings.generateMessage())
            delay(reconnectionSettings.intervalMillis)

            val result = withTimeoutOrNull(10_000) {
                while (true) {
                    if (isAllJobCompleted) {
                        return@withTimeoutOrNull true
                    } else {
                        delay(1)
                    }
                }
            }

            if (result == null) {
                inform("The last session cannot be cleaned up", null)
                makeNotification(0, "Failed to reconnect")
                bye()
            } else {
                initialize()
                run()
            }
        }
    }

    internal fun run() {
        if (networkSetting.LOG_DO_SAVE_LOG && logStream == null) {
            prepareLog()
        }
        inform("Establish VPN connection", null)
        prepareLayers()

        launchJobIncoming()
        launchJobControl()
    }

    private fun launchJobIncoming() {
        jobIncoming = launch(handler) {
            while (isActive) {
                sstpClient.proceed()
                pppClient.proceed()

                if (isClosing) {
                    status.sstp = SstpStatus.CALL_DISCONNECT_IN_PROGRESS_1
                }
            }
        }
    }

    private fun launchJobControl() {
        jobControl = launch(handler) {
            val controlBuffer = ByteBuffer.allocate(CONTROL_BUFFER_SIZE)

            while (isActive) {
                val candidate = controlQueue.take()
                if (candidate == 0) break

                controlBuffer.clear()
                when (candidate) {
                    is ControlPacket -> {
                        candidate.write(controlBuffer)
                    }

                    is PppFrame -> {
                        controlBuffer.putShort(SSTP_PACKET_TYPE_DATA)
                        controlBuffer.putShort((candidate._length + 8).toShort())
                        candidate.write(controlBuffer)
                    }

                    else -> throw Exception("Invalid Control Unit")
                }
                controlBuffer.flip()

                sslTerminal.send(controlBuffer)
            }
        }
    }

    private fun launchJobEncapsulate(channel: Channel<ByteBuffer>) {
        jobEncapsulate = launch(handler) { // buffer packets
            val dataBuffer = ByteBuffer.allocate(networkSetting.BUFFER_OUTGOING)
            val minCapacity = networkSetting.currentMtu + 8

            val ipv4Version: Int = (0x4).shl(28)
            val ipv6Version: Int = (0x6).shl(28)
            val versionMask: Int = (0xF).shl(28)

            var polled: ByteBuffer?

            fun encapsulate(src: ByteBuffer): Boolean // true if data protocol is enabled
            {
                val header = src.getInt(0)
                val version = when (header and versionMask) {
                    ipv4Version -> {
                        if (!networkSetting.PPP_IPv4_ENABLED) return false
                        PPP_PROTOCOL_IP
                    }

                    ipv6Version -> {
                        if (!networkSetting.PPP_IPv6_ENABLED) return false
                        PPP_PROTOCOL_IPV6
                    }

                    else -> throw Exception("Invalid data protocol was detected")
                }

                dataBuffer.putShort(SSTP_PACKET_TYPE_DATA)
                dataBuffer.putShort((src.limit() + 8).toShort())
                dataBuffer.putShort(PPP_HEADER)
                dataBuffer.putShort(version)
                dataBuffer.put(src)

                return true
            }


            while (isActive) {
                dataBuffer.clear()
                if (!encapsulate(channel.receive())) continue

                while (isActive) {
                    polled = channel.poll()
                    if (polled != null) {
                        encapsulate(polled)
                        if (dataBuffer.remaining() < minCapacity) break
                    } else {
                        break
                    }
                }

                dataBuffer.flip()
                sslTerminal.send(dataBuffer)
            }
        }
    }

    internal fun launchJobData() {
        jobData = launch(handler) {
            val channel = Channel<ByteBuffer>(0)
            val readBufferAlpha = ByteBuffer.allocate(networkSetting.currentMtu)
            val readBufferBeta = ByteBuffer.allocate(networkSetting.currentMtu)
            var isBlockingAlpha = true

            launchJobEncapsulate(channel)

            suspend fun read(dst: ByteBuffer) {
                dst.clear()
                dst.position(
                    ipTerminal.ipInput.read(
                        dst.array(),
                        0,
                        networkSetting.currentMtu
                    )
                )
                dst.flip()

                channel.send(dst)
            }

            while (isActive) {
                isBlockingAlpha = if (isBlockingAlpha) {
                    read(readBufferAlpha)
                    false
                } else {
                    read(readBufferBeta)
                    true
                }
            }
        }
    }

    internal fun attachNetworkObserver() {
        observer = NetworkObserver(this)
    }

    private fun prepareLayers() {
        sslTerminal = SslTerminal(this)
        sstpClient = SstpClient(this)
        pppClient = PppClient(this)
        ipTerminal = IpTerminal(this)
    }

    private fun prepareLog() {
        val currentTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val filename = "log_osc_${currentTime}.txt"
        val uri = Uri.parse(networkSetting.LOG_DIR)
        DocumentFile.fromTreeUri(vpnService, uri)!!.createFile("text/plain", filename).also {
            logStream = BufferedOutputStream(vpnService.contentResolver.openOutputStream(it!!.uri))
        }
    }

    private fun makeNotification(id: Int, message: String) {
        val builder = NotificationCompat.Builder(vpnService.applicationContext, vpnService.CHANNEL_ID).also {
            it.setSmallIcon(R.drawable.ic_baseline_vpn_lock_24)
            it.setContentText(message)
            it.priority = NotificationCompat.PRIORITY_DEFAULT
            it.setAutoCancel(true)
        }

        NotificationManagerCompat.from(vpnService.applicationContext).also {
            it.notify(id, builder.build())
        }

        inform(message, null)
    }
}
