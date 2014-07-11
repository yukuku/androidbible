package yuku.alkitab.base.widget;

import android.text.style.ClickableSpan;
import android.view.View;

public class CallbackSpan extends ClickableSpan {
	public static interface OnClickListener {
		void onClick(View widget, Object data);
	}
	
	final Object data_;
	private OnClickListener onClickListener_;
	
	public CallbackSpan() {
		data_ = null;
	}

	public CallbackSpan(Object data, OnClickListener onClickListener) {
		data_ = data;
		onClickListener_ = onClickListener;
	}
	
	public void setOnClickListener(OnClickListener onClickListener) {
		onClickListener_ = onClickListener;
	}
	
	@Override
	public void onClick(View widget) {
		if (onClickListener_ != null) {
			onClickListener_.onClick(widget, data_);
		}
	}
}
