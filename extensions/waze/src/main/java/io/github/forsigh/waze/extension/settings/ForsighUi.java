package io.github.forsigh.waze.extension.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

/**
 * Asks before restarting Waze. Several patched values are read once at startup or pushed into the
 * native config store on sync, so a change is applied by starting a fresh process.
 *
 * All user-visible text is a literal, so it stays English in every locale.
 */
public final class ForsighUi {

    private static final String RESTART_TITLE = "Restart Waze";
    private static final String RESTART_CONFIRM = "Restart";
    private static final String RESTART_LATER = "Later";

    private ForsighUi() {
    }

    public static void restartApp(Context context) {
        if (context == null) {
            return;
        }
        final String packageName = context.getPackageName();
        final Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null || launch.getComponent() == null) {
            return;
        }
        final Intent mainIntent = Intent.makeRestartActivityTask(launch.getComponent());
        mainIntent.setPackage(packageName);
        context.startActivity(mainIntent);
        System.exit(0);
    }

    public static void showRestartDialog(final Activity activity, String message) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(RESTART_TITLE)
                .setMessage(message)
                .setPositiveButton(RESTART_CONFIRM, (dialog, which) -> restartApp(activity))
                .setNegativeButton(RESTART_LATER, null)
                .show();
    }
}
