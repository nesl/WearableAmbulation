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
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

import edu.ucla.nesl.wearcontext.DataMapClient;
import edu.ucla.nesl.wearcontext.shared.InferenceType;

/**
 * Created by cgshen on 7/12/15.
 */
public class InferenceAlarmReceiver extends BroadcastReceiver {

    private final static byte[] BYTE_DATA_1K = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127};

    private final static String TAG = "Wear/InfAlarmReceiver";
    private final static long timestamp = System.currentTimeMillis();
    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENSING_PERIOD = 1000 * 150;
    private final static int ALARM_INTERVAL = 1000 * 60 * 5; // Millisec * Second * Minute

    private final static InferenceType mType = InferenceType.WearPhoneAcc;
    private final static boolean logBattery = false;
    private final static boolean featureCalc = true;
    private final static boolean classifyCalc = false;

    private SensorManager mSensorManager;
    private TransportationModeListener mListener;
    private DataMapClient mClient;
    private static int numThreads;
    private static int resCount = 0;

    public static double locSpeed;
    public static double locAccuracy;

    private static final Object lock = new Object();
    public static final Object locLock = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock");
        wl.acquire();

        mClient = DataMapClient.getInstance(context);

        Log.i(TAG, "InferenceAlarmReceiver received, mType=" + mType + ", feature=" + featureCalc + ", classify=" + classifyCalc);

        if (logBattery) {
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level / (float)scale;
                Log.d(TAG, "Battery level = " + batteryPct);

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

            IntentFilter ifilter1 = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus1 = context.registerReceiver(null, ifilter1);

            // Are we charging / charged?
            int status = batteryStatus1.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;

            Log.i("PowerConnectionReceiver", "isCharging==" + isCharging);
        }

        if (mType == InferenceType.NoInference) {
            int j = 0;
            for (int i = 0; i < 60; i++) {
                j++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (mType == InferenceType.DataTransmission) {
            // Test sending data from watch to phone
            Log.i(TAG, "Sending data via BLE...");

            for (int i = 0; i < 30; i++) {
                long tic = System.currentTimeMillis();
                for (int ii = 0; ii < 10; ii++) {
                    mClient.sendSensorData(System.currentTimeMillis(), BYTE_DATA_1K);
                }
                long tac = System.currentTimeMillis();
                Log.i(TAG, "Sending data finished, time=" + (tac - tic) + "ms");

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "Sending data ALL finished.");
        }
        else if (mType == InferenceType.WearAcc || mType == InferenceType.WearAccGPS || mType == InferenceType.WearPhoneAcc) {
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
                    Log.i(TAG, "InferenceAlarmReceiver execution finished.");
                }
            }, SENSING_PERIOD);
        }

        wl.release();

    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, InferenceAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), ALARM_INTERVAL, pi);
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
            // client.sendData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
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

                    int activity = -1;
                    int n = mData.length;

                    if (mType == InferenceType.WearAcc) {
                        // In this case, all data come from the watch
                        // Watch can choose to send raw data, features, or classification labels
                        if (featureCalc) {
                            // Features: mean, var, and fft(5)
                            double wear_sum = 0.0;
                            double wear_mean = 0.0;
                            double wear_var = 0.0;
                            double wear_rms = 0.0;
                            double wear_range = 0.0;
                            double wear_min = Double.MAX_VALUE;
                            double wear_max = Double.MIN_VALUE;
                            double wear_fft5 = 0.0;
                            double wear_fft8 = 0.0;

                            // Get mean, var, rms, range
                            for (int i = 0; i < n; i++) {
                                double temp = mData[i];
                                wear_sum += temp;
                                wear_rms += Math.pow(temp, 2.0);
                                wear_min = Math.min(wear_min, temp);
                                wear_max = Math.max(wear_max, temp);
                            }
                            wear_mean = wear_sum / n;

                            wear_range = Math.abs(wear_max - wear_min);

                            wear_rms = Math.sqrt(wear_rms / n);

                            wear_sum = 0.0;
                            for (int i = 0; i < n; i++){
                                wear_sum += Math.pow((mData[i] - wear_mean), 2.0);
                            }
                            wear_var = wear_sum / n;

                            // Get fft
                            wear_fft8 = goertzel(mData, 8., n);

                            if (classifyCalc) {
                                // Decision tree (acc only) from sklearn, max = 5
                                if (wear_rms <= 10.3000907898) {
                                    if (wear_var <= 0.00882150046527) {
                                        if (wear_rms <= 9.99174118042) {
                                            if (wear_var <= 0.00562749989331) {
                                                activity = 1;
                                            }
                                        } else {
                                            if (wear_range <= 0.393983006477) {
                                                activity = 2;
                                            } else {
                                                if (wear_range <= 0.402398496866) {
                                                    activity = 3;
                                                } else {
                                                    activity = 2;
                                                }
                                            }
                                        }
                                    } else {
                                        if (wear_rms <= 10.0294046402) {
                                            activity = 3;
                                        } else {
                                            if (wear_var <= 0.143972992897) {
                                                activity = 2;
                                            } else {
                                                activity = 3;
                                            }
                                        }
                                    }
                                } else {
                                    if (wear_rms <= 10.8408107758) {
                                        if (wear_var <= 2.39609098434) {
                                            if (wear_range <= 5.30206346512) {
                                                if (wear_range <= 3.69736599922) {
                                                    activity = 3;
                                                } else {
                                                    activity = 2;
                                                }
                                            } else {
                                                activity = 3;
                                            }
                                        } else {
                                            if (wear_range <= 10.4032249451) {
                                                activity = 2;
                                            } else {
                                                if (wear_var <= 11.2173519135) {
                                                    activity = 3;
                                                } else {
                                                    activity = 1;
                                                }
                                            }
                                        }
                                    } else {
                                        if (wear_range <= 12.9299907684) {
                                            if (wear_var <= 1.36739695072) {
                                                activity = 3;
                                            } else {
                                                activity = 2;
                                            }
                                        } else {
                                            if (wear_fft8 <= 13.5743255615) {
                                                activity = 2;
                                            } else {
                                                activity = 3;
                                            }
                                        }
                                    }
                                }

//                                // Send classification result over bluetooth
//                                mClient.sendSensorData(tic, ByteBuffer.allocate(4).putInt(activity).array());
//                                Log.i(TAG, "sync result labels...");
                            }
                            else {
                                // Send features over bluetooth
                                ByteBuffer buf = ByteBuffer.allocate(8 * 5);
                                buf.putDouble(wear_var);
                                buf.putDouble(wear_fft8);
                                buf.putDouble(wear_fft5);
                                buf.putDouble(wear_rms);
                                buf.putDouble(wear_range);
                                mClient.sendSensorData(tic, buf.array());
                                Log.i(TAG, "sync features...");
                            }
                        }
                        else {
                            // Send raw data over bluetooth
                            ByteBuffer buf = ByteBuffer.allocate(8 * n);
                            for (int i = 0; i < n; i++) {
                                buf.putDouble(mData[i]);
                            }
                            mClient.sendSensorData(tic, buf.array());
                            Log.i(TAG, "Sending raw data...");
                        }
                    }
                    else if (mType == InferenceType.WearPhoneAcc) {
                        // In this case, the phone need 3 wearable features
                        // Watch can choose to send features or raw data
                        if (featureCalc) {
                            // Features: var, fft (9), and fft(10)
                            double wear_sum = 0.0;
                            double wear_mean = 0.0;
                            double wear_var = 0.0;
                            double wear_fft9 = 0.0;
                            double wear_fft10 = 0.0;

                            // Get mean, var, rms, range
                            for (int i = 0; i < n; i++) {
                                double temp = mData[i];
                                wear_sum += temp;
                            }
                            wear_mean = wear_sum / n;

                            wear_sum = 0.0;
                            for (int i = 0; i < n; i++){
                                wear_sum += Math.pow((mData[i] - wear_mean), 2.0);
                            }
                            wear_var = wear_sum / n;

                            // Get fft
                            wear_fft9 = goertzel(mData, 9., n);
                            wear_fft10 = goertzel(mData, 10., n);

                            // Send features over bluetooth
                            ByteBuffer buf = ByteBuffer.allocate(8 * 3);
                            buf.putDouble(wear_var);
                            buf.putDouble(wear_fft9);
                            buf.putDouble(wear_fft10);
                            mClient.sendSensorData(tic, buf.array());
                            Log.i(TAG, "Sending features...");
                        }
                        else {
                            // Send raw data over bluetooth
                            ByteBuffer buf = ByteBuffer.allocate(8 * n);
                            for (int i = 0; i < 8 * n; i++) {
                                buf.putDouble(mData[i]);
                            }
                            mClient.sendSensorData(tic, buf.array());
                            Log.i(TAG, "sync raw data...");
                        }
                    }

                    long toc = System.nanoTime();
                    Log.d(TAG, String.format("act=%d, time=%dns", activity, (toc - tic)));
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
