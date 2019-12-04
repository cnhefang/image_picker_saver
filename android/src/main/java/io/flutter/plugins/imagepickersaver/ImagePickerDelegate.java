// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepickersaver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * A delegate class doing the heavy lifting for the plugin.
 * <p>
 * <p>When invoked, both the {@link #chooseImageFromGallery} and {@link #takeImageWithCamera}
 * methods go through the same steps:
 * <p>
 * <p>1. Check for an existing {@link #pendingResult}. If a previous pendingResult exists, this
 * means that the chooseImageFromGallery() or takeImageWithCamera() method was called at least
 * twice. In this case, stop executing and finish with an error.
 * <p>
 * <p>2. Check that a required runtime permission has been granted. The chooseImageFromGallery()
 * method checks if the {@link Manifest.permission#READ_EXTERNAL_STORAGE} permission has been
 * granted. Similarly, the takeImageWithCamera() method checks that {@link
 * Manifest.permission#CAMERA} has been granted.
 * <p>
 * <p>The permission check can end up in two different outcomes:
 * <p>
 * <p>A) If the permission has already been granted, continue with picking the image from gallery or
 * camera.
 * <p>
 * <p>B) If the permission hasn't already been granted, ask for the permission from the user. If the
 * user grants the permission, proceed with step #3. If the user denies the permission, stop doing
 * anything else and finish with a null result.
 * <p>
 * <p>3. Launch the gallery or camera for picking the image, depending on whether
 * chooseImageFromGallery() or takeImageWithCamera() was called.
 * <p>
 * <p>This can end up in three different outcomes:
 * <p>
 * <p>A) User picks an image. No maxWidth or maxHeight was specified when calling {@code
 * pickImage()} method in the Dart side of this plugin. Finish with full path for the picked image
 * as the result.
 * <p>
 * <p>B) User picks an image. A maxWidth and/or maxHeight was provided when calling {@code
 * pickImage()} method in the Dart side of this plugin. A scaled copy of the image is created.
 * Finish with full path for the scaled image as the result.
 * <p>
 * <p>C) User cancels picking an image. Finish with null result.
 */
