import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:sstp/sstp.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _sstpPlugin = Sstp();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  tryCatch(Function fn, [dynamic defaultValue = "Default value"]) async {
    try {
      return await fn() ?? defaultValue;
    } on PlatformException {
      return 'Failed to get platform version.';
    }
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _sstpPlugin.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    platformVersion = await tryCatch(
        _sstpPlugin.getPlatformVersion, 'Unknown platform version');

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_platformVersion\n'),
              ElevatedButton(
                onPressed: () => tryCatch(() => _sstpPlugin.connect(
                      hostname: '79.164.218.31',
                      port: 995,
                    )),
                child: const Text("Connect"),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
