package edu.ucla.nesl.wearcontext.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by cgshen on 7/12/15.
 */
public class InferenceAlarmReceiver extends BroadcastReceiver {
    private final static String TAG = "WearContext/Wear/InferenceAlarmReceiver";
    private final static long timestamp = System.currentTimeMillis();

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock");
        wl.acquire();

        Log.i(TAG, "InferenceAlarmReceiver Fired!");

        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float)scale;

            BufferedWriter outputBattery = new BufferedWriter(new FileWriter("/sdcard/wearcontext/battery_normal_use_" + timestamp + ".txt", true));

            if (batteryPct > 0.2) {
                outputBattery.append(String.valueOf(System.currentTimeMillis()) + "," + String.valueOf(batteryPct) + "\n");
                outputBattery.flush();

                Log.d(TAG, "Battery level = " + batteryPct);
            }
            else {
                outputBattery.flush();
                outputBattery.close();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


        wl.release();
    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, InferenceAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 * 1, pi); // Millisec * Second * Minute
        Log.i(TAG, "Alarm set.");
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, InferenceAlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
        Log.i(TAG, "Alarm cancelled.");
    }
}
