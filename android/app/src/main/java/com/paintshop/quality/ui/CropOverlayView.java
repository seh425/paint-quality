package com.paintshop.quality.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A simple crop selector: drag inside to move, drag the bottom-right handle to resize.
 */
public class CropOverlayView extends View {

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_RESIZE = 2;

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF cropRect = new RectF();
    private float lastX;
    private float lastY;
    private int mode = MODE_NONE;
    private float handleRadius;

    public CropOverlayView(Context context) {
        this(context, null);
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dimPaint.setColor(0x99000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        gridPaint.setColor(0x66FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handleRadius = dp(10);
    }

    public void setImageRect(RectF rect) {
        imageRect.set(rect);
        if (cropRect.isEmpty()) {
            float insetX = rect.width() * 0.06f;
            float insetY = rect.height() * 0.18f;
            cropRect.set(rect.left + insetX, rect.top + insetY,
                    rect.right - insetX, rect.bottom - insetY);
        } else {
            clampCrop();
        }
        invalidate();
    }

    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    public RectF getImageRect() {
        return new RectF(imageRect);
    }

    public boolean hasSelection() {
        return !cropRect.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageRect.isEmpty() || cropRect.isEmpty()) {
            return;
        }
        canvas.drawRect(0, 0, getWidth(), cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, dimPaint);

        canvas.drawRect(cropRect, borderPaint);
        canvas.drawLine(cropRect.left + cropRect.width() / 3f, cropRect.top,
                cropRect.left + cropRect.width() / 3f, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + cropRect.width() * 2f / 3f, cropRect.top,
                cropRect.left + cropRect.width() * 2f / 3f, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + cropRect.height() / 3f,
                cropRect.right, cropRect.top + cropRect.height() / 3f, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + cropRect.height() * 2f / 3f,
                cropRect.right, cropRect.top + cropRect.height() * 2f / 3f, gridPaint);

        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius * 0.6f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (imageRect.isEmpty()) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (distance(x, y, cropRect.right, cropRect.bottom) <= handleRadius * 2.5f) {
                    mode = MODE_RESIZE;
                } else if (cropRect.contains(x, y)) {
                    mode = MODE_MOVE;
                } else {
                    mode = MODE_NONE;
                }
                lastX = x;
                lastY = y;
                return mode != MODE_NONE;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_NONE) {
                    return false;
                }
                float dx = x - lastX;
                float dy = y - lastY;
                if (mode == MODE_MOVE) {
                    cropRect.offset(dx, dy);
                    clampCrop();
                } else {
                    cropRect.right = Math.max(cropRect.left + dp(80), Math.min(imageRect.right, x));
                    cropRect.bottom = Math.max(cropRect.top + dp(60), Math.min(imageRect.bottom, y));
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                return true;
            default:
                return false;
        }
    }

    private void clampCrop() {
        if (cropRect.width() > imageRect.width()) {
            cropRect.right = cropRect.left + imageRect.width();
        }
        if (cropRect.height() > imageRect.height()) {
            cropRect.bottom = cropRect.top + imageRect.height();
        }
        if (cropRect.left < imageRect.left) {
            cropRect.offset(imageRect.left - cropRect.left, 0);
        }
        if (cropRect.top < imageRect.top) {
            cropRect.offset(0, imageRect.top - cropRect.top);
        }
        if (cropRect.right > imageRect.right) {
            cropRect.offset(imageRect.right - cropRect.right, 0);
        }
        if (cropRect.bottom > imageRect.bottom) {
            cropRect.offset(0, imageRect.bottom - cropRect.bottom);
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
