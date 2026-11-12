package org.ztbridge.android;

final class NativeIO {
    static { System.loadLibrary("bridgeio"); }
    static native int read(int fd, byte[] target);
    static native int configure(int fd);
    static native int write(int fd, byte[] source, int offset, int length);
}
