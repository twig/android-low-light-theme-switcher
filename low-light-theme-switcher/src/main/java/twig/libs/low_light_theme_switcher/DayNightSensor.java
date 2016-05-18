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

import java.util.concurrent.TimeUnit;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Created by twig on 27/02/2016.
 */
public class DayNightSensor implements SensorEventListener {
    // Threshold before lighting is considered "dark"
    public static final float LUX_THRESHOLD = 4.0f;
    // Interval between light samples taken in milliseconds
    public static final int DEFAULT_INTERVAL = 3000;
    // Default value for auto-recreate of activity
    public static final boolean DEFAULT_AUTORESTART_ACTIVITY = true;


    // Easier for users to customise the library
    public static class Settings {
        // Threshold before lighting is considered "dark"
        public float luxThreshold = LUX_THRESHOLD;
        // Interval between light samples taken in milliseconds
        public int samplingDelay = DEFAULT_INTERVAL;
        // Should the library auto-restart the activity when lighting changes?
        public boolean autoRestartActivity = DEFAULT_AUTORESTART_ACTIVITY;
    }


    // Single instance
    private static DayNightSensor instance;


    // Class fields - state
    private Application.ActivityLifecycleCallbacks lifecycleCallback;
    private Activity currentActivity;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private int currentNightMode;
    private PublishSubject<Float> throttler;

    // Class fields - configuration
    private float luxThreshold;
    private int samplingDelay;
    private boolean autoRestartActivity;


    /**
     * Begin monitoring with default settings.
     */
    public static void start(Application application) {
        start(application, new Settings());
    }

    /**
     * Begin monitoring the light levels for this application.
     *
     * @param application
     * @param settings Customised settings.
     */
    public static void start(Application application, Settings settings) {
        if (instance != null) {
            throw new RuntimeException("DayNightSensor already instantiated.");
        }

        instance = new DayNightSensor(application, settings);
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
    private DayNightSensor(Application application, Settings settings) {
        this.lifecycleCallback = null;
        this.currentActivity = null;
        this.lightSensor = null;
        this.currentNightMode = AppCompatDelegate.MODE_NIGHT_AUTO;

        this.luxThreshold = settings.luxThreshold;
        this.samplingDelay = settings.samplingDelay;
        this.autoRestartActivity = settings.autoRestartActivity;

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
//                Log.e("onActivityResumed", activity.toString());

                currentActivity = activity;
                beginMonitoringLightLevels();
            }

            @Override
            public void onActivityPaused(Activity activity) {
//                Log.e("onActivityPaused", activity.toString());

                if (activity == currentActivity) {
                    currentActivity = null;
                    stopMonitoringLightLevels();
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
//                Log.e("onActivityStopped", activity.toString());

                if (activity == currentActivity) {
                    currentActivity = null;
                    stopMonitoringLightLevels();
                }
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
//                Log.e("onActivityDestroyed", activity.toString());

                if (activity == currentActivity) {
                    currentActivity = null;
                    stopMonitoringLightLevels();
                }
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

        // Stops working after activity restarted
        throttler = PublishSubject.create();
        throttler
            .throttleFirst(samplingDelay, TimeUnit.MILLISECONDS, Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Action1<Float>() {
                @Override
                public void call(Float lux) {
//                    Log.e("throttled", String.valueOf(lux));
                    doSensorChange(lux);
                }
            })
        ;
    }

    /**
     * Stop checking light levels (Activity ended, suspended, etc)
     */
    protected void stopMonitoringLightLevels() {
        sensorManager.unregisterListener(this, lightSensor);
//        throttler.onCompleted();
    }


    /**
     * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
     * (section regarding Sensor.TYPE_LIGHT)
     *
     * @see https://en.wikipedia.org/wiki/Lux
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        float lux = event.values[0];
//        Log.e("onSensorChanged", String.valueOf(lux));

        throttler.onNext(lux);
    }

    protected void doSensorChange(float lux) {
        int changeToMode = currentNightMode;

        // Less than LUX_THRESHOLD is considered dark
        if (lux < luxThreshold) {
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

            if (currentActivity != null && autoRestartActivity) {
                currentActivity.recreate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
