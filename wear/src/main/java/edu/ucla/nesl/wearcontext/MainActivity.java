package edu.ucla.nesl.wearcontext;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.Random;

import edu.ucla.nesl.wearcontext.alarm.InferenceAlarmReceiver;

public class MainActivity extends Activity {
    private static final String TAG = "WearContext/Wear/MainActivity";
    private DeviceClient client;
    private Random random;
    private boolean mAlarmSet = false;

    private InferenceAlarmReceiver alarm = new InferenceAlarmReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        client = DeviceClient.getInstance(this);
        random = new Random();

        alarm.setAlarm(this);

//        // TODO: Keep the Wear screen always on (for testing only!)
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onBeep(View view) {
        client.sendSensorData(555, ByteBuffer.allocate(4).putFloat(random.nextFloat()).array());
//        if (mAlarmSet == false) {
//            alarm.setAlarm(this);
//            mAlarmSet = true;
//        }
//        else {
//            alarm.cancelAlarm(this);
//            mAlarmSet = false;
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");
        // Read alarm info from shared preference
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        mAlarmSet = sharedPref.getBoolean(getString(R.string.alarm_set), false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause() called");
        // Save to shared preference
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.alarm_set), mAlarmSet);
        editor.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy() called");
    }
}
