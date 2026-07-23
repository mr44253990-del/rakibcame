package com.example.privileged

import android.content.Context
import android.os.Process
import com.example.ui.NativeUinputBridge

class PrivilegedMapperUserService : IPrivilegedMapperService.Stub {

    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    override fun destroy() {
        runCatching { NativeUinputBridge.destroyVirtualDevice() }
        System.exit(0)
    }

    override fun getBackendLabel(): String {
        val uid = Process.myUid()
        return when (uid) {
            0 -> "Privileged backend: ROOT (uid=0)"
            2000 -> "Privileged backend: ADB shell (uid=2000)"
            else -> "Privileged backend uid=$uid"
        }
    }

    override fun canOpenUinput(): Boolean {
        return NativeUinputBridge.canOpenUinput("/dev/uinput")
    }

    override fun createVirtualXboxDevice(): Boolean {
        return NativeUinputBridge.createVirtualXboxDevice()
    }

    override fun emitXboxButton(controlCode: Int, pressed: Boolean): Boolean {
        return NativeUinputBridge.emitXboxButton(controlCode, pressed)
    }

    override fun destroyVirtualDevice(): Boolean {
        return NativeUinputBridge.destroyVirtualDevice()
    }
}
