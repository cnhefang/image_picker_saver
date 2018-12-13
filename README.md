# Image Picker and Saver plugin for Flutter
[![pub package](https://img.shields.io/pub/v/image_picker_saver.svg)](https://pub.dartlang.org/packages/image_picker_saver)

  Android supported

  IOS supported 8.0+

Saves photos to the gallery.

## Installation
click the pub version icon to read hwo to install this plugin.


### Save image Example
``` dart
    import 'package:image_picker_saver/image_picker_saver.dart';


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