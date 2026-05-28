package com.example.smartventapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class DialTicksView extends View {
    private Paint paint;
    private int tickCount = 60;
    private float currentValue = 68f;
    private float minValue = 50f;
    private float maxValue = 100f;
    
    private OnValueChangeListener listener;

    public interface OnValueChangeListener {
        void onValueChanged(int value);
    }

    public DialTicksView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.listener = listener;
    }

    public void setRange(float min, float max) {
        this.minValue = min;
        this.maxValue = max;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(width, height) / 2 - 40;
        
        canvas.save();
        canvas.translate(width / 2, height / 2);
        
        float startAngle = 135f;
        float sweepAngle = 270f;
        canvas.rotate(startAngle);

        float angleStep = sweepAngle / tickCount;
        float percent = (currentValue - minValue) / (maxValue - minValue);
        int activeTick = (int) (percent * tickCount);

        for (int i = 0; i <= tickCount; i++) {
            float tickPercent = (float) i / tickCount;
            
            // Summer (Yellow) -> Normal (Green) -> Cold (Blue)
            int tickColor;
            if (tickPercent > 0.7f) {
                tickColor = Color.parseColor("#FFEE00"); // Yellow
            } else if (tickPercent > 0.35f) {
                tickColor = Color.parseColor("#00FF88"); // Green
            } else {
                tickColor = Color.parseColor("#00E5FF"); // Blue
            }

            if (i <= activeTick) {
                // Active/Filled part: Bright and Thick
                paint.setColor(tickColor);
                paint.setAlpha(255);
                paint.setStrokeWidth(10f);
                canvas.drawLine(0, -radius, 0, -radius + 45, paint);
            } else {
                // Inactive part: Faded version of the same color
                paint.setColor(tickColor);
                paint.setAlpha(60);
                paint.setStrokeWidth(4f);
                canvas.drawLine(0, -radius, 0, -radius + 20, paint);
            }
            canvas.rotate(angleStep);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - (getWidth() / 2);
        float y = event.getY() - (getHeight() / 2);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                double angle = Math.toDegrees(Math.atan2(y, x)) + 90;
                if (angle < 0) angle += 360;
                
                float adjustedAngle = (float) angle;
                if (adjustedAngle < 135 && adjustedAngle > 45) return true;
                
                if (adjustedAngle <= 45) adjustedAngle += 360;
                
                float normalized = (adjustedAngle - 135) / 270f;
                normalized = Math.max(0, Math.min(1, normalized));
                
                int newValue = Math.round(minValue + (normalized * (maxValue - minValue)));
                if (newValue != (int)currentValue) {
                    currentValue = newValue;
                    if (listener != null) listener.onValueChanged(newValue);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void setValue(int value) {
        this.currentValue = value;
        invalidate();
    }
}