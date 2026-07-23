package com.example.privileged;

interface IPrivilegedMapperService {
    void destroy() = 16777114;
    String getBackendLabel() = 1;
    boolean canOpenUinput() = 2;
    boolean createVirtualXboxDevice() = 3;
    boolean emitXboxButton(int controlCode, boolean pressed) = 4;
    boolean destroyVirtualDevice() = 5;
}
