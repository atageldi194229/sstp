package com.vpn.sstp.preference.custom

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.getBooleanPrefValue


internal class HomeConnectorPreference(context: Context, attrs: AttributeSet) : SwitchPreferenceCompat(context, attrs) {
    private val oscPreference = OscPreference.HOME_CONNECTOR
    private var listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == oscPreference.name) {
            isChecked = getBooleanPrefValue(oscPreference, prefs)
        }
    }

    override fun onAttached() {
        super.onAttached()

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDetached() {
        super.onDetached()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
