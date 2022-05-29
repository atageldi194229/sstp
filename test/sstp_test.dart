import 'package:flutter_test/flutter_test.dart';
import 'package:sstp/sstp.dart';
import 'package:sstp/sstp_platform_interface.dart';
import 'package:sstp/sstp_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockSstpPlatform 
    with MockPlatformInterfaceMixin
    implements SstpPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final SstpPlatform initialPlatform = SstpPlatform.instance;

  test('$MethodChannelSstp is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelSstp>());
  });

  test('getPlatformVersion', () async {
    Sstp sstpPlugin = Sstp();
    MockSstpPlatform fakePlatform = MockSstpPlatform();
    SstpPlatform.instance = fakePlatform;
  
    expect(await sstpPlugin.getPlatformVersion(), '42');
  });
}
