package de.buttercookie.simbadroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;

import de.buttercookie.simbadroid.service.SmbService;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "SimbaDroidPrefs";
    private static final String PREF_KEY_START_ON_BOOT = "start_on_boot";
    private static final String PREF_KEY_IP_ADDRESS = "selectedIpAddress";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            Context storageContext = context.createDeviceProtectedStorageContext();
            SharedPreferences sharedPreferences = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean startOnBoot = sharedPreferences.getBoolean(PREF_KEY_START_ON_BOOT, false);

            if (startOnBoot) {
                String selectedIp = sharedPreferences.getString(PREF_KEY_IP_ADDRESS, null);
                SmbService.startService(context, false, selectedIp);
            }
        }
    }
}