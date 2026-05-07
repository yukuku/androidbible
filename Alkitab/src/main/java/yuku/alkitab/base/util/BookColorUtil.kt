package yuku.alkitab.base.util

object BookColorUtil {

    @JvmStatic
    fun getForegroundOnDark(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_ff80ab.toInt() // Accent Pink 100
            in 39..65 -> // NT
                0xff_82b1ff.toInt() // Accent Blue 100
            else -> // others
                0xff_e0e0e0.toInt() // Grey 200
        }
    }

    @JvmStatic
    fun getForegroundOnLight(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_c2185b.toInt() // Pink 700
            in 39..65 -> // NT
                0xff_1976d2.toInt() // Blue 700
            else -> // others
                0xff_424242.toInt() // Grey 800
        }
    }

    @JvmStatic
    fun getBackground(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_880e4f.toInt() // Pink 900
            in 39..65 -> // NT
                0xff_0d47a1.toInt() // Blue 900
            else -> // others
                0xff_212121.toInt() // Grey 900
        }
    }
}
