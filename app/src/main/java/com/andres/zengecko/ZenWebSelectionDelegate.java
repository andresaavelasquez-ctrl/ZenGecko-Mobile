package com.andres.zengecko;

import android.app.Activity;
import org.mozilla.geckoview.BasicSelectionActionDelegate;

/**
 * Expone las acciones flotantes de Android para texto dentro de GeckoView.
 */
public final class ZenWebSelectionDelegate extends BasicSelectionActionDelegate {
    public ZenWebSelectionDelegate(Activity activity) {
        super(activity, true);
        enableExternalActions(true);
    }
}
