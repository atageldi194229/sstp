import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'sstp_method_channel.dart';

abstract class SstpPlatform extends PlatformInterface {
  /// Constructs a SstpPlatform.
  SstpPlatform() : super(token: _token);

  static final Object _token = Object();

  static SstpPlatform _instance = MethodChannelSstp();

  /// The default instance of [SstpPlatform] to use.
  ///
  /// Defaults to [MethodChannelSstp].
  static SstpPlatform get instance => _instance;
  
  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [SstpPlatform] when
  /// they register themselves.
  static set instance(SstpPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<String?> connect({
    required String hostname,
    required int port,
    required String username,
    required String password,
  }) {
    throw UnimplementedError('connect() has not been implemented.');
  }
}
