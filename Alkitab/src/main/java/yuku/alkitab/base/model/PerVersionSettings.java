package yuku.alkitab.base.model;

// must be gson-compatible
public class PerVersionSettings {
	public float fontSizeMultiplier;

	public static PerVersionSettings createDefault() {
		final PerVersionSettings res = new PerVersionSettings();
		res.fontSizeMultiplier = 1.f;
		return res;
	}
}
