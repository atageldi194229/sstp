package com.vpn.sstp.layer

import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import com.vpn.sstp.ControlClient
import com.vpn.sstp.misc.isSame
import com.vpn.sstp.misc.toHexByteArray
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer


internal class IpTerminal(parent: ControlClient) : Terminal(parent) {
    private lateinit var fd: ParcelFileDescriptor

    internal lateinit var ipInput: FileInputStream

    internal lateinit var ipOutput: FileOutputStream

    private fun getPrefixLength(array: ByteArray): Int {
        if (array[0] == 10.toByte()) return 8

        if (array[0] == 172.toByte() && array[1] in 16..31) return 20

        return 16
    }

    private fun getNetworkAddress(array: ByteArray, prefixLength: Int): InetAddress {
        val buffer = ByteBuffer.allocate(4)
        buffer.put(array)

        var num = buffer.getInt(0)
        var mask: Int = -1
        mask = mask.shl(32 - prefixLength)
        num = num and mask
        buffer.putInt(0, num)

        return InetAddress.getByAddress(buffer.array())
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    internal fun initializeTun() {
        val setting = parent.networkSetting
        val builder = parent.builder

        Log.d("ATASAN", "initializeTun started")

        if (setting.PPP_IPv4_ENABLED) {
            if (setting.mgIp.isRejected) {
                Log.d("ATASAN", "IPv4 NCP was rejected")
                throw Exception("IPv4 NCP was rejected")
            }

            if (setting.currentIp.isSame(ByteArray(4))) {
                Log.d("ATASAN", "Null IPv4 address was given")
                throw Exception("Null IPv4 address was given")
            }

            val prefix = if (setting.IP_PREFIX == 0) getPrefixLength(setting.currentIp) else setting.IP_PREFIX
            val hostAddress = InetAddress.getByAddress(setting.currentIp)
            val networkAddress = getNetworkAddress(setting.currentIp, prefix)

            builder.addAddress(hostAddress, prefix)
            builder.addRoute(networkAddress, prefix)

            if (!setting.mgDns.isRejected) {
                val dnsAddress = InetAddress.getByAddress(setting.currentDns)
                builder.addDnsServer(dnsAddress)
            }

            if (!setting.IP_ONLY_LAN) {
                builder.addRoute("0.0.0.0", 0)
            }
        }

        Log.d("ATASAN", "initializeTun 1")

        if (setting.PPP_IPv6_ENABLED) {
            if (setting.mgIpv6.isRejected) {
                Log.d("ATASAN", "IPv6 NCP was rejected")
                throw Exception("IPv6 NCP was rejected")
            }

            if (setting.currentIpv6.isSame(ByteArray(8))) {
                Log.d("ATASAN", "Null IPv6 address was given")
                throw Exception("Null IPv6 address was given")
            }

            val address = ByteArray(16)
            "FE80".toHexByteArray().copyInto(address)
            ByteArray(6).copyInto(address, destinationOffset = 2)
            setting.currentIpv6.copyInto(address, destinationOffset = 8)

            builder.addAddress(InetAddress.getByAddress(address), 64)
            builder.addRoute("fc00::", 7)

            if (!setting.IP_ONLY_ULA) {
                builder.addRoute("::", 0)
            }
        }

        Log.d("ATASAN", "initializeTun started 2")

        builder.setMtu(setting.currentMtu)

        builder.setBlocking(true)

        fd = builder.establish()!!

        ipInput = FileInputStream(fd.fileDescriptor)

        ipOutput = FileOutputStream(fd.fileDescriptor)

        Log.d("ATASAN", "initializeTun started 3")
    }

    override fun release() {
        if (::fd.isInitialized) fd.close()
    }
}
