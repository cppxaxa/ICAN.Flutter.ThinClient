import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:simple_animations/simple_animations.dart';
import 'package:thinclient006/Animations/TriggerBlink.dart';
import 'package:thinclient006/main_controller.dart';

import 'Animations/FadeIn.dart';

class MainHomePage extends StatefulWidget {
    MainHomePage({Key key}) : super(key: key);

    @override
    _MainHomePageState createState() => _MainHomePageState();
}

class _MainHomePageState extends State<MainHomePage> {
    MainController controller = MainController();
    String _batteryLevel = 'Unknown battery level.';
    String _hotwordService = 'Hotword service';
    String _passageAnswer = "";

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
    
    Future<void> getAnswerInPassageCmd() async {
        String passage ="Rameswaram is a town and municipality in the Ramanathapuram district of the Indian state of Tamil Nadu. It is on Pamban Island separated from mainland India by the Pamban channel and is about 40 kilometres from Mannar Island, Sri Lanka. It is in the Gulf of Mannar, at the tip of the Indian peninsula.[1] Pamban Island, also known as Rameswaram Island, is connected to mainland India by the Pamban Bridge. Rameswaram is the terminus of the railway line from Chennai and Madurai. Together with Varanasi, it is considered to be one of the holiest places in India to Hindus, and part of the Char Dham pilgrimage. Rameshwaram is 970 meters from sea coast.";
        String question = "Distance to sea beach";
        setAnswerOfPassage("Getting answer ...");
        controller.qaClientResponseCallback = setAnswerOfPassage;
        controller.getAnswerForQuestionInPassage(passage, question);
    }
    void setAnswerOfPassage(String answer) {
        setState(() {
            _passageAnswer = answer;
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
                            child: Text('Blink'),
                            onPressed: BlinkOnce,
                        ),
                        TriggerBlink(Text("Hello"), trigger),
                        FadeIn(1,
                            RaisedButton(
                                child: Text('Init Hotword Detection'),
                                onPressed: initHotwordDetectionCmd,
                            )
                        ),
                        FadeIn(2,
                            Text(_hotwordService)
                        ),
                        FadeIn(3,
                            RaisedButton(
                                child: Text('Get Battery Level'),
                                onPressed: requestBatteryLevelCmd,
                            )
                        ),
                        FadeIn(4,
                            Text(_batteryLevel)
                        ),
                        FadeIn(5,
                            RaisedButton(
                                child: Text('Get Answer'),
                                onPressed: getAnswerInPassageCmd,
                            )
                        ),
                        FadeIn(6,
                            Text("Answer: " + _passageAnswer)
                        ),
                    ],
                ),
            ),
        );
    }

    Playback trigger = Playback.START_OVER_FORWARD;
    void BlinkOnce() {
        setState(() {
            trigger = Playback.START_OVER_FORWARD;
        });
    }
}