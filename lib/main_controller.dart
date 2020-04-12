import 'package:flutter/services.dart';

enum HotwordDetectorState {
    Uninit, InitProcess, Initialized, Failed
}
class MainController
{
    HotwordDetectorState hotwordDetectorState = HotwordDetectorState.Uninit;
    void initHotwordDetection() async {
        if (hotwordDetectorState != HotwordDetectorState.Uninit) {
            if (initHotwordDetectionCallback != null) initHotwordDetectionCallback(hotwordDetectorState.toString());
            return;
        }

        const String METHOD_CHANNEL = 'com.ican.thinclient/stream';
        var method = 'initHotwordDetection';
        hotwordDetectorState = HotwordDetectorState.InitProcess;
        String result = hotwordDetectorState.toString();

        try {
            await MethodChannel(METHOD_CHANNEL).invokeMethod(method);
            hotwordDetectorState = HotwordDetectorState.Initialized;
            result = hotwordDetectorState.toString();
        } on PlatformException catch (e) {
            hotwordDetectorState = HotwordDetectorState.Failed;
            result = "Failed to get '${method}' from channel '${METHOD_CHANNEL}' with message '${e.message}'.";
        }

        if (initHotwordDetectionCallback != null) initHotwordDetectionCallback(result);
    }
    Function initHotwordDetectionCallback;
    Function hotwordDetectionCallback;

    void startSpeechRecognition() async {

    }
    Function speechRecognitionSuccessCallback;

    void setUserQuery(String message) async {

    }
    Function setUserQueryCallback;

    void initDataServer(String configuration, String loginId) {
        subscribeToServer(configuration, loginId);
    }

    void subscribeToServer(String configuration, String loginId) {

    }
    Function serverCallback;

    void requestBatteryLevel() async {
        const String METHOD_CHANNEL = 'com.ican.thinclient/stream';
        var method = 'getBatteryLevel';
        String batteryLevel;

        try {
            final int result = await MethodChannel(METHOD_CHANNEL).invokeMethod(method);
            batteryLevel = 'Battery level at $result % .';
        } on PlatformException catch (e) {
            batteryLevel = "Failed to get battery level: '${e.message}'.";
        }

        if (batteryLevelCallback != null)
            batteryLevelCallback(batteryLevel);
    }
    Function batteryLevelCallback;
}