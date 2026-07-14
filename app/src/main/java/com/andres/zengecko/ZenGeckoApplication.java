package com.andres.zengecko;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

public final class ZenGeckoApplication extends Application {
    private static final String TAG = "ZenGecko/Runtime";
    private static GeckoRuntime runtime;

    public static synchronized GeckoRuntime runtime(Context context) {
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(ZenPanelController.javaScriptEnabled(context))
                    .remoteDebuggingEnabled(BuildConfig.DEBUG)
                    .consoleOutput(BuildConfig.DEBUG)
                    .debugLogging(BuildConfig.DEBUG)
                    .build();
            Log.i(TAG, "Creating GeckoRuntime lazily");
            runtime = GeckoRuntime.create(context.getApplicationContext(), settings);
        }
        return runtime;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Do not warm Gecko eagerly here. BrowserRepository creates it when the first session is
        // needed, avoiding a startup race observed on HyperOS during the first configuration pass.
        Log.i(TAG, "Application created; GeckoRuntime will initialize on demand");
    }
}
