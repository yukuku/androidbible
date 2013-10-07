package yuku.alkitab.base.widget;

import android.text.style.ClickableSpan;
import android.view.View;

public abstract class VerseInlineLinkSpan extends ClickableSpan {
	public interface Factory {
		VerseInlineLinkSpan create(final Type type, final int arif);
	}

	private final Type type;
	private final int arif;
	private final Object source;

	public enum Type {
		footnote,
		xref,
	}

	public VerseInlineLinkSpan(final Type type, final int arif, final Object source) {
		this.type = type;
		this.arif = arif;
		this.source = source;
	}

	@Override
	public final void onClick(final View widget) {
		onClick(type, arif, source);
	}

	public abstract void onClick(final Type type, final int arif, final Object source);
}
