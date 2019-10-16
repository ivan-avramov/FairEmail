package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2019 by Marcel Bokhorst (M66B)
*/

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;

public class ServiceExternal extends Service {
    private static final String ACTION_POLL = BuildConfig.APPLICATION_ID + ".POLL";
    private static final String ACTION_ENABLE = BuildConfig.APPLICATION_ID + ".ENABLE";
    private static final String ACTION_DISABLE = BuildConfig.APPLICATION_ID + ".DISABLE";

    // adb shell am startservice -a eu.faircode.email.POLL
    // adb shell am startservice -a eu.faircode.email.ENABLE --es account Gmail
    // adb shell am startservice -a eu.faircode.email.DISABLE --es account Gmail

    private static final ExecutorService executor =
            Helper.getBackgroundExecutor(1, "external");


    @Override
    public void onCreate() {
        Log.i("Service external create");
        super.onCreate();
        startForeground(Helper.NOTIFICATION_EXTERNAL, getNotification().build());
    }

    @Override
    public void onDestroy() {
        Log.i("Service external destroy");
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            EntityLog.log(this, "Service external intent=" + intent);
            Log.logExtras(intent);

            super.onStartCommand(intent, flags, startId);
            startForeground(Helper.NOTIFICATION_EXTERNAL, getNotification().build());

            if (intent == null)
                return START_NOT_STICKY;

            if (!ActivityBilling.isPro(this))
                return START_NOT_STICKY;

            String action = intent.getAction();

            if (ACTION_POLL.equals(action)) {
                ServiceSynchronize.process(this, true);
                return START_NOT_STICKY;
            }

            final Boolean enabled;
            if (ACTION_ENABLE.equals(action))
                enabled = true;
            else if (ACTION_DISABLE.equals(action))
                enabled = false;
            else
                enabled = null;

            if (enabled != null) {
                final String accountName = intent.getStringExtra("account");
                if (accountName == null) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putBoolean("schedule", false).apply();

                    boolean previous = prefs.getBoolean("enabled", true);
                    if (!enabled.equals(previous)) {
                        prefs.edit().putBoolean("enabled", enabled).apply();
                        ServiceSynchronize.reload(this, "external");
                    }
                } else {
                    final Context context = getApplicationContext();
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            DB db = DB.getInstance(context);
                            EntityAccount account = db.account().getAccount(accountName);
                            if (account != null) {
                                db.account().setAccountSynchronize(account.id, enabled);
                                ServiceSynchronize.reload(context, "account enabled=" + enabled);
                            }
                        }
                    });
                }
            }

            return START_NOT_STICKY;
        } finally {
            stopSelf(startId);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private NotificationCompat.Builder getNotification() {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "service")
                        .setSmallIcon(R.drawable.baseline_compare_arrows_white_24)
                        .setContentTitle(getString(R.string.tile_synchronize))
                        .setAutoCancel(false)
                        .setShowWhen(false)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                        .setLocalOnly(true);

        return builder;
    }
}
