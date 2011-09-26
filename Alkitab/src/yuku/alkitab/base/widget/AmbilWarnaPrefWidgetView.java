package yuku.alkitab.base.widget;

import android.content.*;
import android.graphics.*;
import android.graphics.Paint.*;
import android.util.*;
import android.view.*;

public class AmbilWarnaPrefWidgetView extends View {
	Paint paint;
	float satudp;
	float ukuranKotak = 24.f;
	
	public AmbilWarnaPrefWidgetView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint();
		paint.setColor(0xffffffff);
		paint.setStyle(Style.STROKE);
		paint.setStrokeWidth(1.f);

		satudp = context.getResources().getDisplayMetrics().density;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		canvas.drawRect(0, 0, ukuranKotak * satudp - 1, ukuranKotak * satudp - 1, paint);
	}
}
