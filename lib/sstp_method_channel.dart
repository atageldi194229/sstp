import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'sstp_platform_interface.dart';

/// An implementation of [SstpPlatform] that uses method channels.
class MethodChannelSstp extends SstpPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('sstp');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<String?> connect({
    required String hostname,
    required int port,
    required String username,
    required String password,
  }) async {
    await methodChannel.invokeMethod<String>('connect', {
      'Hostname': hostname,
      'Port': port,
      'Username': username,
      'Password': password,
    });
    return "connect";
  }
}
