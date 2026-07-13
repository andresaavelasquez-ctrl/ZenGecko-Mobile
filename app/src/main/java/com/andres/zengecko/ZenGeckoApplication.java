package com.andres.zengecko;

import android.app.Application;
import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

public final class ZenGeckoApplication extends Application {
    private static GeckoRuntime runtime;

    public static synchronized GeckoRuntime runtime(Context context) {
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .remoteDebuggingEnabled(BuildConfig.DEBUG)
                    .build();
            runtime = GeckoRuntime.create(context.getApplicationContext(), settings);
        }
        return runtime;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        runtime(this).warmUp();
    }
}
