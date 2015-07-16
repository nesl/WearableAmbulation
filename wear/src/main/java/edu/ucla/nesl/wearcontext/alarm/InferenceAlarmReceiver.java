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
    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENSING_PERIOD = 1000 * 90;

    private final static InferenceType mType = InferenceType.WearAcc;
    private final static boolean logBattery = false;
    private final static boolean featureCalc = true;
    private final static boolean classifyCalc = false;

    private SensorManager mSensorManager;
    private TransportationModeListener mListener;
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
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 * 5, pi); // Millisec * Second * Minute
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

                if (featureCalc) {
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
                }

                data[count] = totalForce;
                count++;
                if (count >= 100) {
                    count = 0;
                }
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

                    if (mType == InferenceType.WearAcc) {
                        if (classifyCalc) {
                            // Decision tree (acc only) from sklearn, max = 5
                            if (wear_rms <= 10.3000907898) {
                                if (wear_var <= 0.00882150046527) {
                                    if (wear_rms <= 9.99174118042) {
                                        if (wear_var <= 0.00562749989331) {
                                            activity = "still";
                                        }
                                    } else {
                                        if (wear_range <= 0.393983006477) {
                                            activity = "walking";
                                        } else {
                                            if (wear_range <= 0.402398496866) {
                                                activity = "transport";
                                            } else {
                                                activity = "walking";
                                            }
                                        }
                                    }
                                } else {
                                    if (wear_rms <= 10.0294046402) {
                                        activity = "transport";
                                    } else {
                                        if (wear_var <= 0.143972992897) {
                                            activity = "walking";
                                        } else {
                                            activity = "transport";
                                        }
                                    }
                                }
                            } else {
                                if (wear_rms <= 10.8408107758) {
                                    if (wear_var <= 2.39609098434) {
                                        if (wear_range <= 5.30206346512) {
                                            if (wear_range <= 3.69736599922) {
                                                activity = "transport";
                                            } else {
                                                activity = "walking";
                                            }
                                        } else {
                                            activity = "transport";
                                        }
                                    } else {
                                        if (wear_range <= 10.4032249451) {
                                            activity = "walking";
                                        } else {
                                            if (wear_var <= 11.2173519135) {
                                                activity = "transport";
                                            } else {
                                                activity = "still";
                                            }
                                        }
                                    }
                                } else {
                                    if (wear_range <= 12.9299907684) {
                                        if (wear_var <= 1.36739695072) {
                                            activity = "transport";
                                        } else {
                                            activity = "walking";
                                        }
                                    } else {
                                        if (wear_fft8 <= 13.5743255615) {
                                            activity = "walking";
                                        } else {
                                            activity = "transport";
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (mType == InferenceType.WearAccGPS) {
                        // Get GPS features
                        double speed = 0.0;
                        double acc = 0.0;

                        // Get fft
                        wear_fft5 = goertzel(mData, 8., n);

                        // Get last known gps speed and accuracy
                        synchronized (InferenceAlarmReceiver.locLock) {
                            speed = locSpeed;
                            acc = locAccuracy;
                        }

                        if (classifyCalc) {
                            // Decision tree (acc + gps), 29 nodes, 88.81% accuracy
                            if (speed < 2.19) {
                                if (wear_var < 0.86) {
                                    if (acc < 19.5) {
                                        if (wear_var < 0.06) {
                                            if (wear_mean < 9.99) {
                                                activity = "transport";
                                            } else {
                                                activity = "walking";
                                            }
                                        } else {
                                            if (acc < 10.5) {
                                                activity = "transport";
                                            } else {
                                                activity = "walking";
                                            }
                                        }
                                    } else {
                                        if (acc < 28.35) {
                                            if (speed < 0.25) {
                                                activity = "still";
                                            } else {
                                                activity = "transport";
                                            }
                                        } else {
                                            if (speed < 0.25) {
                                                activity = "walking";
                                            } else {
                                                activity = "transport";
                                            }
                                        }
                                    }
                                } else {
                                    if (wear_fft5 < 130.7) {
                                        if (speed < 0.13) {
                                            activity = "walking";
                                        } else {
                                            if (acc < 118) {
                                                activity = "transport";
                                            } else {
                                                activity = "still";
                                            }
                                        }
                                    } else {
                                        if (acc < 8.5) {
                                            activity = "walking";
                                        } else {
                                            if (acc < 10.5) {
                                                activity = "still";
                                            } else {
                                                activity = "walking";
                                            }
                                        }
                                    }
                                }
                            } else {
                                activity = "transport";
                            }
                        }
                    }

                    long toc = System.nanoTime();
                    //Log.d(TAG, String.format("Transportation mode: %s, time used = %d ns", activity, (toc - tic)));
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
