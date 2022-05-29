package com.vpn.sstp.fragment

import android.app.Activity
import android.content.*
import android.net.*
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.vpn.sstp.R
import com.vpn.sstp.*
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.*
import com.vpn.sstp.preference.checkPreferences
import com.vpn.sstp.preference.custom.HomeConnectorPreference
import com.vpn.sstp.preference.toastInvalidSetting


class HomeFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachConnectorListener()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            startVpnService(ACTION_VPN_CONNECT)
        }
    }

    private fun startVpnService(action: String) {
        context?.startService(Intent(context, SstpVpnService::class.java).setAction(action))
    }

    private fun attachConnectorListener() {
        findPreference<HomeConnectorPreference>(OscPreference.HOME_CONNECTOR.name)!!.also {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newState ->
                if (newState == true) {
                    checkPreferences(preferenceManager.sharedPreferences)?.also { message ->
                        toastInvalidSetting(message, context)
                        return@OnPreferenceChangeListener false
                    }

                    VpnService.prepare(context)?.also { intent ->
                        startActivityForResult(intent, 0)
                    } ?: onActivityResult(0, Activity.RESULT_OK, null)
                } else {
                    startVpnService(ACTION_VPN_DISCONNECT)
                }

                true
            }
        }
    }
}
