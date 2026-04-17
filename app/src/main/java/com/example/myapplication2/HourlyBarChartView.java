package com.example.myapplication2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom View that draws a 24-bar histogram of activity by hour of day.
 * No charting library required.
 */
public class HourlyBarChartView extends View {

    private int[] hourlyData = new int[24]; // units logged per hour
    private int maxValue = 1;

    private final Paint barPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HourlyBarChartView(Context context) {
        super(context);
        init();
    }

    public HourlyBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setColor(Color.parseColor("#90CAF9"));   // light blue
        peakPaint.setColor(Color.parseColor("#1565C0"));  // dark blue for peak
        labelPaint.setColor(Color.parseColor("#555555"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        axisPaint.setColor(Color.parseColor("#CCCCCC"));
        axisPaint.setStrokeWidth(2f);
    }

    /** Sets data and triggers a redraw. */
    public void setData(int[] hourlyData) {
        this.hourlyData = hourlyData;
        maxValue = 1;
        for (int v : hourlyData) {
            if (v > maxValue) maxValue = v;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        float labelHeight = 36f;
        float topPad = 20f;
        float chartH = h - labelHeight - topPad;
        float barW = (float) w / 24;
        float gap = barW * 0.15f;

        // Axis line
        canvas.drawLine(0, h - labelHeight, w, h - labelHeight, axisPaint);

        // Find peak hour for coloring
        int peakHour = 0;
        for (int i = 1; i < 24; i++) {
            if (hourlyData[i] > hourlyData[peakHour]) peakHour = i;
        }

        for (int i = 0; i < 24; i++) {
            float barH = maxValue > 0 ? (hourlyData[i] / (float) maxValue) * chartH : 0;
            float left  = i * barW + gap;
            float right = (i + 1) * barW - gap;
            float top   = topPad + chartH - barH;
            float bottom = topPad + chartH;

            canvas.drawRoundRect(new RectF(left, top, right, bottom),
                    6f, 6f,
                    i == peakHour ? peakPaint : barPaint);

            // Hour labels every 3 hours
            if (i % 3 == 0) {
                labelPaint.setTextSize(22f);
                canvas.drawText(i + "h",
                        i * barW + barW / 2,
                        h - 6f,
                        labelPaint);
            }
        }
    }
}
