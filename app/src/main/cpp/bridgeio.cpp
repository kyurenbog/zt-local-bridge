#include <jni.h>
#include <vector>
#include "ZeroTierSockets.h"

static int ioResult(int result) {
    if (result >= 0) return result;
    const int error = zts_errno;
    return error == ZTS_EAGAIN || error == ZTS_EINTR ? -2 : -1;
}
extern "C" JNIEXPORT jint JNICALL
Java_org_ztbridge_android_NativeIO_configure(JNIEnv*, jclass, jint fd) {
    if (zts_set_recv_timeout(fd, 1, 0) != 0) return -1;
    return zts_set_send_timeout(fd, 1, 0);
}

// Do not pin Java heap arrays across blocking network calls.
extern "C" JNIEXPORT jint JNICALL
Java_org_ztbridge_android_NativeIO_read(JNIEnv* env, jclass, jint fd, jbyteArray target) {
    std::vector<jbyte> buffer(env->GetArrayLength(target));
    int n = zts_bsd_read(fd, buffer.data(), buffer.size());
    if (n > 0) env->SetByteArrayRegion(target, 0, n, buffer.data());
    return ioResult(n);
}
extern "C" JNIEXPORT jint JNICALL
Java_org_ztbridge_android_NativeIO_write(JNIEnv* env, jclass, jint fd, jbyteArray source, jint offset, jint length) {
    int size = env->GetArrayLength(source);
    if (offset < 0 || length < 0 || offset > size || length > size - offset) return -1;
    std::vector<jbyte> buffer(length);
    env->GetByteArrayRegion(source, offset, length, buffer.data());
    if (env->ExceptionCheck()) return -1;
    return ioResult(zts_bsd_write(fd, buffer.data(), buffer.size()));
}
