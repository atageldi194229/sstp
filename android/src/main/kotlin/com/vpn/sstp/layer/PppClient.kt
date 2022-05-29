package com.vpn.sstp.layer

import com.vpn.sstp.ControlClient
import com.vpn.sstp.misc.*
import com.vpn.sstp.negotiator.*
import com.vpn.sstp.unit.*


internal enum class LcpState {
    REQ_SENT, ACK_RCVD, ACK_SENT, OPENED
}

internal enum class IpcpState {
    REQ_SENT, ACK_RCVD, ACK_SENT, OPENED
}

internal enum class Ipv6cpState {
    REQ_SENT, ACK_RCVD, ACK_SENT, OPENED
}

internal class PppClient(parent: ControlClient) : Client(parent) {
    internal var globalIdentifier: Byte = -1
    internal var currentLcpRequestId: Byte = 0
    internal var currentIpcpRequestId: Byte = 0
    internal var currentIpv6cpRequestId: Byte = 0
    internal var currentAuthRequestId: Byte = 0

    internal val lcpTimer = Timer(3_000L)
    internal val lcpCounter = Counter(10)
    internal var lcpState = LcpState.REQ_SENT
    private var isInitialLcp = true

    internal val authTimer = Timer(parent.networkSetting.PPP_AUTH_TIMEOUT * 1000L)
    internal var isAuthFinished = false
    private var isInitialAuth = true

    internal val ipcpTimer = Timer(3_000L)
    internal val ipcpCounter = Counter(10)
    internal var ipcpState = IpcpState.REQ_SENT
    private var isInitialIpcp = true

    internal val ipv6cpTimer = Timer(3_000L)
    internal val ipv6cpCounter = Counter(10)
    internal var ipv6cpState = Ipv6cpState.REQ_SENT
    private var isInitialIpv6cp = true

    internal val echoTimer = Timer(60_000L)
    internal val echoCounter = Counter(1)

    private val hasIncoming: Boolean
        get() = incomingBuffer.pppLimit > incomingBuffer.position()

    private val canStartPpp: Boolean
        get() {
            if (status.sstp == SstpStatus.CLIENT_CALL_CONNECTED) return true

            if (status.sstp == SstpStatus.CLIENT_CONNECT_ACK_RECEIVED) return true

            return false
        }

    private fun readAsDiscarded() {
        incomingBuffer.position(incomingBuffer.pppLimit)
    }

    private fun proceedLcp() {
        if (lcpTimer.isOver) {
            sendLcpConfigureRequest()
            if (lcpState == LcpState.ACK_RCVD) lcpState = LcpState.REQ_SENT
            return
        }

        if (!hasIncoming) return

        if  (incomingBuffer.getShort() != PPP_PROTOCOL_LCP) readAsDiscarded()
        else {
            when (incomingBuffer.getByte()) {
                LCP_CODE_CONFIGURE_REQUEST -> receiveLcpConfigureRequest()

                LCP_CODE_CONFIGURE_ACK -> receiveLcpConfigureAck()

                LCP_CODE_CONFIGURE_NAK -> receiveLcpConfigureNak()

                LCP_CODE_CONFIGURE_REJECT -> receiveLcpConfigureReject()

                LCP_CODE_TERMINATE_REQUEST, LCP_CODE_CODE_REJECT -> {
                    parent.informInvalidUnit(::proceedLcp)
                    kill()
                    return
                }

                else -> readAsDiscarded()
            }
        }

        incomingBuffer.forget()
    }

    private fun proceedPap() {
        if (authTimer.isOver) {
            parent.informTimerOver(::proceedPap)
            kill()
            return
        }

        if (!hasIncoming) return

        if  (incomingBuffer.getShort() != PPP_PROTOCOL_PAP) readAsDiscarded()
        else {
            when (incomingBuffer.getByte()) {
                PAP_CODE_AUTHENTICATE_ACK -> receivePapAuthenticateAck()

                PAP_CODE_AUTHENTICATE_NAK -> receivePapAuthenticateNak()

                else -> readAsDiscarded()
            }
        }

        incomingBuffer.forget()
    }

