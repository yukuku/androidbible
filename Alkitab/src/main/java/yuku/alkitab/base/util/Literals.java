package yuku.alkitab.base.util;

import java.util.Arrays;
import java.util.List;

public class Literals {
	@SafeVarargs
	public static <T> T[] Array(final T... elements) {
		return elements;
	}

	@SafeVarargs
	public static <T> List<T> List(final T... elements) {
		return Arrays.asList(elements);
	}
}
