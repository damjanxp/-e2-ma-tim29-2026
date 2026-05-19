package com.example.slagalica.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;

/**
 * Utility klasa koja detektuje shake gest putem akcelerometra uređaja.
 *
 * <p>Upotreba u Activity:</p>
 * <pre>
 *   private ShakeDetector shakeDetector;
 *
 *   {@literal @}Override protected void onResume() {
 *       super.onResume();
 *       shakeDetector.start();
 *   }
 *
 *   {@literal @}Override protected void onPause() {
 *       super.onPause();
 *       shakeDetector.stop();
 *   }
 * </pre>
 *
 * <p>Nikad ne pozivati {@code start()}/{@code stop()} u {@code onCreate()}/{@code onDestroy()} —
 * senzori troše bateriju i moraju se pauzirati kada Activity nije vidljiv.</p>
 */
public class ShakeDetector implements SensorEventListener {

    // -------------------------------------------------------------------------
    // Konstante
    // -------------------------------------------------------------------------

    /** Prag ubrzanja iznad gravitacije koji se smatra shake gestom (m/s²). */
    private static final float SHAKE_THRESHOLD = 12.0f;

    /** Minimalni vremenski razmak između dva uzastopna shake-a (ms). */
    private static final long SHAKE_COOLDOWN = 1000L;

    // -------------------------------------------------------------------------
    // Callback interfejs
    // -------------------------------------------------------------------------

    /** Callback koji se poziva kada se detektuje shake gest. */
    public interface OnShakeListener {
        /**
         * Pozvano na UI thread-u kada akcelerometar detektuje shake
         * koji prelazi {@link #SHAKE_THRESHOLD} i prošao je {@link #SHAKE_COOLDOWN}.
         */
        void onShake();
    }

    // -------------------------------------------------------------------------
    // Polja
    // -------------------------------------------------------------------------

    private final SensorManager  sensorManager;
    private Sensor               accelerometer;
    private final OnShakeListener listener;

    /** Timestamp poslednjeg detektovanog shake-a (ms), 0 ako još nije bilo. */
    private long lastShakeTime = 0L;

    // -------------------------------------------------------------------------
    // Konstruktor
    // -------------------------------------------------------------------------

    /**
     * Kreira novi {@link ShakeDetector}.
     *
     * @param ctx      Context (Activity ili Application) — koristi se samo za SensorManager
     * @param listener callback koji se poziva pri svakom shake gestu
     */
    public ShakeDetector(@NonNull Context ctx, @NonNull OnShakeListener listener) {
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.listener      = listener;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Registruje listener na akcelerometar. Pozivati u {@code onResume()}.
     *
     * <p>Ako uređaj nema akcelerometar, metoda ne radi ništa —
     * shake detektovanje neće biti dostupno.</p>
     */
    public void start() {
        if (sensorManager == null) return;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * Odjavljuje listener sa akcelerometra. Pozivati u {@code onPause()}.
     *
     * <p>Uvek pozivati ovu metodu da se spreči curenje memorije i bespotrebna
     * potrošnja baterije kada Activity nije u fokusu.</p>
     */
    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    /**
     * Poziva se pri svakoj promeni vrednosti akcelerometra.
     *
     * <p>Izračunava ukupno ubrzanje i poredi ga sa pragom.
     * Ako gForce prelazi {@link #SHAKE_THRESHOLD} i prošlo je više od
     * {@link #SHAKE_COOLDOWN} od poslednjeg shake-a, poziva {@link OnShakeListener#onShake()}.</p>
     *
     * @param event SensorEvent sa vrednostima na osama x, y, z (m/s²)
     */
    @Override
    public void onSensorChanged(@NonNull SensorEvent event) {
        float ax = event.values[0];
        float ay = event.values[1];
        float az = event.values[2];

        // Ukupna magnitude ubrzanja
        double totalAcceleration = Math.sqrt((double) ax * ax + (double) ay * ay + (double) az * az);

        // Oduzimamo gravitaciju da dobijemo neto ubrzanje od korisnikovog pokreta
        double gForce = totalAcceleration - SensorManager.GRAVITY_EARTH;

        if (gForce > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN) {
                lastShakeTime = now;
                listener.onShake();
            }
        }
    }

    /**
     * Poziva se pri promeni tačnosti senzora. Nije relevantno za shake detekciju.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nije potrebno pratiti promenu tačnosti za shake detekciju
    }
}

