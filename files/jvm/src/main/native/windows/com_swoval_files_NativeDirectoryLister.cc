#include <jni.h>
#include "windows.h"
#include "com_swoval_files_implNativeDirectoryLister.h"
#include <stdio.h>

typedef struct Handle {
    WIN32_FIND_DATA ffd;
    HANDLE handle;
    bool first = true;
    int err    = ERROR_SUCCESS;
} Handle;

extern "C" {
BOOL WINAPI DllMainCRTStartup(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    return TRUE;
}
/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    errno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_swoval_files_implNativeDirectoryLister_errno(JNIEnv *, jobject,
                                                                         jlong handlep) {
    Handle *handle = (Handle *)handlep;
    int err        = handle->err;
    switch (err) {
    case ERROR_NO_MORE_FILES:
        return com_swoval_files_implNativeDirectoryLister_EOF;
    case ERROR_SUCCESS:
        return com_swoval_files_implNativeDirectoryLister_ESUCCESS;
    case ERROR_FILE_NOT_FOUND:
        return com_swoval_files_implNativeDirectoryLister_ENOENT;
    case ERROR_ACCESS_DENIED:
        return com_swoval_files_implNativeDirectoryLister_EACCES;
    case ERROR_PATH_NOT_FOUND:
        return com_swoval_files_implNativeDirectoryLister_ENOENT;
    case ERROR_DIRECTORY:
        return com_swoval_files_implNativeDirectoryLister_ENOTDIR;
    default:
        return err;
    }
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    strerror
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_swoval_files_implNativeDirectoryLister_strerror(JNIEnv *env, jobject,
                                                                               jint err) {
    char buf[256];
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, NULL, err,
                  MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), buf, (sizeof(buf) / sizeof(char)),
                  NULL);
    return env->NewStringUTF(buf);
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    openDir
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_swoval_files_implNativeDirectoryLister_openDir(JNIEnv *env, jobject,
                                                                            jstring dir) {
    Handle *handle = (Handle *)HeapAlloc(GetProcessHeap(), 0, sizeof(Handle));
    handle->first  = true;
    handle->handle = FindFirstFileEx(env->GetStringUTFChars(dir, 0), FindExInfoBasic, &handle->ffd,
                                     FindExSearchNameMatch, NULL, FIND_FIRST_EX_LARGE_FETCH);
    handle->err    = GetLastError();
    return (jlong)handle;
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    closeDir
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_swoval_files_implNativeDirectoryLister_closeDir(JNIEnv *, jobject,
                                                                            jlong handle) {
    FindClose(((Handle *)handle)->handle);
    HeapFree(GetProcessHeap(), 0, (LPVOID)handle);
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    nextFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_swoval_files_implNativeDirectoryLister_nextFile(JNIEnv *, jobject,
                                                                             jlong h) {
    Handle *handle = (Handle *)h;
    if (handle->first) {
        handle->first = false;
    } else {
        if (!FindNextFile(handle->handle, &handle->ffd)) {
            handle->err = GetLastError();
            return 0;
        }
    }
    return (jlong)&handle->ffd;
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    getType
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_swoval_files_implNativeDirectoryLister_getType(JNIEnv *, jobject,
                                                                           jlong handle) {
    DWORD attrs = ((WIN32_FIND_DATA *)handle)->dwFileAttributes;
    if (attrs & FILE_ATTRIBUTE_REPARSE_POINT) {
        return com_swoval_files_implNativeDirectoryLister_UNKNOWN;
    } else if (attrs & FILE_ATTRIBUTE_DIRECTORY) {
        return com_swoval_files_implNativeDirectoryLister_DIRECTORY;
    } else {
        return com_swoval_files_implNativeDirectoryLister_FILE;
    }
}

/*
 * Class:     com_swoval_files_implNativeDirectoryLister
 * Method:    getName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_swoval_files_implNativeDirectoryLister_getName(JNIEnv *env, jobject,
                                                                              jlong handle) {
    return env->NewStringUTF(((WIN32_FIND_DATA *)handle)->cFileName);
}
}
