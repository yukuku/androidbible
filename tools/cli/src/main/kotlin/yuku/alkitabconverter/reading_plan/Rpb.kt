package yuku.alkitabconverter.reading_plan

/** The magic bytes every .rpb file starts with, followed by one version byte. */
internal val RPB_HEADER = byteArrayOf(0x52, 0x8a.toByte(), 0x61, 0x34, 0x00, 0xe0.toByte(), 0xea.toByte())

internal const val RPB_VERSION: Byte = 0x01
