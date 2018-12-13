// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ImagePickerSaverPlugin.h"

#import <MobileCoreServices/MobileCoreServices.h>
#import <Photos/Photos.h>
#import <UIKit/UIKit.h>


@interface FLTImagePickerSaverPlugin ()<UINavigationControllerDelegate, UIImagePickerControllerDelegate>
@end

static const int SOURCE_CAMERA = 0;
static const int SOURCE_GALLERY = 1;

@implementation FLTImagePickerSaverPlugin {
    FlutterResult _result;
    NSDictionary *_arguments;
    UIImagePickerController *_imagePickerController;
    UIViewController *_viewController;
}

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
    FlutterMethodChannel *channel =
    [FlutterMethodChannel methodChannelWithName:@"mastercarl.com/image_saver"
                                binaryMessenger:[registrar messenger]];
    UIViewController *viewController =
    [UIApplication sharedApplication].delegate.window.rootViewController;
    FLTImagePickerSaverPlugin *instance =
    [[FLTImagePickerSaverPlugin alloc] initWithViewController:viewController];
    [registrar addMethodCallDelegate:instance channel:channel];
}

- (instancetype)initWithViewController:(UIViewController *)viewController {
    self = [super init];
    if (self) {
        _viewController = viewController;
        _imagePickerController = [[UIImagePickerController alloc] init];
    }
    return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
    if (_result) {
        _result([FlutterError errorWithCode:@"multiple_request"
                                    message:@"Cancelled by a second request"
                                    details:nil]);
        _result = nil;
    }
    
    if ([@"saveFile" isEqualToString:call.method]) {
        _result = result;
        _arguments = call.arguments;
        
        FlutterStandardTypedData* fileData = [_arguments objectForKey:@"fileData"] ;
        
        NSString * fileName=@"";
        //NSLog(@"fileData.data.length  :%ul",fileData.data.length);
        UIImage *image=[UIImage imageWithData:fileData.data];
        
        PHAuthorizationStatus status = [PHPhotoLibrary authorizationStatus];
        if (status == PHAuthorizationStatusRestricted) {
            NSLog(@"not allow to access photo library");
        } else if (status == PHAuthorizationStatusDenied) { // if user chosen"Not Allow"
            NSLog(@"提Remind users to go to [Settings - Privacy - Photo - xxx] to open the access switch");
        } else if (status == PHAuthorizationStatusAuthorized) { // if user chosen"Allow"
            [self saveImage:image];
        } else if (status == PHAuthorizationStatusNotDetermined) { // if user not chosen before
            // Requests authorization with dialog
            [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
                if (status == PHAuthorizationStatusAuthorized) { //  if user  chosen "Allow"
                    //Save Image to Directory
                    [self saveImage:image];
                }
            }];
        }
        //_result(fileName);
    }
    else {
        result(FlutterMethodNotImplemented);
    }
}

-(void)saveImage:(UIImage *)image  {
    __block NSString* fileName;
    __block NSString* localId;
    __block PHAssetChangeRequest *assetChangeRequest = nil;
    [[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
        PHAssetChangeRequest *assetChangeRequest = [PHAssetChangeRequest creationRequestForAssetFromImage:image];
        
        
        // [assetCollectionChangeRequest addAssets:@[[assetChangeRequest placeholderForCreatedAsset]]];
        
        localId = [[assetChangeRequest placeholderForCreatedAsset] localIdentifier];
    } completionHandler:^(BOOL success, NSError *error) {
        
        if (success) {
            NSLog(@"save image successful ");
            PHFetchResult* assetResult = [PHAsset fetchAssetsWithLocalIdentifiers:@[localId] options:nil];
            PHAsset *asset = [assetResult firstObject];
            [[PHImageManager defaultManager] requestImageDataForAsset:asset options:nil resultHandler:^(NSData *imageData, NSString *dataUTI, UIImageOrientation orientation, NSDictionary *info) {
                NSLog(@"Success %@ %@",dataUTI,info);
                
                NSLog(@"Success PHImageFileURLKey %@  ", (NSString *)[info objectForKey:@"PHImageFileURLKey"]);
                fileName=((NSURL *)[info objectForKey:@"PHImageFileURLKey"]).absoluteString;
                _result(fileName);
            }];
            
        } else {
            NSLog(@"save image failed!%@",error);
            
            fileName= @"";
            _result(fileName);
            
        }
    }];
}







// The way we save images to the tmp dir currently throws away all EXIF data
// (including the orientation of the image). That means, pics taken in portrait
// will not be orientated correctly as is. To avoid that, we rotate the actual
// image data.
// TODO(goderbauer): investigate how to preserve EXIF data.
- (UIImage *)normalizedImage:(UIImage *)image {
    if (image.imageOrientation == UIImageOrientationUp) return image;
    
    UIGraphicsBeginImageContextWithOptions(image.size, NO, image.scale);
    [image drawInRect:(CGRect){0, 0, image.size}];
    UIImage *normalizedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return normalizedImage;
}

- (UIImage *)scaledImage:(UIImage *)image
                maxWidth:(NSNumber *)maxWidth
               maxHeight:(NSNumber *)maxHeight {
    double originalWidth = image.size.width;
    double originalHeight = image.size.height;
    
    bool hasMaxWidth = maxWidth != (id)[NSNull null];
    bool hasMaxHeight = maxHeight != (id)[NSNull null];
    
    double width = hasMaxWidth ? MIN([maxWidth doubleValue], originalWidth) : originalWidth;
    double height = hasMaxHeight ? MIN([maxHeight doubleValue], originalHeight) : originalHeight;
    
    bool shouldDownscaleWidth = hasMaxWidth && [maxWidth doubleValue] < originalWidth;
    bool shouldDownscaleHeight = hasMaxHeight && [maxHeight doubleValue] < originalHeight;
    bool shouldDownscale = shouldDownscaleWidth || shouldDownscaleHeight;
    
    if (shouldDownscale) {
        double downscaledWidth = (height / originalHeight) * originalWidth;
        double downscaledHeight = (width / originalWidth) * originalHeight;
        
        if (width < height) {
            if (!hasMaxWidth) {
                width = downscaledWidth;
            } else {
                height = downscaledHeight;
            }
        } else if (height < width) {
            if (!hasMaxHeight) {
                height = downscaledHeight;
            } else {
                width = downscaledWidth;
            }
        } else {
            if (originalWidth < originalHeight) {
                width = downscaledWidth;
            } else if (originalHeight < originalWidth) {
                height = downscaledHeight;
            }
        }
    }
    
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), NO, 1.0);
    [image drawInRect:CGRectMake(0, 0, width, height)];
    
    UIImage *scaledImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return scaledImage;
}



@end
