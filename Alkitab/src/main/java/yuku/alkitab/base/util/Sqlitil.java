package yuku.alkitab.base.util;

import yuku.alkitab.base.App;

import java.text.DateFormat;
import java.util.Date;

public class Sqlitil {
	static ThreadLocal<DateFormat> mediumDateFormat = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return android.text.format.DateFormat.getMediumDateFormat(App.context);
		}
	};

	static ThreadLocal<DateFormat> timeFormat = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return android.text.format.DateFormat.getTimeFormat(App.context);
		}
	};

	public static int nowDateTime() {
		return (int) (new Date().getTime() / 1000);
	}
	
	public static int toInt(Date date) {
		return (int) (date.getTime() / 1000);
	}
	
	public static Date toDate(int date) {
		return new Date((long)date * 1000);
	}
	
	public static String toLocaleDateMedium(Date date) {
		return mediumDateFormat.get().format(date);
	}
	
	public static String toLocaleDateMedium(int date) {
		 return toLocaleDateMedium(new Date((long)date * 1000));
	}

	public static String toLocalDateTimeSimple(Date date) {
		return mediumDateFormat.get().format(date) + ", " + timeFormat.get().format(date);
	}

}
