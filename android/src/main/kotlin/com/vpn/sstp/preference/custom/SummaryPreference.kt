package com.vpn.sstp.preference.custom

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.getStringPrefValue


internal abstract class SummaryPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    abstract val oscPreference: OscPreference
    abstract val preferenceTitle: String
    protected open val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == oscPreference.name) {
            updateSummary()
        }
    }

    protected open val summaryValue: String
        get() = getStringPrefValue(oscPreference, sharedPreferences)

    private fun updateSummary() {
        summary = summaryValue
    }

    override fun onAttached() {
        super.onAttached()

        title = preferenceTitle
        updateSummary()

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDetached() {
        super.onDetached()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)

        holder?.findViewById(android.R.id.summary)?.also {
            it as TextView
            it.maxLines = Int.MAX_VALUE
        }
    }
}

internal class HomeStatusPreference(context: Context, attrs: AttributeSet) : SummaryPreference(context, attrs) {
    override val oscPreference = OscPreference.HOME_STATUS
    override val preferenceTitle = "Current Status"
    override val summaryValue: String
        get() {
            val status = getStringPrefValue(oscPreference, sharedPreferences)

            return if (status.isEmpty()) {
                "[No Connection Established]"
            } else {
                status
            }
        }
}
