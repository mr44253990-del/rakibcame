package com.example.ui

object NativeUinputBridge {
    const val CONTROL_A = 1
    const val CONTROL_B = 2
    const val CONTROL_X = 3
    const val CONTROL_Y = 4
    const val CONTROL_LB = 5
    const val CONTROL_RB = 6
    const val CONTROL_LT = 7
    const val CONTROL_RT = 8
    const val CONTROL_LS = 9
    const val CONTROL_RS = 10
    const val CONTROL_BACK = 11
    const val CONTROL_START = 12
    const val CONTROL_DPAD_UP = 13
    const val CONTROL_DPAD_DOWN = 14
    const val CONTROL_DPAD_LEFT = 15
    const val CONTROL_DPAD_RIGHT = 16

    init {
        try {
            System.loadLibrary("uinput_bridge")
        } catch (_: Throwable) {
            // JNI backend may be unavailable on unsupported builds.
        }
    }

    external fun getBackendVersion(): String
    external fun canOpenUinput(path: String = "/dev/uinput"): Boolean
    external fun createVirtualXboxDevice(): Boolean
    external fun emitXboxButton(buttonCode: Int, pressed: Boolean): Boolean
    external fun destroyVirtualDevice(): Boolean
}
