package com.andres.zengecko;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Opens Android's browser-role/default-app UI without assuming an OEM. */
public final class BrowserRoleHelper {
    private BrowserRoleHelper() { }

    public static void requestDefaultBrowser(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roles = activity.getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        Toast.makeText(activity,
                                "Zen Browser ya es el navegador predeterminado",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    activity.startActivity(
                            roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER));
                    return;
                }
            }
            activity.startActivity(
                    new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Throwable first) {
            try {
                Intent details = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                details.setData(android.net.Uri.parse(
                        "package:" + activity.getPackageName()));
                activity.startActivity(details);
            } catch (Throwable second) {
                Toast.makeText(activity,
                        "Android no expuso la selección de navegador",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
