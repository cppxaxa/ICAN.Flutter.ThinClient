import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:thinclient006/main_controller.dart';

class MainHomePage extends StatefulWidget {
    MainHomePage({Key key}) : super(key: key);

    @override
    _MainHomePageState createState() => _MainHomePageState();
}

class _MainHomePageState extends State<MainHomePage> {
    MainController controller = MainController();
    String _batteryLevel = 'Unknown battery level.';
    String _hotwordService = 'Hotword service';

    Future<void> requestBatteryLevelCmd() async {
        controller.batteryLevelCallback = setBatteryLevel;
        controller.requestBatteryLevel();
    }
    void setBatteryLevel(String batteryLevel) {
        setState(() {
            _batteryLevel = batteryLevel;
        });
    }

    Future<void> initHotwordDetectionCmd() async {
        controller.initHotwordDetectionCallback = setHotwordDetectionState;
        controller.initHotwordDetection();
    }
    void setHotwordDetectionState(String message) {
        setState(() {
            _hotwordService = message;
        });
    }

    @override
    Widget build(BuildContext context) {
        return Material(
            child: Center(
                child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                        RaisedButton(
                            child: Text('Init Hotword Detection'),
                            onPressed: initHotwordDetectionCmd,
                        ),
                        Text(_hotwordService),
                        RaisedButton(
                            child: Text('Get Battery Level'),
                            onPressed: requestBatteryLevelCmd,
                        ),
                        Text(_batteryLevel),
                    ],
                ),
            ),
        );
    }
}