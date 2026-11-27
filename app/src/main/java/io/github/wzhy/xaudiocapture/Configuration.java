package io.github.wzhy.xaudiocapture;

import static io.github.wzhy.xaudiocapture.Constants.LOG_TAG;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class Configuration {
    public static final String PREF_NAME = "enabled_apps";
    public static int mApiVersion = -1;
    private static SharedPreferences mRemotePrefs = null;
    private static Runnable onConnectedCallback = null;

    public static void init(@Nullable Runnable onConnected) {
        onConnectedCallback = onConnected;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                //Log.i(LOG_TAG, "Successfully connected to Xposed Service Database.");
                mRemotePrefs = service.getRemotePreferences(PREF_NAME);
                mApiVersion = service.getApiVersion();
                if (onConnectedCallback != null) onConnectedCallback.run();
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                Log.w(LOG_TAG, "Xposed Service disconnected.");
                mRemotePrefs = null;
                mApiVersion = -1;
            }
        });
    }

    public static int getActiveXposedVersion() {
            return mApiVersion;
    }

    public static void add(String packageName) {
        if (mRemotePrefs != null) {
            mRemotePrefs.edit().putBoolean(packageName, true).apply();
        } else {
            Log.w(LOG_TAG, "Service not connected, failed to add: " + packageName);
        }
    }

    public static void remove(String packageName) {
        if (mRemotePrefs != null) {
            mRemotePrefs.edit().remove(packageName).apply();
        } else {
            Log.w(LOG_TAG, "Service not connected, failed to remove: " + packageName);
        }
    }

    public static boolean isEnabled(String packageName) {
        if (mRemotePrefs != null) {
            return mRemotePrefs.getBoolean(packageName, false);
        }
        return false;
    }
}
