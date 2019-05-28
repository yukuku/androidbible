package yuku.alkitab.base.model;

import android.support.annotation.Keep;

// must be gson-compatible
@Keep
public class PerVersionSettings {
	public float fontSizeMultiplier;

	public static PerVersionSettings createDefault() {
		final PerVersionSettings res = new PerVersionSettings();
		res.fontSizeMultiplier = 1.f;
		return res;
	}
}
