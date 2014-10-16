package yuku.alkitab.base.util;

import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public class Literals {
	@SafeVarargs
	public static <T> T[] Array(final T... elements) {
		return elements;
	}

	@NonNull public static String[] ToStringArray(final Object... elements) {
		final String[] res = new String[elements.length];
		for (int i = 0, len = res.length; i < len; i++) {
			res[i] = elements[i] == null ? null : elements[i].toString();
		}
		return res;
	}

	@SafeVarargs
	public static <T> List<T> List(final T... elements) {
		return Arrays.asList(elements);
	}
}
