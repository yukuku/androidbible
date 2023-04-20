package yuku.alkitab.base.util;

import androidx.annotation.NonNull;

public class Literals {
    @SafeVarargs
    public static <T> T[] Array(final T... elements) {
        return elements;
    }

    @NonNull
    public static String[] ToStringArray(final Object... elements) {
        final String[] res = new String[elements.length];
        for (int i = 0, len = res.length; i < len; i++) {
            res[i] = elements[i] == null ? null : elements[i].toString();
        }
        return res;
    }
}
