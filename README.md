# Android: Low Light Theme Switcher

This helper library will automatically switches your Activity between Dark/Light themes using the device light sensor and DayNight theme from Support Library 23.2.

The DayNight theme available in Support Library is a really neat feature, but functionally it's not that useful. Even though it's night by time definition, I could still be in a well lit room and it would still show me the night theme.

I beleive that this default behaviour is due to a limitation that not all Android devices will have a light sensor. If the target device does not have a light sensor then it simply falls back to the default behaviour of AppCompatDelegate.MODE_NIGHT_AUTO.

Requires API 14+.

[![demo video](http://img.youtube.com/vi/MVIeD-2MtGk/0.jpg)](http://www.youtube.com/watch?v=MVIeD-2MtGk)

# Adding it to your project

Add the jitpack repo to your project build.gradle

	allprojects {
		repositories {
			...
			maven { url "https://jitpack.io" }
		}
	}

Add the library to your module build.gradle

	dependencies {
	        compile 'com.github.twig:android-low-light-theme-switcher:1.1.0'
	}

# Usage

    public class YourApplication extends Application {
        @Override
        public void onCreate() {
            super.onCreate();
  
            // Change between day/night/auto
            DayNightSensor.start(this);
        }
  
        @Override
        public void onTerminate() {
            DayNightSensor.stop(this);
            super.onTerminate();
        }
    }

# Options

## DayNightSensor.start(this, [settings]);

Parameter `settings` is an instance of `DayNightSensor.Settings`.

By default:

- `Settings.luxThreshold` is currently `4.0` lumens. Still trying to figure out a good "dark enough" value.
- `Settings.samplingDelay` checks the light sensor values every `3,000` milliseconds.
- `Settings.autoRestartActivity` will automatically recreate the current activity if darkness levels change. Set to `false` if you don't want it to cause jitters.


# Technical details

- If `autoRestartActivity` is `true`, DayNightSensor calls `Activity.recreate()` when a change in brightness is detected.
- Make sure that your Activity saves and restores instance state data correctly.
