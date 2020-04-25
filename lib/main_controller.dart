import 'dart:async';
import 'dart:developer';

import 'package:flutter/services.dart';
import 'package:streams_channel/streams_channel.dart';

enum HotwordDetectorState {
    Uninit, InitProcess, Initialized, Failed
}

enum QaClientState {
    Uninit, Initialized, Error
}

class MainController
{
    StreamSubscription<dynamic> hotwordSubscription;
    HotwordDetectorState hotwordDetectorState = HotwordDetectorState.Uninit;
    void initHotwordDetection() async {
        if (hotwordDetectorState != HotwordDetectorState.Uninit) {
            if (initHotwordDetectionCallback != null) initHotwordDetectionCallback(hotwordDetectorState.toString());
            return;
        }

        const String METHOD_CHANNEL = 'com.ican.thinclient/method';
        var method = 'initHotwordDetection';
        hotwordDetectorState = HotwordDetectorState.InitProcess;
        String result = hotwordDetectorState.toString();

        try {
            await MethodChannel(METHOD_CHANNEL).invokeMethod(method);
            hotwordDetectorState = HotwordDetectorState.Initialized;
            result = hotwordDetectorState.toString();
        } on PlatformException catch (e) {
            hotwordDetectorState = HotwordDetectorState.Failed;
            result = "Failed to get '$method' from channel '$METHOD_CHANNEL' with message '${e.message}'.";
        }

        if (initHotwordDetectionCallback != null) initHotwordDetectionCallback(result);


        // Hotword subscription
        const String STREAM_CHANNEL = 'com.ican.thinclient/stream';

        if (hotwordSubscription != null) {
            hotwordSubscription.cancel();
            hotwordSubscription = null;
        }
        else {
            StreamsChannel streamsChannel = StreamsChannel(STREAM_CHANNEL);
            final streamId = "HotwordStreamSubscriberA";
            hotwordSubscription = streamsChannel
                .receiveBroadcastStream(streamId)
                .listen((data) => {
                    if (hotwordDetectionCallback != null)
                        hotwordDetectionCallback(data)
            });

            hotwordSubscription.onDone(() {
                hotwordSubscription = null;
            });
        }
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
        const String METHOD_CHANNEL = 'com.ican.thinclient/method';
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

    QaClientState qaClientState = QaClientState.Uninit;
    void getAnswerForQuestionInPassage(String passage, String question) async {
        const String METHOD_CHANNEL = 'com.ican.thinclient/method';

        if (qaClientState != QaClientState.Initialized) 
        {
            var method = 'initQaClient';

            try {
                await MethodChannel(METHOD_CHANNEL).invokeMethod(method);
                qaClientState = QaClientState.Initialized;
            } on PlatformException catch (e) {
                qaClientState = QaClientState.Error;
                if (qaClientResponseCallback != null) qaClientResponseCallback(method + " " + qaClientState.toString());
                return;
            }
        }

        var method = "getAnswerInPassage";
        String answer;
        try {
            if (qaClientResponseCallback != null) qaClientResponseCallback("calling getAnswer");
            answer = await MethodChannel(METHOD_CHANNEL)
                .invokeMethod(method, {'passage': passage, 'question': question});
            qaClientState = QaClientState.Initialized;
        } on PlatformException catch (e) {
            qaClientState = QaClientState.Error;
            if (qaClientResponseCallback != null) qaClientResponseCallback(method + " " + qaClientState.toString());
            return;
        }

        if (qaClientResponseCallback != null) qaClientResponseCallback(answer);
    }
    Function qaClientResponseCallback;
}