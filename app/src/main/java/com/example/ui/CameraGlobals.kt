package com.example.ui

import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder

object CameraGlobals {
    var imageCapture: ImageCapture? = null
    var videoCapture: VideoCapture<Recorder>? = null
    var cameraControl: CameraControl? = null
    var activeRecording: androidx.camera.video.Recording? = null
}