    private fun proceedChap() {
        if (authTimer.isOver) {
            parent.informTimerOver(::proceedChap)
            kill()
            return
        }

        if (!hasIncoming) return

        if (incomingBuffer.getShort() != PPP_PROTOCOL_CHAP) readAsDiscarded()
        else {
            when (incomingBuffer.getByte()) {
                CHAP_CODE_CHALLENGE -> receiveChapChallenge()

                CHAP_CODE_SUCCESS -> receiveChapSuccess()

                CHAP_CODE_FAILURE -> receiveChapFailure()

                else -> readAsDiscarded()
            }
        }

        incomingBuffer.forget()
    }

    private fun proceedIpcp() {
        if (ipcpTimer.isOver) {
            sendIpcpConfigureRequest()
            if (ipcpState == IpcpState.ACK_RCVD) ipcpState = IpcpState.REQ_SENT
            return
        }

        if (!hasIncoming) return

        when (incomingBuffer.getShort()) {
            PPP_PROTOCOL_LCP -> {
                if (incomingBuffer.getByte() == LCP_CODE_PROTOCOL_REJECT) {
                    receiveLcpProtocolReject(PPP_PROTOCOL_IPCP)
                } else readAsDiscarded()
            }

            PPP_PROTOCOL_IPCP -> {
                when (incomingBuffer.getByte()) {
                    IPCP_CODE_CONFIGURE_REQUEST -> receiveIpcpConfigureRequest()

                    IPCP_CODE_CONFIGURE_ACK -> receiveIpcpConfigureAck()

                    IPCP_CODE_CONFIGURE_NAK -> receiveIpcpConfigureNak()

                    IPCP_CODE_CONFIGURE_REJECT -> receiveIpcpConfigureReject()

                    IPCP_CODE_TERMINATE_REQUEST, IPCP_CODE_CODE_REJECT -> {
                        parent.informInvalidUnit(::proceedIpcp)
                        kill()
                        return
                    }

                    else -> readAsDiscarded()
                }
            }

            else -> readAsDiscarded()
        }

        incomingBuffer.forget()
    }

    private fun proceedIpv6cp() {
        if (ipv6cpTimer.isOver) {
            sendIpv6cpConfigureRequest()
            if (ipv6cpState == Ipv6cpState.ACK_RCVD) ipv6cpState = Ipv6cpState.REQ_SENT
            return
        }

        if (!hasIncoming) return

        when (incomingBuffer.getShort()) {
            PPP_PROTOCOL_LCP -> {
                if (incomingBuffer.getByte() == LCP_CODE_PROTOCOL_REJECT) {
                    receiveLcpProtocolReject(PPP_PROTOCOL_IPV6CP)
                }
            }

            PPP_PROTOCOL_IPV6CP -> {
                when (incomingBuffer.getByte()) {
                    IPv6CP_CODE_CONFIGURE_REQUEST -> receiveIpv6cpConfigureRequest()

                    IPv6CP_CODE_CONFIGURE_ACK -> receiveIpv6cpConfigureAck()

                    IPv6CP_CODE_CONFIGURE_NAK -> receiveIpv6cpConfigureNak()

                    IPv6CP_CODE_CONFIGURE_REJECT -> receiveIpv6cpConfigureReject()

                    IPv6CP_CODE_TERMINATE_REQUEST, IPv6CP_CODE_CODE_REJECT -> {
                        parent.informInvalidUnit(::proceedIpv6cp)
                        kill()
                        return
                    }

                    else -> readAsDiscarded()
                }
            }

            else -> readAsDiscarded()
        }

        incomingBuffer.forget()
    }

