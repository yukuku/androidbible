package yuku.alkitab.base.widget;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import yuku.alkitab.base.util.AppLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ScrollbarSetter {
	static final String TAG = ScrollbarSetter.class.getSimpleName();

	static Field scrollCacheField;
	static Class<?> scrollCacheClass;
	static Field scrollBarField;
	static Class<?> scrollBarClass;
	static Method setVerticalThumbDrawable;
	static boolean reflectionOk;

	static {
		try {
			scrollCacheField = View.class.getDeclaredField("mScrollCache");
			scrollCacheField.setAccessible(true);
			scrollCacheClass = scrollCacheField.getType();
			scrollBarField = scrollCacheClass.getDeclaredField("scrollBar");
			scrollBarField.setAccessible(true);
			scrollBarClass = scrollBarField.getType();
			setVerticalThumbDrawable = scrollBarClass.getDeclaredMethod("setVerticalThumbDrawable", Drawable.class);
			setVerticalThumbDrawable.setAccessible(true);
			reflectionOk = true;
		} catch (Exception e) {
			AppLog.e(TAG, "reflection error", e);
		}
	}

	public static boolean setVerticalThumb(final View view, final Drawable drawable) {
		if (!reflectionOk) return false;

		try {
			final Object scrollCache = scrollCacheField.get(view);
			final Object scrollBar = scrollBarField.get(scrollCache);
			setVerticalThumbDrawable.invoke(scrollBar, drawable);

			return true;
		} catch (Exception e) {
			AppLog.e(TAG, "reflection error", e);
			return false;
		}
	}
}
