package io.github.wzhy.xaudiocapture.xposed;

import static io.github.wzhy.xaudiocapture.Configuration.PREF_NAME;
import static io.github.wzhy.xaudiocapture.Constants.LOG_TAG;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    // taken from ApplicationInfo.java
    // https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/content/pm/ApplicationInfo.java
    private static final int PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE = 1 << 27;
    private Field privateFlagsField = null;
    private SharedPreferences mPref;

    @SuppressLint("DiscouragedPrivateApi")
    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, LOG_TAG, "onModuleLoaded: " + param.getProcessName());
        try {
            privateFlagsField = ApplicationInfo.class.getDeclaredField("privateFlags");
            privateFlagsField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log(Log.WARN, LOG_TAG, "Failed to reflect privateFlags field: " + Log.getStackTraceString(e));
        }
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, LOG_TAG, "System server starting, deploying hook...");
        mPref = getRemotePreferences(PREF_NAME);
        deployHooks(param.getClassLoader());
    }

    private void deployHooks(ClassLoader classLoader) {
        try {
            ClassLoader cl = (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
            @SuppressLint("PrivateApi") Class<?> computerEngineClass = Class.forName(
                    "com.android.server.pm.ComputerEngine",
                    true,
                    cl
            );

            for (Method method : computerEngineClass.getDeclaredMethods()) {
                String methodName = method.getName();

                if (methodName.equals("getApplicationInfo") ||
                        methodName.equals("getApplicationInfoInternal") ||
                        methodName.equals("getInstalledApplications") ||
                        methodName.equals("getInstalledApplicationsListInternal")) {
                    deoptimize(method);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result == null) return null;

                        if (result instanceof ApplicationInfo) {
                            hookPrivateFlags((ApplicationInfo) result);
                        } else if (result instanceof List) {
                            for (Object obj : (List<?>) result) {
                                if (obj instanceof ApplicationInfo) {
                                    hookPrivateFlags((ApplicationInfo) obj);
                                }
                            }
                        } else if (result.getClass().getName().equals("android.content.pm.ParceledListSlice")) {
                            try {
                                Method getListMethod = result.getClass().getMethod("getList");
                                List<?> list = (List<?>) getListMethod.invoke(result);
                                if (list != null) {
                                    for (Object obj : list) {
                                        if (obj instanceof ApplicationInfo) {
                                            hookPrivateFlags((ApplicationInfo) obj);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log(Log.ERROR, LOG_TAG, "Failed to process ParceledListSlice elements: " + e.getMessage());
                            }
                        }
                        return result;
                    });
                    log(Log.INFO, LOG_TAG, "Successfully attached interceptor to method: " + methodName);
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, LOG_TAG, "Failed to deploy ComputerEngine interceptors", t);
        }
    }

    private void hookPrivateFlags(ApplicationInfo info) {
        if (info != null && privateFlagsField != null) {
            if (mPref.getBoolean(info.packageName, false)) {
                try {
                    int privateFlags = privateFlagsField.getInt(info);
                    privateFlags |= PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE;
                    privateFlagsField.setInt(info, privateFlags);
                    //log(Log.INFO, LOG_TAG, "Dynamic injection success for: " + info.packageName);
                } catch (Exception e) {
                    log(Log.ERROR, LOG_TAG, "Failed to modify privateFlags for " + info.packageName + ": " + e.getMessage());
                }
            }
        }
    }
}