    private fun proceedNetwork() {
        if (echoTimer.isOver) sendLcpEchoRequest()

        if (!hasIncoming) return
        else {
            echoTimer.reset()
            echoCounter.reset()
        }

        when (incomingBuffer.getShort()) {
            PPP_PROTOCOL_LCP -> {
                when (incomingBuffer.getByte()) {
                    LCP_CODE_ECHO_REQUEST -> receiveLcpEchoRequest()

                    LCP_CODE_ECHO_REPLY -> receiveLcpEchoReply()

                    else -> {
                        parent.informInvalidUnit(::proceedNetwork)
                        kill()
                        return
                    }
                }
            }

            PPP_PROTOCOL_CHAP -> {
                when (incomingBuffer.getByte()) {
                    CHAP_CODE_CHALLENGE -> receiveChapChallenge()

                    CHAP_CODE_SUCCESS -> receiveChapSuccess()

                    CHAP_CODE_FAILURE -> receiveChapFailure()

                    else -> readAsDiscarded()
                }
            }

            PPP_PROTOCOL_IP, PPP_PROTOCOL_IPV6 -> incomingBuffer.convey()

            else -> readAsDiscarded()
        }

        incomingBuffer.forget()
    }

    override fun proceed() {
        if (!canStartPpp) return

        when (status.ppp) {
            PppStatus.NEGOTIATE_LCP -> {
                if (isInitialLcp) {
                    sendLcpConfigureRequest()
                    isInitialLcp = false
                }

                proceedLcp()
                if (lcpState == LcpState.OPENED) status.ppp = PppStatus.AUTHENTICATE
            }

            PppStatus.AUTHENTICATE -> {
                when (networkSetting.currentAuth) {
                    AuthSuite.PAP -> {
                        if (isInitialAuth) {
                            sendPapRequest()
                            isInitialAuth = false
                        }

                        proceedPap()
                        if (isAuthFinished) {
                            status.ppp = if (networkSetting.PPP_IPv4_ENABLED) {
                                PppStatus.NEGOTIATE_IPCP
                            } else {
                                PppStatus.NEGOTIATE_IPV6CP
                            }
                        }
                    }

                    AuthSuite.MSCHAPv2 -> {
                        if (isInitialAuth) {
                            networkSetting.chapSetting = ChapSetting()
                            authTimer.reset()
                            isInitialAuth = false
                        }

                        proceedChap()
                        if (isAuthFinished) {
                            status.ppp = if (networkSetting.PPP_IPv4_ENABLED) {
                                PppStatus.NEGOTIATE_IPCP
                            } else {
                                PppStatus.NEGOTIATE_IPV6CP
                            }
                        }
                    }
                }
            }

            PppStatus.NEGOTIATE_IPCP -> {
                if (isInitialIpcp) {
                    sendIpcpConfigureRequest()
                    isInitialIpcp = false
                }

                proceedIpcp()
                if (ipcpState == IpcpState.OPENED) {
                    if (networkSetting.PPP_IPv6_ENABLED) {
                        status.ppp = PppStatus.NEGOTIATE_IPV6CP
                    } else startNetworking()
                }
            }

            PppStatus.NEGOTIATE_IPV6CP -> {
                if (isInitialIpv6cp) {
                    sendIpv6cpConfigureRequest()
                    isInitialIpv6cp = false
                }

                proceedIpv6cp()
                if (ipv6cpState == Ipv6cpState.OPENED) startNetworking()
            }

            PppStatus.NETWORK -> proceedNetwork()
        }
    }

    private fun startNetworking() {
        parent.attachNetworkObserver()

        parent.ipTerminal.also {
            try {
                it.initializeTun()
            } catch (e: Exception) {
                parent.inform("Failed to create VPN interface", e)
                kill()
                return
            }
        }

        status.ppp = PppStatus.NETWORK
        parent.reconnectionSettings.resetCount()
        parent.launchJobData()
        echoTimer.reset()
    }

    internal fun kill() {
        status.sstp = SstpStatus.CALL_DISCONNECT_IN_PROGRESS_1
        parent.inform("PPP layer turned down", null)
    }
}
