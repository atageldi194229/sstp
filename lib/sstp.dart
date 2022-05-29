
import 'sstp_platform_interface.dart';

class Sstp {
  Future<String?> getPlatformVersion() {
    return SstpPlatform.instance.getPlatformVersion();
  }

  Future<String?> connect({
    required String hostname,
    int port = 443,
    String username = "vpn",
    String password = "vpn",
  }) {
    return SstpPlatform.instance.connect(
      hostname: hostname,
      port: port,
      username: username,
      password: password,
    );
  }
}
