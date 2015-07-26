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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import edu.ucla.nesl.wearcontext.shared.InferenceType;

/**
 * Created by cgshen on 7/12/15.
 */
public class InferenceAlarmReceiver extends BroadcastReceiver {
    private final static String TAG = "WearContext/Mobile/InferenceAlarmReceiver";
    private final static long timestamp = System.currentTimeMillis();
    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENSING_PERIOD = 1000 * 90;
    private final static int ALARM_INTERVAL = 1000 * 60 * 5; // Millisec * Second * Minute

    private final static String START_BATTERY_LOGGING = "android.intent.action.START_BATTERY_LOGGING";
    private final static String STOP_BATTERY_LOGGING = "android.intent.action.STOP_BATTERY_LOGGING";

    private final static InferenceType mType = InferenceType.WearPhoneAcc;
    private final static boolean logBattery = false;
    private final static boolean featureCalc = true;
    private final static boolean classifyCalc = true;

    private SensorManager mSensorManager;
    private TransportationModeListener mListener;
    private LocationManager mLocationManager;
    private Vibrator mVibrator;

    private static int numThreads;
    private static int resCount = 0;

    private static final Object lock = new Object();
    public static final Object locLock = new Object();

    private double gps_accuracy = 0.0, gps_speed = 0.0;
    private static double wear_var = 0.0, wear_fft9 = 0.0, wear_fft10 = 0.0;

    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock");
        wl.acquire();

        mContext = context;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        Log.i(TAG, "InferenceAlarmReceiver received, mType=" + mType + ", feature=" + featureCalc + ", classify=" + classifyCalc);
        // Toast.makeText(mContext, "InferenceAlarmReceiver received, mType=" + mType + ", feature=" + featureCalc + ", classify=" + classifyCalc, Toast.LENGTH_LONG).show();
        mVibrator.vibrate(500);

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

        if (mType == InferenceType.WearPhoneAcc || mType == InferenceType.PhoneAccGPS) {
            // Start inference using only acc for 1 minute (10s for test)
            mSensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
            Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);

            // Register the listener
            if (mSensorManager != null) {
                if (accelerometerSensor != null) {
                    mListener = new TransportationModeListener(mType);

                    mSensorManager.registerListener(mListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);

                    if (mType == InferenceType.PhoneAccGPS) {
                        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                        // location update
                        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mListener);
                        if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mListener);
                    }

                    // Start battery logging
                    Intent startBatteryLogging = new Intent();
                    startBatteryLogging.setAction(START_BATTERY_LOGGING);
                    startBatteryLogging.putExtra("NAME", mType == InferenceType.PhoneAccGPS ? "AccGPS" : "Acc");
                    context.sendBroadcast(startBatteryLogging);


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

                    if (mType == InferenceType.PhoneAccGPS) {
                        mLocationManager.removeUpdates(mListener);
                    }

                    // Stop battery logging
                    Intent stopBatteryLogging = new Intent();
                    stopBatteryLogging.setAction(STOP_BATTERY_LOGGING);
                    mContext.sendBroadcast(stopBatteryLogging);

