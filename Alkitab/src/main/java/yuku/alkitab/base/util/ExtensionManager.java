package yuku.alkitab.base.util;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.BuildConfig;

import java.util.ArrayList;
import java.util.List;

public class ExtensionManager {
	static final String TAG = ExtensionManager.class.getSimpleName();

	public static final String ACTION_SHOW_VERSE_INFO = "yuku.alkitab.extensions.action.SHOW_VERSE_INFO";

	private static final Intent openExtension = new Intent(ACTION_SHOW_VERSE_INFO);

	public static class Info {
		public final ActivityInfo activityInfo;
		public final CharSequence label;
		public final boolean supportsMultipleVerses;
		public final boolean includeVerseText;
		public final boolean includeVerseTextFormatting;

		public Info(final ActivityInfo activityInfo, final CharSequence label, final boolean supportsMultipleVerses, final boolean includeVerseText, final boolean includeVerseTextFormatting) {
			this.activityInfo = activityInfo;
			this.label = label;
			this.supportsMultipleVerses = supportsMultipleVerses;
			this.includeVerseText = includeVerseText;
			this.includeVerseTextFormatting = includeVerseTextFormatting;
		}
	}

	static List<Info> extensions;

	private static boolean getBooleanFromMetadata(@Nullable final Bundle metadata, @NonNull final String key, final boolean def) {
		if (metadata == null) return def;
		return metadata.getBoolean(key, def);
	}

	public static synchronized List<Info> getExtensions() {
		if (extensions == null) {
			final PackageManager pm = App.context.getPackageManager();
			final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(openExtension, 0);

			extensions = new ArrayList<>();

			for (final ResolveInfo ri : resolveInfos) {
				try {
					final ActivityInfo ai = pm.getActivityInfo(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), PackageManager.GET_META_DATA);
					final CharSequence label = ai.loadLabel(pm);
					final boolean supportsMultipleVerses = getBooleanFromMetadata(ai.metaData, "supportsMultipleVerses", false);
					final boolean includeVerseText = getBooleanFromMetadata(ai.metaData, "includeVerseText", false);
					final boolean includeVerseTextFormatting = getBooleanFromMetadata(ai.metaData, "includeVerseTextFormatting", false);

					final Info info = new Info(ai, label, supportsMultipleVerses, includeVerseText, includeVerseTextFormatting);
					extensions.add(info);
				} catch (PackageManager.NameNotFoundException e) {
					AppLog.e(TAG, "PackageManager should not emit this", e);
				}
			}

			if (BuildConfig.DEBUG) {
				AppLog.d(TAG, "Found " + extensions.size() + " extensions:");
				for (final Info info : extensions) {
					AppLog.d(TAG, "- " + info.activityInfo.packageName + "/" + info.activityInfo.name);
				}
			}
		}

		return new ArrayList<>(extensions);
	}

	public static synchronized void invalidate() {
		extensions = null;
	}

	public static class InvalidateExtensionsReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			AppLog.d(TAG, "invalidating extensions because of " + intent.getAction());
			invalidate();
		}
	}
}
