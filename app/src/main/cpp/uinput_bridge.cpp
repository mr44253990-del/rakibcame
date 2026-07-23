#include <jni.h>
#include <string>
#include <cstring>
#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <android/log.h>

#define LOG_TAG "VirtualXboxUinput"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static int g_uinput_fd = -1;
static int g_hat_x = 0;
static int g_hat_y = 0;

static bool emit_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return write(fd, &ev, sizeof(ev)) == sizeof(ev);
}

static bool sync_events(int fd) {
    return emit_event(fd, EV_SYN, SYN_REPORT, 0);
}

static bool setup_abs(int fd, int code, int min, int max, int fuzz = 0, int flat = 0) {
    struct uinput_abs_setup abs_setup{};
    abs_setup.code = code;
    abs_setup.absinfo.minimum = min;
    abs_setup.absinfo.maximum = max;
    abs_setup.absinfo.fuzz = fuzz;
    abs_setup.absinfo.flat = flat;
    return ioctl(fd, UI_ABS_SETUP, &abs_setup) >= 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_ui_NativeUinputBridge_getBackendVersion(JNIEnv* env, jobject /* this */) {
    std::string msg = "JNI uinput backend v3";
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
    if (g_uinput_fd >= 0) {
        return JNI_TRUE;
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        ALOGE("Failed to open /dev/uinput: %s", strerror(errno));
        return JNI_FALSE;
    }

    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);

    ioctl(fd, UI_SET_KEYBIT, BTN_SOUTH);
    ioctl(fd, UI_SET_KEYBIT, BTN_EAST);
    ioctl(fd, UI_SET_KEYBIT, BTN_WEST);
    ioctl(fd, UI_SET_KEYBIT, BTN_NORTH);
    ioctl(fd, UI_SET_KEYBIT, BTN_TL);
    ioctl(fd, UI_SET_KEYBIT, BTN_TR);
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBL);
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBR);
    ioctl(fd, UI_SET_KEYBIT, BTN_SELECT);
    ioctl(fd, UI_SET_KEYBIT, BTN_START);
    ioctl(fd, UI_SET_KEYBIT, BTN_MODE);

    if (!setup_abs(fd, ABS_X, -32768, 32767, 0, 16) ||
        !setup_abs(fd, ABS_Y, -32768, 32767, 0, 16) ||
        !setup_abs(fd, ABS_RX, -32768, 32767, 0, 16) ||
        !setup_abs(fd, ABS_RY, -32768, 32767, 0, 16) ||
        !setup_abs(fd, ABS_Z, 0, 255, 0, 0) ||
        !setup_abs(fd, ABS_RZ, 0, 255, 0, 0) ||
        !setup_abs(fd, ABS_HAT0X, -1, 1, 0, 0) ||
        !setup_abs(fd, ABS_HAT0Y, -1, 1, 0, 0)) {
        ALOGE("Failed to configure ABS axes: %s", strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    struct uinput_setup usetup{};
    std::snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "%s", "Virtual Xbox Controller");
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = 0x045e;
    usetup.id.product = 0x028e;
    usetup.id.version = 1;

    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) {
        ALOGE("UI_DEV_SETUP failed: %s", strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        ALOGE("UI_DEV_CREATE failed: %s", strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    g_uinput_fd = fd;
    g_hat_x = 0;
    g_hat_y = 0;
    sync_events(g_uinput_fd);
    ALOGI("Virtual Xbox device created");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_emitXboxButton(JNIEnv* /* env */, jobject /* this */, jint controlCode, jboolean pressed) {
    if (g_uinput_fd < 0) {
        ALOGE("emitXboxButton called before device creation");
        return JNI_FALSE;
    }

    bool ok = true;
    int value = pressed ? 1 : 0;

    switch (controlCode) {
        case 1: ok = emit_event(g_uinput_fd, EV_KEY, BTN_SOUTH, value); break; // A
        case 2: ok = emit_event(g_uinput_fd, EV_KEY, BTN_EAST, value); break;  // B
        case 3: ok = emit_event(g_uinput_fd, EV_KEY, BTN_WEST, value); break;  // X
        case 4: ok = emit_event(g_uinput_fd, EV_KEY, BTN_NORTH, value); break; // Y
        case 5: ok = emit_event(g_uinput_fd, EV_KEY, BTN_TL, value); break;    // LB
        case 6: ok = emit_event(g_uinput_fd, EV_KEY, BTN_TR, value); break;    // RB
        case 7: ok = emit_event(g_uinput_fd, EV_ABS, ABS_Z, pressed ? 255 : 0); break; // LT
        case 8: ok = emit_event(g_uinput_fd, EV_ABS, ABS_RZ, pressed ? 255 : 0); break; // RT
        case 9: ok = emit_event(g_uinput_fd, EV_KEY, BTN_THUMBL, value); break; // LS
        case 10: ok = emit_event(g_uinput_fd, EV_KEY, BTN_THUMBR, value); break; // RS
        case 11: ok = emit_event(g_uinput_fd, EV_KEY, BTN_SELECT, value); break; // BACK
        case 12: ok = emit_event(g_uinput_fd, EV_KEY, BTN_START, value); break; // START
        case 13:
            g_hat_y = pressed ? -1 : (g_hat_y == -1 ? 0 : g_hat_y);
            ok = emit_event(g_uinput_fd, EV_ABS, ABS_HAT0Y, g_hat_y);
            break;
        case 14:
            g_hat_y = pressed ? 1 : (g_hat_y == 1 ? 0 : g_hat_y);
            ok = emit_event(g_uinput_fd, EV_ABS, ABS_HAT0Y, g_hat_y);
            break;
        case 15:
            g_hat_x = pressed ? -1 : (g_hat_x == -1 ? 0 : g_hat_x);
            ok = emit_event(g_uinput_fd, EV_ABS, ABS_HAT0X, g_hat_x);
            break;
        case 16:
            g_hat_x = pressed ? 1 : (g_hat_x == 1 ? 0 : g_hat_x);
            ok = emit_event(g_uinput_fd, EV_ABS, ABS_HAT0X, g_hat_x);
            break;
        default:
            ALOGE("Unsupported control code: %d", controlCode);
            return JNI_FALSE;
    }

    if (!ok) {
        ALOGE("Failed to emit control %d: %s", controlCode, strerror(errno));
        return JNI_FALSE;
    }

    return sync_events(g_uinput_fd) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ui_NativeUinputBridge_destroyVirtualDevice(JNIEnv* /* env */, jobject /* this */) {
    if (g_uinput_fd < 0) return JNI_TRUE;
    ioctl(g_uinput_fd, UI_DEV_DESTROY);
    close(g_uinput_fd);
    g_uinput_fd = -1;
    g_hat_x = 0;
    g_hat_y = 0;
    ALOGI("Virtual Xbox device destroyed");
    return JNI_TRUE;
}
