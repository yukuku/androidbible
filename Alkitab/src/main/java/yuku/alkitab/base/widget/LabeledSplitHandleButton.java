package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.events.AppEvents;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.debug.R;


public class LabeledSplitHandleButton extends SplitHandleButton {
    String label1 = null;
    String label2 = null;
    Paint labelPaint = new Paint();
    float textSize = 14f;
    float label1length;
    float label2length;
    float rotatelength;
    boolean label1pressed = false;
    boolean label2pressed = false;
    boolean rotatepressed = false;
    boolean label1down = false;
    boolean label2down = false;
    boolean rotatedown = false;
    float density;
    int bottomInset = 0;

    ButtonPressListener buttonPressListener;
    int primaryColor;
    int accentColor;
    Paint accentColorPaint = new Paint();

    Bitmap splitVerticalBitmap;
    Bitmap splitHorizontalBitmap;

    public enum Button {
        start,
        end,
        rotate,
    }

    public interface ButtonPressListener {
        void onLabelPressed(Button button);
    }

    public LabeledSplitHandleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void init() {
        density = getResources().getDisplayMetrics().density;
        labelPaint.setColor(0xffffffff);
        labelPaint.setShadowLayer(2.f * density, 0, 0, 0xff000000);
        labelPaint.setTextSize(textSize * density);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setAntiAlias(true);

        initializePrimaryColor();
        accentColor = ResourcesCompat.getColor(getResources(), R.color.accent, getContext().getTheme());
        accentColorPaint.setColor(accentColor);
        accentColorPaint.setAntiAlias(true);

        rotatelength = getResources().getDimensionPixelSize(R.dimen.split_handle_thickness);

        AppEvents.observeOnView(this, AppEvents.nightModeChanged, () -> {
            initializePrimaryColor();
            invalidate();
        });
    }

    public void setButtonPressListener(final ButtonPressListener buttonPressListener) {
        this.buttonPressListener = buttonPressListener;
    }

    public void setLabel1(String label1) {
        this.label1 = label1;
        invalidate();
    }

    public void setLabel2(String label2) {
        this.label2 = label2;
        invalidate();
    }

    /**
     * When the handle is a vertical bar (side-by-side split), the bar itself
     * extends edge-to-edge behind the navigation bar; this lifts the bottom
     * label (and its touch target) back into the safe area.
     */
    public void setBottomInset(int bottomInset) {
        if (this.bottomInset == bottomInset) return;
        this.bottomInset = bottomInset;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        // check if touch is at label1, label2, rotate button, or neither
        final int maxLabel1sz = (int) Math.min(140 * density, label1length);
        final int maxLabel2sz = (int) Math.min(140 * density, label2length);

        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // pos is the x (when orientation is vertical) or y (otherwise)
            final float pos;

            // length is the width or the height
            final int length;

            // shift of the end label away from the bar's far end
            final int endInset;

            if (orientation == Orientation.vertical) {
                pos = event.getX();
                length = getWidth();
                endInset = 0;
            } else {
                pos = event.getY();
                length = getHeight();
                endInset = bottomInset;
            }

            if (action == MotionEvent.ACTION_DOWN) {
                label1down = pos < maxLabel1sz;
                label2down = pos > length - maxLabel2sz - endInset;
                rotatedown = pos >= (length - rotatelength) * 0.5f && pos <= (length + rotatelength) * 0.5f;
            }

            if (action == MotionEvent.ACTION_UP && buttonPressListener != null) {
                label1pressed = label1down && pos < maxLabel1sz;
                label2pressed = label2down && pos > length - maxLabel2sz - endInset;
                rotatepressed = rotatedown && pos >= (length - rotatelength) * 0.5f && pos <= (length + rotatelength) * 0.5f;

                if (rotatepressed) {
                    orientation = orientation == Orientation.horizontal ? Orientation.vertical : Orientation.horizontal;
                    post(() -> buttonPressListener.onLabelPressed(Button.rotate));
                } else if (label1pressed) {
                    post(() -> buttonPressListener.onLabelPressed(Button.start));
                } else if (label2pressed) {
                    post(() -> buttonPressListener.onLabelPressed(Button.end));
                }

                if (rotatepressed || label1pressed || label2pressed) {
                    post(() -> {
                        rotatedown = label1down = label2down = false;
                        postInvalidate();
                    });
                }
            }

            if ((action == MotionEvent.ACTION_UP && !label1pressed && !label2pressed && !rotatepressed) || action == MotionEvent.ACTION_CANCEL) {
                label1down = label1pressed = false;
                label2down = label2pressed = false;
                rotatedown = rotatepressed = false;
            }
        }

