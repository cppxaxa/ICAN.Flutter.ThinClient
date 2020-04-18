import 'package:flutter/material.dart';
import 'main_view.dart';

void main() {
  runApp(MainApp());
}

class MainApp extends StatelessWidget {
    // This widget is the root of your application.
    @override
    Widget build(BuildContext context) {
        return MaterialApp(
            title: 'ICAN ThinClient',
            theme: ThemeData(
                primarySwatch: Colors.blue,
            ),
            home: Scaffold(
                appBar: AppBar(
                    title: Text('ICAN ThinClient'),
                ),
                body: Center(
                            child: MainHomePage(),
                        )
                    )
            );
    }
}

