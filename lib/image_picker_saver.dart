// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Specifies the source where the picked image should come from.
enum ImageSource {
  /// Opens up the device camera, letting the user to take a new picture.
  camera,

  /// Opens the user's photo gallery.
  gallery,
}

class ImagePickerSaver {
  static const MethodChannel _channel =
      MethodChannel('mastercarl.com/image_saver');

  static Future<String> saveFile({@required Uint8List fileData}) async {
    assert(fileData != null);

    String filePath = await _channel.invokeMethod(
      'saveFile',
      <String, dynamic>{
        'fileData': fileData,
      },
    );
    debugPrint("saved filePath:" + filePath);
    //process ios return filePath
    if(filePath.startsWith("file://")){
      filePath=filePath.replaceAll("file://", "");
    }
    return  filePath;
  }
}
