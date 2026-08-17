package io.github.albert.extractorir;

import android.content.Context;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/** A single-line TextView that always shrinks enough to show its complete text. */
public final class AutoFitTextView extends TextView {
    private final float maximumTextSizeSp;

    public AutoFitTextView(Context context, AttributeSet attributes) {
        super(context, attributes);
        maximumTextSizeSp = getTextSize()
                / getResources().getDisplayMetrics().scaledDensity;
        setSingleLine(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft()
                - getPaddingRight();
        fitTextToWidth(availableWidth);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void fitTextToWidth(int availableWidth) {
        CharSequence text = getText();
        if (availableWidth <= 0 || text == null || text.length() == 0) {
            return;
        }

        float maximumSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                maximumTextSizeSp,
                getResources().getDisplayMetrics()
        );
        TextPaint measurementPaint = new TextPaint(getPaint());
        measurementPaint.setTextSize(maximumSizePx);
        float requiredWidth = Layout.getDesiredWidth(text, measurementPaint);
        float fittedSizePx = requiredWidth > availableWidth
                ? maximumSizePx * availableWidth / requiredWidth
                : maximumSizePx;

        // A small safety margin avoids clipping from device-specific font rounding.
        fittedSizePx *= 0.98f;
        if (Math.abs(getTextSize() - fittedSizePx) >= 0.5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedSizePx);
        }
    }
}
