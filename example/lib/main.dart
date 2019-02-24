// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker_saver/image_picker_saver.dart';
import 'package:video_player/video_player.dart';
import 'dart:ui' as ui;

void main() {
  runApp(new MyApp());
}

GlobalKey globalKey = new GlobalKey();

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Image Picker Demo',
      home: new MyHomePage(title: 'Image Picker Example'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _MyHomePageState createState() => new _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  Future<File> _imageFile;
  bool isVideo = false;
  VideoPlayerController _controller;
  VoidCallback listener;

  void _onImageSaveButtonPressed() async {
    print("_onImageSaveButtonPressed");
    var response = await http
        .get('http://upload.art.ifeng.com/2017/0425/1493105660290.jpg');

    debugPrint(response.statusCode.toString());

    var filePath = await ImagePickerSaver.saveFile(
        fileData: response.bodyBytes, title: 'ImagePickerPicture',
        description: 'example of image picker saver');

    var savedFile= File.fromUri(Uri.file(filePath));
    setState(() {
      _imageFile = Future<File>.sync(() => savedFile);
    });
  }
  void _takeScreenShot() async {
    RenderRepaintBoundary boundary =
    globalKey.currentContext.findRenderObject();
    ui.Image image = await boundary.toImage();

    ByteData byteData = await image.toByteData(format: ui.ImageByteFormat.png);
    Uint8List pngBytes = byteData.buffer.asUint8List();
    print(pngBytes);
    var filePath =await ImagePickerSaver.saveFile(fileData: pngBytes);
    var savedFile= File.fromUri(Uri.file(filePath));
    setState(() {
      _imageFile = Future<File>.sync(() => savedFile);
    });
  }
  void _onImageButtonPressed(ImageSource source) {
    setState(() {
      if (_controller != null) {
        _controller.setVolume(0.0);
        _controller.removeListener(listener);
      }
      if (isVideo) {
        ImagePickerSaver.pickVideo(source: source).then((File file) {
          if (file != null && mounted) {
            setState(() {
              _controller = VideoPlayerController.file(file)
                ..addListener(listener)
                ..setVolume(1.0)
                ..initialize()
                ..setLooping(true)
                ..play();
            });
          }
        });
      } else {
        _imageFile = ImagePickerSaver.pickImage(source: source);
      }
    });
  }

  @override
  void deactivate() {
    if (_controller != null) {
      _controller.setVolume(0.0);
      _controller.removeListener(listener);
    }
    super.deactivate();
  }

  @override
  void dispose() {
    if (_controller != null) {
      _controller.dispose();
    }
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    listener = () {
      setState(() {});
    };
  }

  Widget _previewVideo(VideoPlayerController controller) {
    if (controller == null) {
      return const Text(
        'You have not yet picked a video',
        textAlign: TextAlign.center,
      );
    } else if (controller.value.initialized) {
      return Padding(
        padding: const EdgeInsets.all(10.0),
        child: AspectRatioVideo(controller),
      );
    } else {
      return const Text(
        'Error Loading Video',
        textAlign: TextAlign.center,
      );
    }
  }

  Widget _previewImage() {
    return FutureBuilder<File>(
        future: _imageFile,
        builder: (BuildContext context, AsyncSnapshot<File> snapshot) {
          if (snapshot.connectionState == ConnectionState.done &&
              snapshot.data != null) {
            debugPrint(snapshot.data.path);
            return Image.file(snapshot.data);
          } else if (snapshot.error != null) {
            return const Text(
              'Error picking image.',
              textAlign: TextAlign.center,
            );
          } else {
            return const Text(
              'You have not yet picked an image.',
              textAlign: TextAlign.center,
            );
          }
        });
  }

  @override
  Widget build(BuildContext context) {
    return new RepaintBoundary(
        key: globalKey,
        child: Scaffold(
          appBar: AppBar(
            title: Text(widget.title),
          ),
          body: Center(
            child: isVideo ? _previewVideo(_controller) : _previewImage(),
          ),
          floatingActionButton: Column(
            mainAxisAlignment: MainAxisAlignment.end,
            children: <Widget>[
              FloatingActionButton(
                onPressed: () {
                  isVideo = false;
                  _onImageButtonPressed(ImageSource.gallery);
                },
                heroTag: 'image0',
                tooltip: 'Pick Image from gallery',
                child: const Icon(Icons.photo_library),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16.0),
                child: FloatingActionButton(
                  onPressed: () {
                    isVideo = false;
                    _onImageButtonPressed(ImageSource.camera);
                  },
                  heroTag: 'image1',
                  tooltip: 'Take a Photo',
                  child: const Icon(Icons.camera_alt),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16.0),
                child: FloatingActionButton(
                  backgroundColor: Colors.red,
                  onPressed: () {
                    isVideo = true;
                    _onImageButtonPressed(ImageSource.gallery);
                  },
                  heroTag: 'video0',
                  tooltip: 'Pick Video from gallery',
                  child: const Icon(Icons.video_library),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16.0),
                child: FloatingActionButton(
                  backgroundColor: Colors.red,
                  onPressed: () {
                    isVideo = true;
                    _onImageButtonPressed(ImageSource.camera);
                  },
                  heroTag: 'video1',
                  tooltip: 'Take a Video',
                  child: const Icon(Icons.videocam),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16.0),
                child: FloatingActionButton(
                  backgroundColor: Colors.lightGreen,
                  onPressed: () {
                    _onImageSaveButtonPressed();
                  },
                  heroTag: 'save image from url',
                  tooltip: 'save image from url',
                  child: const Icon(Icons.save),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 16.0),
                child: FloatingActionButton(
                  backgroundColor: Colors.lightGreen,
                  onPressed: () {
                    _takeScreenShot();
                  },
                  heroTag: 'ScreenShot',
                  tooltip: 'ScreenShot',
                  child: const Icon(Icons.mobile_screen_share),
                ),
              ),
            ],
          ),
        ));
  }
}

class AspectRatioVideo extends StatefulWidget {
  final VideoPlayerController controller;

  AspectRatioVideo(this.controller);

  @override
  AspectRatioVideoState createState() => new AspectRatioVideoState();
}

class AspectRatioVideoState extends State<AspectRatioVideo> {
  VideoPlayerController get controller => widget.controller;
  bool initialized = false;

  VoidCallback listener;

  @override
  void initState() {
    super.initState();
    listener = () {
      if (!mounted) {
        return;
      }
      if (initialized != controller.value.initialized) {
        initialized = controller.value.initialized;
        setState(() {});
      }
    };
    controller.addListener(listener);
  }

  @override
  Widget build(BuildContext context) {
    if (initialized) {
      final Size size = controller.value.size;
      return new Center(
        child: new AspectRatio(
          aspectRatio: size.width / size.height,
          child: new VideoPlayer(controller),
        ),
      );
    } else {
      return new Container();
    }
  }
}
