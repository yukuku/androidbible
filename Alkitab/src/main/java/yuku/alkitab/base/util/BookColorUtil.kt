package yuku.alkitab.base.util

object BookColorUtil {

    @JvmStatic
    fun getForegroundOnDark(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_ef5350.toInt() // Pink
            in 39..65 -> // NT
                0xff_42a5f5.toInt() // Blue 400
            else -> // others
                0xff_eeeeee.toInt() // Grey 200
        }
    }


    @JvmStatic
    fun getBackground(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_e53935.toInt() // Red 600
            in 39..65 -> // NT
                0xff_1e88e5.toInt() // Blue 600
            else -> // others
                0xff_212121.toInt() // Grey 900
        }
    }

}