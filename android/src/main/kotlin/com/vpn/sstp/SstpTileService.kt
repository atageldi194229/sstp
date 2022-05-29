package com.vpn.sstp

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.getBooleanPrefValue
import com.vpn.sstp.preference.checkPreferences


@RequiresApi(Build.VERSION_CODES.N)
internal class SstpTileService : TileService() {
    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    private val listener by lazy {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == OscPreference.ROOT_STATE.name) {
                updateTileState()
            }
        }
    }

    private val rootState: Boolean
        get() = getBooleanPrefValue(OscPreference.ROOT_STATE, prefs)

    private val isVpnPrepared: Boolean
        get() = VpnService.prepare(applicationContext) == null

    private fun invalidateTileState() {
        qsTile.state = Tile.STATE_UNAVAILABLE
        qsTile.updateTile()
    }

    private fun updateTileState() {
        qsTile.state = if (rootState) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        qsTile.updateTile()
    }

    private fun flipTileState() {
        qsTile.state = if (qsTile.state == Tile.STATE_ACTIVE) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }

        qsTile.updateTile()
    }

    private fun initializeState() {
        if (isVpnPrepared) {
            updateTileState()
        } else {
            invalidateTileState()
        }
    }

    override fun onTileAdded() {
        initializeState()
    }

    override fun onStartListening() {
        initializeState()

        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStopListening() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun startVpnService(action: String) {
        val intent = Intent(applicationContext, SstpVpnService::class.java).setAction(action)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onClick() {
        if (!isVpnPrepared || checkPreferences(prefs) != null) return

        flipTileState()

        when (qsTile.state) {
            Tile.STATE_ACTIVE -> startVpnService(ACTION_VPN_CONNECT)
            Tile.STATE_INACTIVE -> startVpnService(ACTION_VPN_DISCONNECT)
        }
    }
}
