package yuku.alkitab.base.widget;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import androidx.annotation.NonNull;

public abstract class VerseInlineLinkSpan extends ClickableSpan {
	public interface Factory {
		VerseInlineLinkSpan create(final Type type, final int arif);
	}

	private final Type type;
	private final int arif;

	public enum Type {
		footnote,
		xref,
	}

	public VerseInlineLinkSpan(final Type type, final int arif) {
		this.type = type;
		this.arif = arif;
	}

	@Override
	public final void onClick(@NonNull final View widget) {
		onClick(type, arif);
	}

	public void onClick(final Type type, final int arif) {
	}

	@Override
	public void updateDrawState(@NonNull final TextPaint ds) {
		// don't call super to prevent link underline and link coloring
		// NOP
	}
}
