package edu.ucla.nesl.wearcontext.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by cgshen on 7/12/15.
 */
public class InferenceAlarmStarter extends BroadcastReceiver {
    private InferenceAlarmReceiver alarm = new InferenceAlarmReceiver();


    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
//            alarm.setAlarm(context);
        }
    }
}
