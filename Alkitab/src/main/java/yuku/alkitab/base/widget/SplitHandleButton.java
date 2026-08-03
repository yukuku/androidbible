package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.view.ViewCompat;
import java.util.Collections;
import java.util.List;

public class SplitHandleButton extends AppCompatButton {
    public interface SplitHandleButtonListener {
        void onHandleDragStart();
        /**
         * Only called when orientation is horizontal
         */
        void onHandleDragMoveX(float dxSinceLast, float dxSinceStart);
        /**
         * Only called when orientation is vertical
         */
        void onHandleDragMoveY(float dySinceLast, float dySinceStart);
        void onHandleDragStop();
    }

    public enum Orientation {
        vertical, // top bottom
        horizontal, // left right
    }

    protected Orientation orientation = Orientation.vertical; // should not ever be null

    SplitHandleButtonListener listener;

    float down;
    float move;
    int[] loc = {0, 0};

    public SplitHandleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(SplitHandleButtonListener listener) {
        this.listener = listener;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(final Orientation orientation) {
        this.orientation = orientation;
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // A handle parked against a screen edge overlaps the system's back-gesture
        // area, where a drag that starts on the bar would be swallowed by the
        // system instead of resizing the panes. Claiming the bar's own bounds
        // keeps such a drag with us. The system caps exclusions at 200dp per
        // edge and honors the rects nearest the bottom, so on a full-height bar
        // only its lower part is guaranteed. The mandatory gesture area at the
        // window bottom cannot be claimed at all; the split manager instead
        // keeps the bar out of it.
        final List<Rect> exclusion = Collections.singletonList(new Rect(0, 0, right - left, bottom - top));
        ViewCompat.setSystemGestureExclusionRects(this, exclusion);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getLocationOnScreen(loc);

        final int action = event.getActionMasked();
		switch (action) {
			case ACTION_DOWN -> {
				if (orientation == Orientation.vertical) {
					down = move = event.getY() + loc[1];
				} else {
					down = move = event.getX() + loc[0];
				}
				if (listener != null) listener.onHandleDragStart();
				setPressed(true);
				return true;
			}
			case ACTION_MOVE -> {
				if (orientation == Orientation.vertical) {
					final float y = event.getY() + loc[1];
					if (listener != null) listener.onHandleDragMoveY(y - move, y - down);
					move = y;
				} else {
					final float x = event.getX() + loc[0];
					if (listener != null) listener.onHandleDragMoveX(x - move, x - down);
					move = x;
				}
				return true;
			}
			case ACTION_CANCEL, ACTION_UP -> {
				if (listener != null) listener.onHandleDragStop();
				setPressed(false);
				return true;
			}
		}
        return false;
    }
}
