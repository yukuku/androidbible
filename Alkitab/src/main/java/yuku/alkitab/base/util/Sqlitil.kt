package yuku.alkitab.base.util

import android.text.format.DateFormat
import java.util.Date
import yuku.alkitab.base.App

object Sqlitil {
    @JvmStatic
    fun nowDateTime(): Int {
        return (System.currentTimeMillis() / 1000).toInt()
    }

    /**
     * Convert Date to unix time
     */
    @JvmStatic
    fun toInt(date: Date): Int {
        return (date.time / 1000).toInt()
    }

    /**
     * Convert unix time to Date
     */
    @JvmStatic
    fun toDate(date: Int): Date {
        return Date(date.toLong() * 1000)
    }

    @JvmStatic
    fun toLocaleDateMedium(date: Date): String {
        return DateFormat.getMediumDateFormat(App.context).format(date)
    }

    @JvmStatic
    fun toLocaleTime(date: Date): String {
        return DateFormat.getTimeFormat(App.context).format(date)
    }
}
