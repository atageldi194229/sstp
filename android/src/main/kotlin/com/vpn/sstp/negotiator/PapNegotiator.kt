package com.vpn.sstp.negotiator

import com.vpn.sstp.layer.PppClient
import com.vpn.sstp.misc.DataUnitParsingError
import com.vpn.sstp.misc.informAuthenticationFailed
import com.vpn.sstp.misc.informDataUnitParsingError
import com.vpn.sstp.unit.PapAuthenticateAck
import com.vpn.sstp.unit.PapAuthenticateNak
import com.vpn.sstp.unit.PapAuthenticateRequest
import com.vpn.sstp.unit.PapFrame
import java.nio.charset.Charset


internal fun PppClient.tryReadingPap(frame: PapFrame): Boolean {
    try {
        frame.read(incomingBuffer)
    } catch (e: DataUnitParsingError) {
        parent.informDataUnitParsingError(frame, e)
        kill()
        return false
    }

    if (frame is PapAuthenticateAck || frame is PapAuthenticateNak) {
        if (frame.id != currentAuthRequestId) return false
    }

    return true
}

internal fun PppClient.sendPapRequest() {
    globalIdentifier++
    currentAuthRequestId = globalIdentifier
    val sending = PapAuthenticateRequest()
    sending.id = currentAuthRequestId
    parent.networkSetting.also {
        sending.idFiled = it.HOME_USERNAME.toByteArray(Charset.forName("US-ASCII"))
        sending.passwordFiled = it.HOME_PASSWORD.toByteArray(Charset.forName("US-ASCII"))
    }
    sending.update()
    parent.controlQueue.add(sending)

    authTimer.reset()
}

internal fun PppClient.receivePapAuthenticateAck() {
    val received = PapAuthenticateAck()
    if (!tryReadingPap(received)) return

    if (!isAuthFinished) {
        isAuthFinished = true
    }
}

internal fun PppClient.receivePapAuthenticateNak() {
    val received = PapAuthenticateAck()
    if (!tryReadingPap(received)) return

    if (!isAuthFinished) {
        parent.informAuthenticationFailed(::receivePapAuthenticateNak)
        kill()
        return
    }
}
