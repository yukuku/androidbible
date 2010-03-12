package yuku.andoutil;

import java.text.*;
import java.util.*;

public class Sqlitil {
	private static SimpleDateFormat dateFormat_ymd_gmt;
	private static SimpleDateFormat dateFormat_ymdhms_gmt;
	
	public static int nowDateTime() {
		return (int) (new Date().getTime() / 1000);
	}
	
	public static int toInt(Date date) {
		return (int) (date.getTime() / 1000);
	}
	
	public static String toYmdGmt(Date date) {
		if (dateFormat_ymd_gmt == null) {
			dateFormat_ymd_gmt = new SimpleDateFormat("yyyy-MM-dd");
			dateFormat_ymd_gmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		}
		
		return dateFormat_ymd_gmt.format(date);
	}
	
	public static String toYmdGmt(int date) {
		return toYmdGmt(new Date((long)date * 1000));
	}
	
	public static String toYmdhmsGmt(Date date) {
		if (dateFormat_ymdhms_gmt == null) {
			dateFormat_ymdhms_gmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			dateFormat_ymdhms_gmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		}
		
		return dateFormat_ymdhms_gmt.format(date);
	}

	public static String toYmdhmsGmt(int date) {
		return toYmdhmsGmt(new Date((long)date * 1000));
	}
	
	public static String toLocaleDateMedium(Date date) {
		 return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
	}
	
	public static String toLocaleDateMedium(int date) {
		 return toLocaleDateMedium(new Date((long)date * 1000));
	}
}
