#include <jni.h>
#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include "com_swoval_files_implNativeDirectoryLister.h"

typedef struct Handle {
    DIR *dp = nullptr;
    int err = 0;
} Handle;

extern "C" {

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    errno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_swoval_files_implNativeDirectoryLister_errno(JNIEnv *env,
                                                                         jobject quicklister,
                                                                         jlong handle) {
    Handle *h = (Handle *)handle;
    switch (h->err) {
    case EACCES:
        return com_swoval_files_implNativeDirectoryLister_EACCES;
    case ENOENT:
        return com_swoval_files_implNativeDirectoryLister_ENOENT;
    case ENOTDIR:
        return com_swoval_files_implNativeDirectoryLister_ENOTDIR;
    default:
        return h->err;
    }
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    strerror
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_swoval_files_implNativeDirectoryLister_strerror(JNIEnv *env,
                                                                               jobject quicklister,
                                                                               jint err) {
    return env->NewStringUTF(strerror(err));
}
/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    openDir
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_swoval_files_implNativeDirectoryLister_openDir(JNIEnv *env,
                                                                            jobject lister,
                                                                            jstring dir) {
    Handle *handle = (Handle *)malloc(sizeof(Handle));
    handle->dp     = nullptr;
    handle->err    = 0;
    handle->dp     = opendir(env->GetStringUTFChars(dir, 0));
    if (!handle->dp) {
        handle->err = errno;
    }
    return (long)handle;
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    closeDir
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_swoval_files_implNativeDirectoryLister_closeDir(JNIEnv *env,
                                                                            jobject lister,
                                                                            jlong handlep) {
    Handle *handle = (Handle *)handlep;
    (void)closedir(handle->dp);
    free(handle);
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    nextFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_swoval_files_implNativeDirectoryLister_nextFile(JNIEnv *env,
                                                                             jobject unused,
                                                                             jlong handlep) {
    Handle *handle = (Handle *)handlep;
    errno          = 0;
    jlong result   = (jlong)readdir(handle->dp);
    if (!result)
        handle->err = errno;
    return result;
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    getType
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_swoval_files_implNativeDirectoryLister_getType(JNIEnv *, jobject,
                                                                           jlong handle) {
    switch (((struct dirent *)handle)->d_type) {
    case DT_DIR:
        return com_swoval_files_implNativeDirectoryLister_DIRECTORY;
    case DT_REG:
        return com_swoval_files_implNativeDirectoryLister_FILE;
    case DT_LNK:
        return com_swoval_files_implNativeDirectoryLister_LINK;
    default:
        return com_swoval_files_implNativeDirectoryLister_UNKNOWN;
    }
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    getName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_swoval_files_implNativeDirectoryLister_getName(JNIEnv *env, jobject,
                                                                              jlong handle) {
    return env->NewStringUTF(((struct dirent *)handle)->d_name);
}
}
