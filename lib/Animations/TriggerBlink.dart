import 'package:flutter/cupertino.dart';
import 'package:simple_animations/simple_animations/controlled_animation.dart';
import 'package:simple_animations/simple_animations/multi_track_tween.dart';

class TriggerBlink extends StatefulWidget {
    final Widget child;
    final Playback trigger;

    TriggerBlink(this.child, this.trigger);

    @override
    State<StatefulWidget> createState() => _TriggerBlinkState(this.child, this.trigger);
}

class _TriggerBlinkState extends State<StatefulWidget> {
    Widget child;
    Playback trigger;

    _TriggerBlinkState(this.child, this.trigger);

    @override
    Widget build(BuildContext context) {
      final tween = MultiTrackTween([
          Track("opacity").add(Duration(milliseconds: 500), Tween(begin: 1.0, end: 0.0)).add(Duration(milliseconds: 500), Tween(begin: 0.0, end: 1.0)),
      ]);

      return ControlledAnimation(
          playback: trigger,
          duration: tween.duration,
          tween: tween,
          child: child,
          builderWithChild: (context, child, animation) => Opacity(
              opacity: animation["opacity"],
              child: child,
          ),
      );
    }
}