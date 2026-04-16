package android.util;

import java.util.Objects;

/**
 * Test-only replacement for {@code android.util.Pair}.
 * <p>
 * The Android unit-test stub jar throws {@code RuntimeException("Method not mocked")}
 * for {@code Pair.create()}, which prevents testing production code that uses it
 * (e.g. {@link yuku.alkitab.base.sync.SyncAdapter#patchNoConflict}). This file
 * provides a real implementation visible only on the unit-test classpath. The
 * shape matches the Android SDK's {@code Pair} so production code behaves identically.
 */
public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> create(A a, B b) {
        return new Pair<>(a, b);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> p = (Pair<?, ?>) o;
        return Objects.equals(p.first, first) && Objects.equals(p.second, second);
    }

    @Override
    public int hashCode() {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
    }

    @Override
    public String toString() {
        return "Pair{" + first + " " + second + "}";
    }
}
