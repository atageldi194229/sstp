package com.vpn.sstp

import android.app.Activity.RESULT_OK
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.annotation.NonNull
import androidx.preference.PreferenceManager

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import com.vpn.sstp.ACTION_VPN_CONNECT
import com.vpn.sstp.SstpVpnService
import com.vpn.sstp.preference.OscPreference
import com.vpn.sstp.preference.accessor.setBooleanPrefValue
import com.vpn.sstp.preference.accessor.setIntPrefValue
import com.vpn.sstp.preference.accessor.setSetPrefValue
import com.vpn.sstp.preference.accessor.setStringPrefValue

/** SstpPlugin */
class SstpPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var activityBinding: ActivityPluginBinding
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  
  private lateinit var context: Context


  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    // Register method channel.
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "sstp")
    channel.setMethodCallHandler(this)
    
    // Get context
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when(call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }

      "connect" -> {
        val intent = VpnService.prepare(activityBinding.activity.applicationContext)
        if (intent != null) {
          var listener: PluginRegistry.ActivityResultListener? = null
          listener = PluginRegistry.ActivityResultListener { req, res, _ ->
            result.success(req == 0 && res == RESULT_OK)
            listener?.let { activityBinding.removeActivityResultListener(it) }
            true
          }
          activityBinding.addActivityResultListener(listener)
          activityBinding.activity.startActivityForResult(intent, 0)
        } else {
          // Already prepared if intent is null.
          // result.success(true)

          val args = call.arguments as Map<*, *>

          val hostname = args["Hostname"] as String // "88.81.42.84"
          val port = args["Port"] as Int // 1195
          val username = args["Username"] as String // "vpn"
          val password = args["Password"] as String // "vpn"
          
          val prefs = PreferenceManager.getDefaultSharedPreferences(context)
          
          setBooleanPrefValue(true, OscPreference.SSL_DO_SELECT_SUITES, prefs)
          setBooleanPrefValue(false, OscPreference.SSL_DO_VERIFY, prefs)
          setSetPrefValue(setOf("TLS_AES_256_GCM_SHA384"), OscPreference.SSL_SUITES, prefs)
          setStringPrefValue(hostname, OscPreference.HOME_HOSTNAME, prefs)
          setIntPrefValue(port, OscPreference.SSL_PORT, prefs)
          setStringPrefValue(username, OscPreference.HOME_USERNAME, prefs)
          setStringPrefValue(password, OscPreference.HOME_PASSWORD, prefs)

          if (context == null) {
            Log.d("ATASAN", "context is null")
          } else {
            Log.d("ATASAN", "everything is ok")
            Log.d("ATASAN", "service starting")

            context.startService(
              Intent(context, SstpVpnService::class.java).setAction(
                ACTION_VPN_CONNECT))

            Log.d("ATASAN", "service started")
          }

          result.success("connect")
        }

      }

      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityBinding = binding
  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activityBinding = binding
  }

  override fun onDetachedFromActivity() {
    TODO("Not yet implemented")
  }
}
