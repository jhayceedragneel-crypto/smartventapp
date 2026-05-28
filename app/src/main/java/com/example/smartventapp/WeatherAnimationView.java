package com.example.smartventapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WeatherAnimationView extends View {
    private Paint paint;
    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();
    private String type = "none"; 
    private float sunGlowRadius = 0f;
    private boolean growing = true;

    public WeatherAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setWeatherType(String type) {
        if (this.type.equals(type)) return;
        this.type = type;
        particles.clear();
        int count = 0;
        if (type.equals("rain")) count = 60;
        else if (type.equals("snow")) count = 40;
        else if (type.equals("leaves")) count = 20;

        for (int i = 0; i < count; i++) {
            particles.add(new Particle(random.nextInt(2000), random.nextInt(2000)));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        switch (type) {
            case "rain": drawRain(canvas, w, h); break;
            case "sun": drawSun(canvas, w, h); break;
            case "snow": drawSnow(canvas, w, h); break;
            case "leaves": drawLeaves(canvas, w, h); break;
        }

        if (!type.equals("none")) {
            postInvalidateDelayed(20);
        }
    }

    private void drawRain(Canvas canvas, int w, int h) {
        paint.setColor(Color.parseColor("#80FFFFFF"));
        paint.setStrokeWidth(3f);
        for (Particle p : particles) {
            canvas.drawLine(p.x, p.y, p.x, p.y + 40, paint);
            p.y += 35;
            if (p.y > h) { p.y = -40; p.x = random.nextInt(w); }
        }
    }

    private void drawSun(Canvas canvas, int w, int h) {
        float centerX = w * 0.85f;
        float centerY = h * 0.12f;
        if (growing) {
            sunGlowRadius += 0.8f;
            if (sunGlowRadius > 50) growing = false;
        } else {
            sunGlowRadius -= 0.8f;
            if (sunGlowRadius < 0) growing = true;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#33FFEB3B"));
        canvas.drawCircle(centerX, centerY, 120 + sunGlowRadius, paint);
        paint.setColor(Color.parseColor("#FFEE00"));
        canvas.drawCircle(centerX, centerY, 70, paint);
    }

    private void drawSnow(Canvas canvas, int w, int h) {
        paint.setColor(Color.WHITE);
        paint.setAlpha(200);
        paint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            canvas.drawCircle(p.x, p.y, 8, paint);
            p.y += 6;
            p.x += Math.sin(p.y / 60.0) * 3;
            if (p.y > h) { p.y = -20; p.x = random.nextInt(w); }
        }
    }

    private void drawLeaves(Canvas canvas, int w, int h) {
        for (Particle p : particles) {
            paint.setColor(Color.parseColor("#CCFF7043"));
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.y % 360);
            canvas.drawRect(0, 0, 25, 12, paint);
            canvas.restore();
            p.y += 7;
            p.x += Math.cos(p.y / 100.0) * 8;
            if (p.y > h) { p.y = -30; p.x = random.nextInt(w); }
        }
    }

    private static class Particle {
        float x, y;
        Particle(float x, float y) { this.x = x; this.y = y; }
    }
}