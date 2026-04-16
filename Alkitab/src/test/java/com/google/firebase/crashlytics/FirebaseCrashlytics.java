package com.google.firebase.crashlytics;

/**
 * Test-only replacement for {@code com.google.firebase.crashlytics.FirebaseCrashlytics}.
 * <p>
 * {@link yuku.alkitab.base.util.AppLog} captures a reference to
 * {@code FirebaseCrashlytics.getInstance()} in a static field initializer. Under unit
 * tests the real SDK throws because {@code FirebaseApp} has never been initialised, which
 * means any test that touches {@code AppLog} fails with {@code ExceptionInInitializerError}
 * — including tests of unrelated production code that merely logs on a catch-path
 * (e.g. {@link yuku.alkitab.base.util.Highlights#decode(String)}).
 * <p>
 * This shadow lets the static initializer complete and makes the recorded calls no-ops.
 * It is only visible on the unit-test classpath, matching the pattern of
 * {@code src/test/java/android/util/Pair.java}.
 */
public final class FirebaseCrashlytics {
    private static final FirebaseCrashlytics INSTANCE = new FirebaseCrashlytics();

    public static FirebaseCrashlytics getInstance() {
        return INSTANCE;
    }

    private FirebaseCrashlytics() {}

    public void log(String message) {}

    public void recordException(Throwable throwable) {}

    public void setCustomKey(String key, String value) {}

    public void setCustomKey(String key, boolean value) {}

    public void setCustomKey(String key, double value) {}

    public void setCustomKey(String key, float value) {}

    public void setCustomKey(String key, int value) {}

    public void setCustomKey(String key, long value) {}

    public void setUserId(String identifier) {}

    public void setCrashlyticsCollectionEnabled(boolean enabled) {}

    public void setCrashlyticsCollectionEnabled(Boolean enabled) {}

    public boolean didCrashOnPreviousExecution() {
        return false;
    }

    public void deleteUnsentReports() {}

    public void sendUnsentReports() {}
}
