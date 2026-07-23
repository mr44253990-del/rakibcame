#include <jni.h>
#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "VirtualXboxUinput"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_ui_NativeUinputBridge_getBackendVersion(JNIEnv* env, jobject /* this */) {
    std::string msg = "JNI uinput scaffold v1";
    return env->NewStringUTF(msg.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_canOpenUinput(JNIEnv* env, jobject /* this */, jstring path_) {
    const char* path = env->GetStringUTFChars(path_, nullptr);
    int fd = open(path, O_WRONLY | O_NONBLOCK);
    bool ok = fd >= 0;
    if (fd >= 0) close(fd);
    env->ReleaseStringUTFChars(path_, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_createVirtualXboxDevice(JNIEnv* /* env */, jobject /* this */) {
    ALOGI("createVirtualXboxDevice scaffold called");
    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_emitXboxButton(JNIEnv* /* env */, jobject /* this */, jint buttonCode, jboolean pressed) {
    ALOGI("emitXboxButton scaffold called: code=%d pressed=%d", buttonCode, pressed ? 1 : 0);
    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_destroyVirtualDevice(JNIEnv* /* env */, jobject /* this */) {
    ALOGI("destroyVirtualDevice scaffold called");
    return JNI_FALSE;
}
