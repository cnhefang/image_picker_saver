# Image Picker and Saver plugin for Flutter
[![pub package](https://img.shields.io/pub/v/image_picker_saver.svg)](https://pub.dartlang.org/packages/image_picker_saver)

  Android supported

  IOS supported 8.0+

forked from official plugin image_picker and add saver function to save image to photo gallery.

## Installation
click the pub version icon to read hwo to install this plugin.


### Save image Example
``` dart

    void _onImageSaveButtonPressed() async {
      print("_onImageSaveButtonPressed");
      var response = await http
          .get('http://upload.art.ifeng.com/2017/0425/1493105660290.jpg');
  
      debugPrint(response.statusCode.toString());
  
      var filePath = await ImagePickerSaver.saveFile(
          fileData: response.bodyBytes);
  
      var savedFile= File.fromUri(Uri.file(filePath));
      setState(() {
        _imageFile = Future<File>.sync(() => savedFile);
      });
    }

```

#---- The following is the official plugin description ---

# Image Picker plugin for Flutter

[![pub package](https://img.shields.io/pub/v/image_picker.svg)](https://pub.dartlang.org/packages/image_picker)

A Flutter plugin for iOS and Android for picking images from the image library,
and taking new pictures with the camera.

*Note*: This plugin is still under development, and some APIs might not be available yet. [Feedback welcome](https://github.com/flutter/flutter/issues) and [Pull Requests](https://github.com/flutter/plugins/pulls) are most welcome!

## Installation

First, add `image_picker` as a [dependency in your pubspec.yaml file](https://flutter.io/platform-plugins/).

### iOS

Add the following keys to your _Info.plist_ file, located in `<project root>/ios/Runner/Info.plist`:

* `NSPhotoLibraryUsageDescription` - describe why your app needs permission for the photo library. This is called _Privacy - Photo Library Usage Description_ in the visual editor.
* `NSCameraUsageDescription` - describe why your app needs access to the camera. This is called _Privacy - Camera Usage Description_ in the visual editor.
* `NSMicrophoneUsageDescription` - describe why your app needs access to the microphone, if you intend to record videos. This is called _Privacy - Microphone Usage Description_ in the visual editor.

### Android

No configuration required - the plugin should work out of the box.

### Example

``` dart
import 'package:image_picker/image_picker.dart';

class MyHomePage extends StatefulWidget {
  @override
  _MyHomePageState createState() => new _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  File _image;

  Future getImage() async {
    var image = await ImagePicker.pickImage(source: ImageSource.camera);

    setState(() {
      _image = image;
    });
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
      appBar: new AppBar(
        title: new Text('Image Picker Example'),
      ),
      body: new Center(
        child: _image == null
            ? new Text('No image selected.')
            : new Image.file(_image),
      ),
      floatingActionButton: new FloatingActionButton(
        onPressed: getImage,
        tooltip: 'Pick Image',
        child: new Icon(Icons.add_a_photo),
      ),
    );
  }
}
```
