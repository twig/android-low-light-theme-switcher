package twig.libs.low_light_theme_switcher;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

/**
 * Created by twig on 27/02/2016.
 */
public class DayNightSensor implements SensorEventListener {
    // Static fields
    public static final float LUX_THRESHOLD = 6.0f;

    private static DayNightSensor instance;

    // Class fields
    private Application.ActivityLifecycleCallbacks lifecycleCallback;
    private Activity currentActivity;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private int currentNightMode;
    private int samplingDelay;


    /**
     * Begin monitoring the light levels for this application.
     *
     * @param application
     * @param samplingDelay Delay in sampling in milliseconds.
     */
    public static void start(Application application, int samplingDelay) {
        if (instance != null) {
            throw new RuntimeException("DayNightSensor already instantiated.");
        }

        instance = new DayNightSensor(application, samplingDelay);
    }

    /**
     * Default samplingDelay to 3000ms.
     */
    public static void start(Application application) {
        start(application, 3000);
    }


    /**
     * Stop monitor light levels for this application.
     *
     * @param application
     */
    public static void stop(Application application) {
        if (instance == null) {
            return;
        }

        instance.cleanup(application);
    }

    // ----- Nothing below here uses 'instance' -----

    // Private constructor
    private DayNightSensor(Application application, int samplingDelay) {
        this.lifecycleCallback = null;
        this.currentActivity = null;
        this.lightSensor = null;
        this.currentNightMode = AppCompatDelegate.MODE_NIGHT_AUTO;
        this.samplingDelay = samplingDelay;

        sensorManager = (SensorManager) application.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            // No light sensor
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
        }
        else {
            lifecycleCallback = getLifecycleCallback();
            application.registerActivityLifecycleCallbacks(lifecycleCallback);
        }
    }


    public void cleanup(Application application) {
        if (lifecycleCallback != null) {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallback);
            lifecycleCallback = null;
        }

        currentActivity = null;

        if (lightSensor != null) {
            SensorManager sensorManager = (SensorManager) application.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(this, lightSensor);

            lightSensor = null;
        }
    }


    protected Application.ActivityLifecycleCallbacks getLifecycleCallback() {
        return new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                beginMonitoringLightLevels();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                currentActivity = null;
                stopMonitoringLightLevels();
            }

            @Override
            public void onActivityStopped(Activity activity) {
                currentActivity = null;
                stopMonitoringLightLevels();
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                currentActivity = null;
                stopMonitoringLightLevels();
            }
        };
    }


    /**
     * Check light levels every X milliseconds.
     */
    protected void beginMonitoringLightLevels() {
        // Sensor sampling period is in MICROseconds.
        // Most people are used to MILLIseconds so x1000
        sensorManager.registerListener(this, lightSensor, samplingDelay * 1000);
    }

    /**
     * Stop checking light levels (Activity ended, suspended, etc)
     */
    protected void stopMonitoringLightLevels() {
        sensorManager.unregisterListener(this, lightSensor);
    }


    /**
     * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
     * (section regarding Sensor.TYPE_LIGHT)
     *
     * @see https://en.wikipedia.org/wiki/Lux
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        int changeToMode = currentNightMode;
        float lux = event.values[0];

        // Less than LUX_THRESHOLD is considered dark
        if (lux < LUX_THRESHOLD) {
//            Log.w("onSensorChanged DARK", String.valueOf(lux));
            changeToMode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        else {
//            Log.w("onSensorChanged BRIGHT", String.valueOf(lux));
            changeToMode = AppCompatDelegate.MODE_NIGHT_NO;
        }

        // Only update and recreate the Activity if needed!
        if (currentNightMode != changeToMode) {
            AppCompatDelegate.setDefaultNightMode(changeToMode);
            currentNightMode = changeToMode;

            if (currentActivity != null) {
                currentActivity.recreate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
