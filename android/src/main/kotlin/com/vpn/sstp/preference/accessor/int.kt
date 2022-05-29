package com.vpn.sstp.preference.accessor

import android.content.SharedPreferences
import com.vpn.sstp.DEFAULT_MRU
import com.vpn.sstp.DEFAULT_MTU
import com.vpn.sstp.preference.OscPreference


internal fun getIntPrefValue(key: OscPreference, prefs: SharedPreferences): Int {
    val defaultValue = when (key) {
        OscPreference.SSL_PORT -> 443
        OscPreference.PPP_MRU -> DEFAULT_MRU
        OscPreference.PPP_MTU -> DEFAULT_MTU
        OscPreference.PPP_AUTH_TIMEOUT -> 3
        OscPreference.IP_PREFIX -> 0
        OscPreference.RECONNECTION_COUNT -> 3
        OscPreference.RECONNECTION_INTERVAL -> 10
        OscPreference.BUFFER_INCOMING, OscPreference.BUFFER_OUTGOING -> 16384
        else -> throw NotImplementedError()
    }

    return prefs.getString(key.name, null)?.toIntOrNull() ?: defaultValue
}

internal fun setIntPrefValue(value: Int, key: OscPreference, prefs: SharedPreferences) {
    prefs.edit().also {
        it.putString(key.name, value.toString())
        it.apply()
    }
}
