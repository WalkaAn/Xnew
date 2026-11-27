package io.github.wzhy.xaudiocapture;

import android.app.Application;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.init(null);
    }
}
