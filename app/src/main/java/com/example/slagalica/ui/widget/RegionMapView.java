package com.example.slagalica.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Šematska (uprošćena) mapa regiona Srbije — "5. Prikaz regiona".
 *
 * <p>Nije geografski precizna mapa (nema OpenStreetMap/Google Maps zavisnosti,
 * po dogovoru radi jednostavnosti i pouzdanosti build-a); regioni su prikazani
 * kao pravougaonici raspoređeni u obliku koji grubo podseća na raspored
 * regiona u Srbiji (Vojvodina na severu, Beograd ispod nje, tri regiona u
 * sredini, Južna Srbija i Kosovo i Metohija na jugu). Svaki region je tapabilan
 * i sadrži nasumično raspoređene tačke igrača ({@link Zone#markers}).</p>
 */
public class RegionMapView extends View {

    /** Jedan region na mapi — granice su relativne (0..1) prema veličini View-a. */
    public static class Zone {
        public final String name;
        public final float left, top, right, bottom;
        public final int playerCount;
        /** 0 = bez okvira, 1 = zlatno, 2 = srebrno, 3 = bronzano (region je bio 1./2./3. prošlog meseca). */
        public final int frameType;
        /** Pozicije igrača, relativne (0..1) unutar granica ovog regiona. */
        public final List<PointF> markers;

        public Zone(String name, float left, float top, float right, float bottom,
                    int playerCount, int frameType, List<PointF> markers) {
            this.name = name;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.playerCount = playerCount;
            this.frameType = frameType;
            this.markers = markers;
        }
    }

    public interface OnZoneClickListener {
        void onZoneClick(@androidx.annotation.NonNull String regionName);
    }

    private static final int COLOR_FILL = Color.parseColor("#FFE8EAF6");
    private static final int COLOR_BORDER_DEFAULT = Color.parseColor("#FF6200EE");
    private static final int COLOR_GOLD = Color.parseColor("#FFB8860B");
    private static final int COLOR_SILVER = Color.parseColor("#FF8C97A1");
    private static final int COLOR_BRONZE = Color.parseColor("#FF8C5323");
    private static final int COLOR_MARKER = Color.parseColor("#FFE6533C");
    private static final int COLOR_TEXT = Color.parseColor("#FF1A1A2E");

    private final List<Zone> zones = new ArrayList<>();
    private final List<RectF> zonePixelRects = new ArrayList<>();
    @Nullable private OnZoneClickListener listener;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RegionMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(COLOR_FILL);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(3));

        namePaint.setColor(COLOR_TEXT);
        namePaint.setTextSize(sp(13));
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setFakeBoldText(true);

        countPaint.setColor(COLOR_TEXT);
        countPaint.setAlpha(180);
        countPaint.setTextSize(sp(11));
        countPaint.setTextAlign(Paint.Align.CENTER);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(COLOR_MARKER);
    }

    public void setOnZoneClickListener(@Nullable OnZoneClickListener listener) {
        this.listener = listener;
    }

    /** Postavlja regione koje treba iscrtati; poziva {@code invalidate()}. */
    public void setZones(@androidx.annotation.NonNull List<Zone> newZones) {
        zones.clear();
        zones.addAll(newZones);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputePixelRects();
    }

    private void recomputePixelRects() {
        zonePixelRects.clear();
        int w = getWidth();
        int h = getHeight();
        for (Zone z : zones) {
            zonePixelRects.add(new RectF(z.left * w, z.top * h, z.right * w, z.bottom * h));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (zonePixelRects.size() != zones.size()) {
            recomputePixelRects();
        }
        for (int i = 0; i < zones.size(); i++) {
            Zone zone = zones.get(i);
            RectF rect = zonePixelRects.get(i);
            float corner = dp(10);

            canvas.drawRoundRect(rect, corner, corner, fillPaint);
            borderPaint.setColor(borderColorFor(zone.frameType));
            canvas.drawRoundRect(rect, corner, corner, borderPaint);

            float centerX = rect.centerX();
            canvas.drawText(zone.name, centerX, rect.top + dp(18), namePaint);
            canvas.drawText(zone.playerCount + " igrača", centerX, rect.top + dp(34), countPaint);

            for (PointF marker : zone.markers) {
                float mx = rect.left + marker.x * rect.width();
                float my = rect.top + marker.y * rect.height();
                // Ne crtaj markere preko naslova regiona (gornjih ~40dp).
                if (my < rect.top + dp(38)) {
                    my = rect.top + dp(38) + (my - rect.top);
                    if (my > rect.bottom - dp(6)) my = rect.bottom - dp(6);
                }
                canvas.drawCircle(mx, my, dp(4), markerPaint);
            }
        }
    }

    private int borderColorFor(int frameType) {
        switch (frameType) {
            case 1: return COLOR_GOLD;
            case 2: return COLOR_SILVER;
            case 3: return COLOR_BRONZE;
            default: return COLOR_BORDER_DEFAULT;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            for (int i = 0; i < zonePixelRects.size(); i++) {
                if (zonePixelRects.get(i).contains(x, y)) {
                    if (listener != null) {
                        listener.onZoneClick(zones.get(i).name);
                    }
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
