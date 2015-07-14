package edu.ucla.nesl.wearcontext.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.textservice.SentenceSuggestionsInfo;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by cgshen on 7/12/15.
 */
public class InferenceAlarmReceiver extends BroadcastReceiver {
    enum InferenceType {
        NoInference,
        WearAcc,
        WearAccGPS
    }

    private final static String TAG = "WearContext/Wear/InferenceAlarmReceiver";
    private final static long timestamp = System.currentTimeMillis();
    private final static InferenceType mType = InferenceType.WearAcc;
    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENSING_PERIOD = 1000 * 10;

    private SensorManager mSensorManager;
    private TransportationModeListener mListener;
    private Vibrator v;
    private static int numThreads;
    public static double locSpeed;
    public static double locAccuracy;

    private static final Object lock = new Object();
    public static final Object locLock = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock");
        wl.acquire();

        Log.i(TAG, "InferenceAlarmReceiver Received, mType=" + mType);

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
        if (mType != InferenceType.NoInference) {
            // Start inference using only acc for 1 minute (10s for test)
            mSensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
            Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);

            // Register the listener
            if (mSensorManager != null) {
                if (accelerometerSensor != null) {
                    mListener = new TransportationModeListener(mType);
                    mSensorManager.registerListener(mListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);


                } else {
                    Log.w(TAG, "No Accelerometer found");
                }
            }

            // Stop after 1 minute (10s for test)
            Handler mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSensorManager.unregisterListener(mListener);
                    mSensorManager = null;
                }
            }, SENSING_PERIOD);
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

    private class TransportationModeListener implements SensorEventListener {
        InferenceType mType;
        int count = 0;
        Thread thread;
        long start = 0;
        double[] data = new double[100];

        // Type define the sensor and model used (e.g. AccOnly or Acc+GPS)
        public TransportationModeListener(InferenceType infType) {
            super();
            mType = infType;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
            // Log.d(TAG, "onSensorDataChanged");
            if (event.sensor.getType() == SENS_ACCELEROMETER) {
                float [] values = event.values;
                double totalForce = 0.0;
                double accx = values[0];
                double accy = values[1];
                double accz = values[2];
                totalForce += Math.pow(accx, 2.0);
                totalForce += Math.pow(accy, 2.0);
                totalForce += Math.pow(accz, 2.0);
                totalForce = Math.sqrt(totalForce);

                long cur = System.currentTimeMillis();

                // 1s classification window
                if (cur - start >= 1000) {
                    start = cur;
                    double[] classData = new double[count];
                    System.arraycopy(data, 0, classData, 0, classData.length);

                    if (count >= 5) {
                        thread = new Thread(new Worker(classData));
                        // Log.d(TAG, "Starting thread");
                        thread.start();
                        numThreads++;
                    }
                    else {
                        Log.d(TAG, "Transportation mode: still (too few acc samples)");
                    }
                    count = 0;
                    //Log.d(TAG, "Reset samples");
                }
                if (count >= 100) {
                    Log.d(TAG, "Wha?? " + System.currentTimeMillis() + " is the time now and we started samples at " + start + " and there are " + count
                            + " samples");
                    return;
                }
                data[count] = totalForce;
                count++;
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        private class Worker implements Runnable {
            double[] mData;

            public Worker(double[] _data) {
                mData = _data;
            }

            @Override
            public void run() {
                synchronized(lock) {
                    long tic = System.nanoTime();

                    String activity = null;
                    int n = mData.length;

                    // Features: mean, var, and fft(5)
                    double sum = 0.0;
                    double mean = 0.0;
                    double var = 0.0;
                    double accFft5 = 0.0;

                    // Get mean an var
                    for (int i = 0; i < n; i++) {
                        sum += mData[i];
                    }
                    mean = sum / n;

                    sum = 0.0;
                    for (int i = 0; i < n; i++){
                        sum += Math.pow((mData[i] - mean), 2.0);
                    }
                    var = sum / n;

                    // Get fft
                    accFft5 = goertzel(mData, 5., n);

                    if (mType == InferenceType.WearAcc) {
                        // Decision tree (acc only), 11 nodes, 78.17% accuracy
                        if (var < 2.01) {
                            if (var < 0.01) {
                                if (mean < 9.88) {
                                    activity = "still";
                                }
                                else {
                                    if (mean < 9.99) {
                                        activity = "transport";
                                    }
                                    else {
                                        activity = "walking";
                                    }
                                }
                            }
                            else {
                                activity = "transport";
                            }
                        }
                        else {
                            if (accFft5 < 133.17) {
                                activity = "transport";
                            }
                            else {
                                activity = "walking";
                            }
                        }
                    }
                    else if (mType == InferenceType.WearAccGPS) {
                        // Get GPS features
                        double speed = 0.0;
                        double acc = 0.0;

                        // Get last known gps speed and accuracy
                        synchronized (InferenceAlarmReceiver.locLock) {
                            speed = locSpeed;
                            acc = locAccuracy;
                        }

                        // Decision tree (acc + gps), 29 nodes, 88.81% accuracy
                        if (speed < 2.19) {
                            if (var < 0.86) {
                                if (acc < 19.5) {
                                    if (var < 0.06) {
                                        if (mean < 9.99) {
                                            activity = "transport";
                                        }
                                        else {
                                            activity = "walking";
                                        }
                                    }
                                    else {
                                        if (acc < 10.5) {
                                            activity = "transport";
                                        }
                                        else {
                                            activity = "walking";
                                        }
                                    }
                                }
                                else {
                                    if (acc < 28.35) {
                                        if (speed < 0.25) {
                                            activity = "still";
                                        }
                                        else {
                                            activity = "transport";
                                        }
                                    }
                                    else {
                                        if (speed < 0.25) {
                                            activity = "walking";
                                        }
                                        else {
                                            activity = "transport";
                                        }
                                    }
                                }
                            }
                            else {
                                if (accFft5 < 130.7) {
                                    if (speed < 0.13) {
                                        activity = "walking";
                                    }
                                    else {
                                        if (acc < 118) {
                                            activity = "transport";
                                        }
                                        else {
                                            activity = "still";
                                        }
                                    }
                                }
                                else {
                                    if (acc < 8.5) {
                                        activity = "walking";
                                    }
                                    else {
                                        if (acc < 10.5) {
                                            activity = "still";
                                        }
                                        else {
                                            activity = "walking";
                                        }
                                    }
                                }
                            }
                        }
                        else {
                            activity = "transport";
                        }
                    }

                    long toc = System.nanoTime();
                    Log.d(TAG, String.format("Transportation mode: %s, time used = %d ns", activity, (toc - tic)));
                    // Log.d(TAG, String.format("There are %d threads", numThreads));
                    numThreads--;
                }
            }
        }

        private double goertzel(double [] data, double freq, double sr) {
            double s_prev = 0;
            double s_prev2 = 0;
            double coeff = 2 * Math.cos( (2*Math.PI*freq) / sr);
            double s;
            for (int i = 0; i < data.length; i++) {
                double sample = data[i];
                s = sample + coeff*s_prev  - s_prev2;
                s_prev2 = s_prev;
                s_prev = s;
            }
            double power = s_prev2*s_prev2 + s_prev*s_prev - coeff*s_prev2*s_prev;

            return power;
        }
    }
}
