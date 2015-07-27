package edu.ucla.nesl.wearcontext;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.Button;

import edu.ucla.nesl.wearcontext.alarm.InferenceAlarmReceiver;


public class MainActivity extends ActionBarActivity  {
    private static final String TAG = "Mobile/MainActivity";
    private static InferenceAlarmReceiver alarmReceiver  = new InferenceAlarmReceiver();
    private Context context;
    private RemoteSensorListener remoteSensorListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(toolbar);

        remoteSensorListener = RemoteSensorListener.getInstance(this);

        context = this;

        Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alarmReceiver.setAlarm(context);
                // remoteSensorListener.connect();
            }
        });

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alarmReceiver.cancelAlarm(context);
                // remoteSensorListener.disconnect();
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy() called");
    }
}
