package com.example.ui

object NativeUinputBridge {
    init {
        try {
            System.loadLibrary("uinput_bridge")
        } catch (_: Throwable) {
            // JNI scaffold is optional in this build until native backend is fully wired.
        }
    }

    external fun getBackendVersion(): String
    external fun canOpenUinput(path: String = "/dev/uinput"): Boolean
    external fun createVirtualXboxDevice(): Boolean
    external fun emitXboxButton(buttonCode: Int, pressed: Boolean): Boolean
    external fun destroyVirtualDevice(): Boolean
}