        final boolean res = super.onTouchEvent(event);
        postInvalidate();
        return res;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // DO NOT CALL super.onDraw(canvas);

        // always draw unpressed bg color first
        canvas.drawColor(primaryColor);

        // meaning changes according to orientation
        final int length;
        final int thickness;
        if (orientation == Orientation.vertical) {
            length = getWidth();
            thickness = getHeight();
        } else {
            length = getHeight();
            thickness = getWidth();
        }

        if (label1down || label2down || rotatedown) {

            if (rotatedown) {
                final float cl = length * 0.5f;
                final float ct = thickness * 0.5f;
                final float r = rotatelength * 1.5f;

                if (orientation == Orientation.vertical) canvas.drawCircle(cl, ct, r, accentColorPaint);
                else canvas.drawCircle(ct, cl, r, accentColorPaint);
            } else {
                canvas.save();

                if (label1down) {
                    if (orientation == Orientation.vertical) canvas.clipRect(0, 0, label1length, thickness);
                    else canvas.clipRect(0, 0, thickness, label1length);
                } else if (label2down) {
                    if (orientation == Orientation.vertical) canvas.clipRect(length - label2length, 0, length, thickness);
                    else canvas.clipRect(0, length - label2length - bottomInset, thickness, length);
                }

                canvas.drawColor(accentColor);
                canvas.restore();
            }
        } else {
            if (isPressed()) { // not label1 nor label2
                canvas.drawColor(accentColor);
            }
        }

        final float pad = 8.f * density;
        final float base = thickness * 0.5f + textSize * density * 0.3f;

        if (label1 != null) {
            if (orientation == Orientation.horizontal) {
                canvas.save();
                canvas.rotate(-90);

                labelPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(label1, -pad, base, labelPaint);

                canvas.restore();
            } else {
                labelPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(label1, pad, base, labelPaint);
            }

            label1length = 16 * density + labelPaint.measureText(label1);
        }

        if (label2 != null) {
            if (orientation == Orientation.horizontal) {
                canvas.save();
                canvas.rotate(-90);

                labelPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(label2, -length + pad + bottomInset, base, labelPaint);

                canvas.restore();
            } else {
                labelPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(label2, length - pad, base, labelPaint);
            }

            label2length = 16.f * density + labelPaint.measureText(label2);
        }

        {
            final Bitmap splitBitmap = orientation == Orientation.vertical ? getSplitHorizontalBitmap() : getSplitVerticalBitmap();
            final float cl = length * 0.5f;
            final float ct = thickness * 0.5f;

            if (orientation == Orientation.vertical) canvas.drawBitmap(splitBitmap, cl - splitBitmap.getWidth() * 0.5f, ct - splitBitmap.getHeight() * 0.5f, null);
            else canvas.drawBitmap(splitBitmap, ct - splitBitmap.getWidth() * 0.5f, cl - splitBitmap.getHeight() * 0.5f, null);
        }
    }

    private Bitmap getSplitVerticalBitmap() {
        if (splitVerticalBitmap == null) {
            splitVerticalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_split_vertical);
        }
        return splitVerticalBitmap;
    }

    private Bitmap getSplitHorizontalBitmap() {
        if (splitHorizontalBitmap == null) {
            splitHorizontalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_split_horizontal);
        }
        return splitHorizontalBitmap;
    }

    void initializePrimaryColor() {
        if (Preferences.getBoolean(Prefkey.is_night_mode, false)) {
            primaryColor = ResourcesCompat.getColor(getResources(), R.color.primary_night_mode, getContext().getTheme());
        } else {
            primaryColor = ResourcesCompat.getColor(getResources(), R.color.primary, getContext().getTheme());
        }
    }
}
