package yuku.alkitab.base.widget;

import android.text.style.ClickableSpan;
import android.view.View;

public class CallbackSpan<T> extends ClickableSpan {
	public static interface OnClickListener<T> {
		void onClick(View widget, T data);
	}
	
	final T data_;
	private OnClickListener<T> onClickListener_;
	
	public CallbackSpan() {
		data_ = null;
	}

	public CallbackSpan(T data, OnClickListener<T> onClickListener) {
		data_ = data;
		onClickListener_ = onClickListener;
	}
	
	public void setOnClickListener(OnClickListener<T> onClickListener) {
		onClickListener_ = onClickListener;
	}
	
	@Override
	public void onClick(View widget) {
		if (onClickListener_ != null) {
			onClickListener_.onClick(widget, data_);
		}
	}
}
