LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CFLAGS := -O3 -fno-strict-aliasing
endif

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_CFLAGS := -O3 -fno-strict-aliasing
endif

ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_CFLAGS := -O3 -fno-strict-aliasing
endif

LOCAL_MODULE    := snappy
LOCAL_SRC_FILES := \
	map.c \
	scmd.c \
	util.c \
	snappy.c \
	yuku_snappy_codec_SnappyImplNative.cpp

# for logging
LOCAL_LDLIBS    += -llog

include $(BUILD_SHARED_LIBRARY)