                    Log.i(TAG, "InferenceAlarmReceiver execution finished.");
                    // Toast.makeText(mContext, "InferenceAlarmReceiver execution finished.", Toast.LENGTH_LONG).show();
                    cancelAlarm(mContext);
                    mVibrator.vibrate(500);
                }
            }, SENSING_PERIOD);
        }

        wl.release();

    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, InferenceAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000 * 10, ALARM_INTERVAL, pi);
        Log.i(TAG, "Alarm set.");
        Toast.makeText(context, "Alarm set.", Toast.LENGTH_LONG).show();
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, InferenceAlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
        Log.i(TAG, "Alarm cancelled.");
        Toast.makeText(context, "Alarm cancelled.", Toast.LENGTH_LONG).show();
    }

    private class TransportationModeListener implements SensorEventListener, LocationListener {
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
                    if (count >= 200) {
                        Log.d(TAG, "Wha?? " + System.currentTimeMillis() + " is the time now and we started samples at " + start + " and there are " + count
                                + " samples");
                        return;
                    }
                }

                data[count] = totalForce;
                count++;
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onLocationChanged(Location location) {
            Log.i(TAG, location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getProvider());
            synchronized (locLock) {
                gps_accuracy = location.getAccuracy();
                gps_speed = location.getSpeed();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

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
                    String activity = "null";
                    int n = mData.length;

                    if (mType == InferenceType.WearPhoneAcc) {
                        if (featureCalc) {
                            // phone-side feature: var, rms, and range
                            double phone_sum = 0.0;
                            double phone_mean = 0.0;
                            double phone_var = 0.0;
                            double phone_range = 0.0;
                            double phone_rms = 0.0;
                            double phone_min = Double.MAX_VALUE;
                            double phone_max = Double.MIN_VALUE;

                            wear_var = Math.random() * 10.0;
                            wear_fft9 = Math.random() * 10.0;
                            wear_fft10 = Math.random() * 10.0;

                            // Get mean, var, rms, range
                            for (int i = 0; i < n; i++) {
                                double temp = mData[i];
                                phone_sum += temp;
                                phone_min = Math.min(phone_min, temp);
                                phone_max = Math.max(phone_max, temp);
                                phone_rms += Math.pow(temp, 2.0);
                            }
                            phone_mean = phone_sum / n;
                            phone_range = Math.abs(phone_max - phone_min);
                            phone_rms = Math.sqrt(phone_rms / n);

                            phone_sum = 0.0;
                            for (int i = 0; i < n; i++){
                                phone_sum += Math.pow((mData[i] - phone_mean), 2.0);
                            }
                            phone_var = phone_sum / n;

                            if (classifyCalc) {
                                // Decision tree (wear + phone acc) from sklearn, max = 5
                                if ( phone_rms <= 10.9690818787 ) {
                                    if ( phone_range <= 1.38361096382 ) {
                                        if ( phone_var <= 0.00403899978846 ) {
                                            if ( phone_rms <= 9.82114315033 ) {
                                                activity = "still";
                                            } else {
                                                if ( phone_rms <= 9.84713363647 ) {
                                                    activity = "walking";
                                                } else {
                                                    activity = "still";
                                                }
                                            }
                                        } else {
                                            if ( wear_var <= 0.0506739988923 ) {
                                                activity = "transport";
                                            } else {
                                                if ( phone_rms <= 9.8354434967 ) {
                                                    activity = "still";
                                                } else {
                                                    activity = "walking";
                                                }
                                            }
                                        }
                                    } else {
                                        if ( wear_var <= 4.9740562439 ) {
                                            if ( phone_range <= 3.8014960289 ) {
                                                if ( phone_var <= 0.672578454018 ) {
                                                    activity = "transport";
                                                } else {
                                                    activity = "still";
                                                }
                                            } else {
                                                activity = "transport";
                                            }
                                        } else {
                                            if ( phone_var <= 1.50709748268 ) {
                                                if ( wear_fft9 <= 0.5804874897 ) {
                                                    activity = "still";
                                                } else {
                                                    activity = "transport";
                                                }
                                            } else {
                                                if ( wear_var <= 10.0692768097 ) {
                                                    activity = "transport";
                                                } else {
                                                    activity = "walking";
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if ( phone_range <= 24.326084137 ) {
                                        if ( wear_var <= 1.58773052692 ) {
                                            if ( phone_range <= 9.62026977539 ) {
                                                if ( wear_fft10 <= 1.3209810257 ) {
                                                    activity = "walking";
                                                } else {
                                                    activity = "transport";
                                                }
                                            } else {
                                                if ( wear_fft9 <= 0.00103499996476 ) {
                                                    activity = "still";
                                                } else {
                                                    activity = "walking";
                                                }
                                            }
                                        } else {
                                            if ( phone_rms <= 11.1141462326 ) {
                                                if ( phone_range <= 16.6261882782 ) {
                                                    activity = "walking";
                                                } else {
                                                    activity = "transport";
                                                }
                                            } else {
                                                activity = "walking";
                                            }
                                        }
                                    } else {
                                        if ( phone_rms <= 12.1419811249 ) {
                                            if ( phone_var <= 20.1230373383 ) {
                                                if ( wear_fft10 <= 4.23131084442 ) {
                                                    activity = "transport";
                                                } else {
                                                    activity = "walking";
                                                }
                                            } else {
                                                if ( phone_range <= 26.5224533081 ) {
                                                    activity = "walking";
                                                } else {
                                                    activity = "still";
                                                }
                                            }
                                        } else {
                                            if ( wear_var <= 10.1940841675 ) {
                                                if ( wear_var <= 7.82402706146 ) {
                                                    activity = "still";
                                                } else {
                                                    activity = "transport";
                                                }
                                            } else {
                                                if ( phone_var <= 30.2964420319 ) {
                                                    activity = "walking";
                                                } else {
                                                    activity = "still";
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (mType == InferenceType.PhoneAccGPS) {
                        if (featureCalc) {
                            // Features: var, energy, fft(3), and fft(5)
                            double phone_sum = 0.0;
                            double phone_mean = 0.0;
                            double phone_var = 0.0;
                            double phone_energy = 0.0;
                            double phone_fft3 = 0.0;
                            double phone_fft8 = 0.0;

                            // Get mean, var, rms, range
                            for (int i = 0; i < n; i++) {
                                double temp = mData[i];
                                phone_sum += temp;
                                phone_energy += temp * temp;
                            }
                            phone_mean = phone_sum / n;
                            phone_energy = Math.sqrt(phone_energy);

                            phone_sum = 0.0;
                            for (int i = 0; i < n; i++){
                                phone_sum += Math.pow((mData[i] - phone_mean), 2.0);
                            }
                            phone_var = phone_sum / n;

                            // Get fft
                            phone_fft3 = goertzel(mData, 3., n);
                            phone_fft8 = goertzel(mData, 8., n);

                            if (classifyCalc) {
                                synchronized (locLock) {
                                    // Decision tree (phone acc + gps) from sklearn, max = 5
                                    if ( gps_accuracy <= 19.5 ) {
                                        if ( phone_fft8 <= 13.6914787292 ) {
                                            if ( gps_speed <= 0.625 ) {
                                                if ( phone_fft8 <= 0.0313805006444 ) {
                                                    if ( phone_var <= 0.00288949999958 ) {
                                                        activity = "still";
                                                    } else {
                                                        activity = "transport";
                                                    }
                                                } else {
                                                    if ( gps_accuracy <= 8.5 ) {
                                                        activity = "transport";
                                                    } else {
                                                        activity = "walking";
                                                    }
                                                }
                                            } else {
                                                if ( phone_fft3 <= 121.535675049 ) {
                                                    activity = "transport";
                                                } else {
                                                    if ( gps_speed <= 3.55039048195 ) {
                                                        activity = "walking";
                                                    } else {
                                                        activity = "transport";
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( gps_speed <= 4.375 ) {
                                                if ( phone_fft3 <= 756.672546387 ) {
                                                    activity = "walking";
                                                } else {
                                                    if ( gps_speed <= 1.375 ) {
                                                        activity = "walking";
                                                    } else {
                                                        activity = "transport";
                                                    }
                                                }
                                            } else {
                                                if ( gps_speed <= 5.375 ) {
                                                    if ( gps_accuracy <= 4.5 ) {
                                                        activity = "still";
                                                    } else {
                                                        activity = "transport";
                                                    }
                                                } else {
                                                    activity = "transport";
                                                }
                                            }
                                        }
                                    } else {
                                        if (phone_energy <= 143.665679932) {
                                            if (gps_speed <= 0.125) {
                                                if (phone_energy <= 139.990478516) {
                                                    activity = "still";
                                                } else {
                                                    if (phone_var <= 0.013386500068) {
                                                        activity = "walking";
                                                    } else {
                                                        activity = "still";
                                                    }
                                                }
                                            } else {
                                                if (gps_accuracy <= 39.5) {
                                                    activity = "transport";
                                                } else {
                                                    if (gps_accuracy <= 143.5) {
                                                        activity = "still";
                                                    } else {
                                                        activity = "walking";
                                                    }
                                                }
                                            }
                                        } else {
                                            if (phone_fft3 <= 61.3018341064) {
                                                if (gps_speed <= 9.0) {
                                                    if (gps_accuracy <= 41.6504974365) {
                                                        activity = "walking";
                                                    } else {
                                                        activity = "still";
                                                    }
                                                } else {
                                                    activity = "transport";
                                                }
                                            } else {
                                                activity = "walking";

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    long toc = System.nanoTime();
                    Log.d(TAG, String.format("res=%s, time=%d ns", activity, (toc - tic)));
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

    /**
     * Accept wearable features from BLE
     * @param _wear_var
     * @param _wear_fft9
     * @param _wear_fft10
     */
    public static void setWearFeature(double _wear_var, double _wear_fft9, double _wear_fft10) {
        synchronized (lock) {
            wear_var = _wear_var;
            wear_fft9 = _wear_fft9;
            wear_fft10 = _wear_fft10;
        }
    }
}
