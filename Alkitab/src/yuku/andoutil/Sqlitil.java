package yuku.andoutil;

import java.text.DateFormat;
import java.util.Date;

public class Sqlitil {
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
		 return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
	}
	
	public static String toLocaleDateMedium(int date) {
		 return toLocaleDateMedium(new Date((long)date * 1000));
	}
}
