/*
 * Class:     yuku.snappy.codec.SnappyImplNative
 */

#include <jni.h>
#include <string.h>
#include "yuku_snappy_codec_SnappyImplNative.h"
#include <android/log.h>

SnappyImplNative::SnappyImplNative() {
	__android_log_print(ANDROID_LOG_DEBUG, "sn-init", "Native build %s %s", __DATE__, __TIME__);

	snappy_init_env(&this->env);
}

///////////////////////////////////////////////////////////////////////////

int SnappyImplNative::compress(u8* in, int inOffset, u8 *out, int outOffset, int len) {
	size_t comp_len;
	int res = snappy_compress(&this->env, (const char*) (in + inOffset), (size_t) len, (char*) (out + outOffset), &comp_len);

	if (res != 0) { // not ok
		return -res;
	} else {
		return comp_len;
	}
}

int SnappyImplNative::decompress(u8* in, int inOffset, u8 *out, int outOffset, int len) {
	int res = snappy_uncompress((const char*) (in + inOffset), (size_t) len, (char*) (out + outOffset));

	if (res != 0) { // not ok
		return -res;
	} else {
		return 0;
	}
}

//////////////////////////////////// JNI INTERFACES /////////////////////////////////////////

extern "C" {
JNIEXPORT jlong Java_yuku_snappy_codec_SnappyImplNative_nativeSetup(
		JNIEnv *env, jobject thiz) {

	SnappyImplNative *s = new SnappyImplNative();

	return (jlong) s;
}

JNIEXPORT jint Java_yuku_snappy_codec_SnappyImplNative_nativeCompress(
		JNIEnv *env, jobject thiz, jlong obj, jbyteArray _in, jint inOffset,
		jbyteArray _out, jint outOffset, jint len) {

	jbyte *in = env->GetByteArrayElements(_in, NULL);
	jbyte *out = env->GetByteArrayElements(_out, NULL);

	SnappyImplNative *s = (SnappyImplNative*) obj;
	int res = s->compress((u8*) in, (int) inOffset, (u8*) out, (int) outOffset, (int) len);

	env->ReleaseByteArrayElements(_out, out, 0);
	env->ReleaseByteArrayElements(_in, in, JNI_ABORT);
	return res;
}

JNIEXPORT jint Java_yuku_snappy_codec_SnappyImplNative_nativeDecompress(
		JNIEnv *env, jobject thiz, jlong obj, jbyteArray _in, jint inOffset,
		jbyteArray _out, jint outOffset, jint len) {

	jbyte *in = env->GetByteArrayElements(_in, NULL);
	jbyte *out = env->GetByteArrayElements(_out, NULL);

	SnappyImplNative *s = (SnappyImplNative*) obj;
	int res = s->decompress((u8*) in, (int) inOffset, (u8*) out, (int) outOffset, (int) len);

	env->ReleaseByteArrayElements(_out, out, 0);
	env->ReleaseByteArrayElements(_in, in, JNI_ABORT);

	return res;
}
}