public class ImagePickerDelegate
        implements PluginRegistry.ActivityResultListener,
        PluginRegistry.RequestPermissionsResultListener {
    @VisibleForTesting
    static final int REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY = 2342;
    @VisibleForTesting
    static final int REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA = 2343;
    @VisibleForTesting
    static final int REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION = 2344;
    @VisibleForTesting
    static final int REQUEST_CAMERA_IMAGE_PERMISSION = 2345;
    @VisibleForTesting
    static final int REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION_TO_SAVE = 2346;
    @VisibleForTesting
    static final int REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY = 2352;
    @VisibleForTesting
    static final int REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA = 2353;
    @VisibleForTesting
    static final int REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION = 2354;
    @VisibleForTesting
    static final int REQUEST_CAMERA_VIDEO_PERMISSION = 2355;

    @VisibleForTesting
    final String fileProviderName;

    private final Activity activity;
    private final File externalFilesDirectory;
    private final ImageResizer imageResizer;
    private final PermissionManager permissionManager;
    private final IntentResolver intentResolver;
    private final FileUriResolver fileUriResolver;
    private final FileUtils fileUtils;
    private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);

        void askForPermission(String permissionName, int requestCode);
    }

    interface IntentResolver {
        boolean resolveActivity(Intent intent);
    }

    interface FileUriResolver {
        Uri resolveFileProviderUriForFile(String fileProviderName, File imageFile);

        void getFullImagePath(Uri imageUri, OnPathReadyListener listener);
    }

    interface OnPathReadyListener {
        void onPathReady(String path);
    }

    private Uri pendingCameraMediaUri;
    private MethodChannel.Result pendingResult;
    private MethodCall methodCall;

    public ImagePickerDelegate(
            final Activity activity, File externalFilesDirectory, ImageResizer imageResizer) {
        this(
                activity,
                externalFilesDirectory,
                imageResizer,
                null,
                null,
                new PermissionManager() {
                    @Override
                    public boolean isPermissionGranted(String permissionName) {
                        return ActivityCompat.checkSelfPermission(activity, permissionName)
                                == PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public void askForPermission(String permissionName, int requestCode) {
                        ActivityCompat.requestPermissions(activity, new String[]{permissionName}, requestCode);
                    }
                },
                new IntentResolver() {
                    @Override
                    public boolean resolveActivity(Intent intent) {
                        return intent.resolveActivity(activity.getPackageManager()) != null;
                    }
                },
                new FileUriResolver() {
                    @Override
                    public Uri resolveFileProviderUriForFile(String fileProviderName, File file) {
                        return FileProvider.getUriForFile(activity, fileProviderName, file);
                    }

                    @Override
                    public void getFullImagePath(final Uri imageUri, final OnPathReadyListener listener) {
                        MediaScannerConnection.scanFile(
                                activity,
                                new String[]{imageUri.getPath()},
                                null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    @Override
                                    public void onScanCompleted(String path, Uri uri) {
                                        listener.onPathReady(path);
                                    }
                                });
                    }
                },
                new FileUtils());

    }

    /**
     * This constructor is used exclusively for testing; it can be used to provide mocks to final
     * fields of this class. Otherwise those fields would have to be mutable and visible.
     */
    @VisibleForTesting
    ImagePickerDelegate(
            Activity activity,
            File externalFilesDirectory,
            ImageResizer imageResizer,
            MethodChannel.Result result,
            MethodCall methodCall,
            PermissionManager permissionManager,
            IntentResolver intentResolver,
            FileUriResolver fileUriResolver,
            FileUtils fileUtils) {
        this.activity = activity;
        this.externalFilesDirectory = externalFilesDirectory;
        this.imageResizer = imageResizer;
        this.fileProviderName = activity.getPackageName() + ".flutter.image_provider";
        this.pendingResult = result;
        this.methodCall = methodCall;
        this.permissionManager = permissionManager;
        this.intentResolver = intentResolver;
        this.fileUriResolver = fileUriResolver;
        this.fileUtils = fileUtils;
    }

    public void chooseVideoFromGallery(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }

        if (!permissionManager.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionManager.askForPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION);
            return;
        }

        launchPickVideoFromGalleryIntent();
    }

    private void launchPickVideoFromGalleryIntent() {
        Intent pickVideoIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickVideoIntent.setType("video/*");

        activity.startActivityForResult(pickVideoIntent, REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY);
    }

    public void takeVideoWithCamera(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }

        if (!permissionManager.isPermissionGranted(Manifest.permission.CAMERA)) {
            permissionManager.askForPermission(
                    Manifest.permission.CAMERA, REQUEST_CAMERA_VIDEO_PERMISSION);
            return;
        }

        launchTakeVideoWithCameraIntent();
    }

    private void launchTakeVideoWithCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        boolean canTakePhotos = intentResolver.resolveActivity(intent);

        if (!canTakePhotos) {
            finishWithError("no_available_camera", "No cameras available for taking pictures.",pendingResult);
            return;
        }

        File videoFile = createTemporaryWritableVideoFile();
        pendingCameraMediaUri = Uri.parse("file:" + videoFile.getAbsolutePath());

        Uri videoUri = fileUriResolver.resolveFileProviderUriForFile(fileProviderName, videoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
        grantUriPermissions(intent, videoUri);

        activity.startActivityForResult(intent, REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA);
    }

    public void chooseImageFromGallery(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }

        if (!permissionManager.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionManager.askForPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION);
            return;
        }

        launchPickImageFromGalleryIntent();
    }

    public void saveImageToGallery(MethodCall methodCall, MethodChannel.Result result) throws IOException {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }

        if (!permissionManager.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionManager.askForPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION_TO_SAVE);
            return;
        }

        this.saveImageToGalleryResult();
    }

    private void saveImageToGalleryResult() throws IOException {

        byte[] fileData = methodCall.argument("fileData");

        String title = methodCall.argument("title") == null ? "Camera" : methodCall.argument("title").toString();

        String desctiption = methodCall.argument("description") == null ? "123" : methodCall.argument("description").toString();

        String filePath = CapturePhotoUtils.insertImage(activity.getContentResolver(), fileData, title, desctiption);

        finishWithSuccess(filePath,pendingResult);
    }

    private void launchPickImageFromGalleryIntent() {

        Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickImageIntent.setType("image/*");

        activity.startActivityForResult(pickImageIntent, REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY);
    }

    public void takeImageWithCamera(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }

        if (!permissionManager.isPermissionGranted(Manifest.permission.CAMERA)) {
            permissionManager.askForPermission(
                    Manifest.permission.CAMERA, REQUEST_CAMERA_IMAGE_PERMISSION);
            return;
        }

        launchTakeImageWithCameraIntent();
    }

    private void launchTakeImageWithCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        boolean canTakePhotos = intentResolver.resolveActivity(intent);

        if (!canTakePhotos) {
            finishWithError("no_available_camera", "No cameras available for taking pictures.",pendingResult);
            return;
        }

        File imageFile = createTemporaryWritableImageFile();
        pendingCameraMediaUri = Uri.parse("file:" + imageFile.getAbsolutePath());

        Uri imageUri = fileUriResolver.resolveFileProviderUriForFile(fileProviderName, imageFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        grantUriPermissions(intent, imageUri);

        activity.startActivityForResult(intent, REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA);
    }

    private File createTemporaryWritableImageFile() {
        return createTemporaryWritableFile(".jpg");
    }

    private File createTemporaryWritableVideoFile() {
        return createTemporaryWritableFile(".mp4");
    }

    private File createTemporaryWritableFile(String suffix) {
        String filename = UUID.randomUUID().toString();
        File image;

        try {
            image = File.createTempFile(filename, suffix, externalFilesDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return image;
    }

    private void grantUriPermissions(Intent intent, Uri imageUri) {
        PackageManager packageManager = activity.getPackageManager();
        List<ResolveInfo> compatibleActivities =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo info : compatibleActivities) {
            activity.grantUriPermission(
                    info.activityInfo.packageName,
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted =
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        switch (requestCode) {
            case REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION:
                if (permissionGranted) {
                    launchPickImageFromGalleryIntent();
                }
                break;
            case REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION_TO_SAVE:
                if (permissionGranted) {
                    try {
                        saveImageToGalleryResult();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                break;
            case REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION:
                if (permissionGranted) {
                    launchPickVideoFromGalleryIntent();
                }
                break;
            case REQUEST_CAMERA_IMAGE_PERMISSION:
                if (permissionGranted) {
                    launchTakeImageWithCameraIntent();
                }
                break;
            case REQUEST_CAMERA_VIDEO_PERMISSION:
                if (permissionGranted) {
                    launchTakeVideoWithCameraIntent();
                }
                break;
            default:
                return false;
        }

        if (!permissionGranted) {
            finishWithSuccess(null,pendingResult);
        }

        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY:
                handleChooseImageResult(resultCode, data);
                break;
            case REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA:
                handleCaptureImageResult(resultCode);
                break;
            case REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY:
                handleChooseVideoResult(resultCode, data);
                break;
            case REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA:
                handleCaptureVideoResult(resultCode);
                break;
            default:
                return false;
        }

        return true;
    }

    private void handleChooseImageResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            String path = fileUtils.getPathFromUri(activity, data.getData());
            handleImageResult(path);
            return;
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null,pendingResult);
    }

    private void handleChooseVideoResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            String path = fileUtils.getPathFromUri(activity, data.getData());
            handleVideoResult(path);
            return;
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null,pendingResult);
    }

    private void handleCaptureImageResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            fileUriResolver.getFullImagePath(
                    pendingCameraMediaUri,
                    new OnPathReadyListener() {
                        @Override
                        public void onPathReady(String path) {
                            handleImageResult(path);
                        }
                    });
            return;
        }

        // User cancelled taking a picture.
        finishWithSuccess(null,pendingResult);
    }

    private void handleCaptureVideoResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            fileUriResolver.getFullImagePath(
                    pendingCameraMediaUri,
                    new OnPathReadyListener() {
                        @Override
                        public void onPathReady(String path) {
                            handleVideoResult(path);
                        }
                    });
            return;
        }

        // User cancelled taking a picture.
        finishWithSuccess(null,pendingResult);
    }

    private void handleImageResult(String path) {
        if (pendingResult != null) {
            Double maxWidth = methodCall.argument("maxWidth");
            Double maxHeight = methodCall.argument("maxHeight");

            String finalImagePath = imageResizer.resizeImageIfNeeded(path, maxWidth, maxHeight);
            finishWithSuccess(finalImagePath,pendingResult);
        } else {
            throw new IllegalStateException("Received image from picker that was not requested");
        }
    }

    private void handleVideoResult(String path) {
        if (pendingResult != null) {
            finishWithSuccess(path,pendingResult);
        } else {
            throw new IllegalStateException("Received video from picker that was not requested");
        }
    }

    private boolean setPendingMethodCallAndResult(
            MethodCall methodCall, MethodChannel.Result result) {
        if (pendingResult != null) {
            return false;
        }

        this.methodCall = methodCall;
        pendingResult = result;
        return true;
    }

    private void finishWithSuccess(final String imagePath, final MethodChannel.Result result) {

        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                result.success(imagePath);
            }
        });
        clearMethodCallAndResult();
    }

    private void finishWithAlreadyActiveError() {
        finishWithError("already_active", "Image picker is already active", pendingResult);
    }

    private void finishWithError(final String errorCode, final String errorMessage, final MethodChannel.Result result) {
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                result.error(errorCode, errorMessage, null);
            }
        });
        clearMethodCallAndResult();
    }

    private void clearMethodCallAndResult() {
        methodCall = null;
        pendingResult = null;
    }
}
