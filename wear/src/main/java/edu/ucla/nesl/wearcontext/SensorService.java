package edu.ucla.nesl.wearcontext;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import edu.ucla.nesl.wearcontext.shared.TimeString;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Vibrator;

public class SensorService extends Service{
    private static final String TAG = "WearContext/Wear/SensorService";

    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;

    private SensorManager mSensorManager;
    private TransportationModeListener mListener;
    private DeviceClient client;
    private PowerManager.WakeLock wl;
    private Vibrator v;
    private BufferedWriter outputAcc, outputBattery;
    private TimeString timeString = new TimeString();
    private boolean logToFile = false;
    private Context context = this;

    private static int numThreads;
    private static final Object lock = new Object();

    public static double locSpeed;
    public static double locAccuracy;
    public static final Object locLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();

        client = DeviceClient.getInstance(this);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Wear Context");
        builder.setContentText("Activity recognition");
        builder.setSmallIcon(R.drawable.ic_launcher);

        startForeground(1, builder.build());
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        startMeasurement();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMeasurement();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean handlerFlag = true;

    private Handler wakeupHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level / (float)scale;

                if (batteryPct > 0.2) {
                    outputBattery.append(String.valueOf(System.currentTimeMillis()) + "," + String.valueOf(batteryPct) + "\n");
                    outputBattery.flush();

                    Log.d(TAG, "Log battery level = " + batteryPct);
                }
                else {
                    outputBattery.flush();
                    outputBattery.close();
                    outputBattery = null;
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
            if (handlerFlag) {
                sendEmptyMessageDelayed(0, 1000 * 60);
            }
        }
    };

    protected void startMeasurement() {
        Log.d(TAG, "start measurement in wear: SensorService");

        client.sendSensorData(-1, 1, 111, new float[]{1.0f});
        v.vibrate(1000);

        String prefix = "/storage/sdcard0/sensor_data/battery_" + timeString.currentTimeForFile();

        try {
            outputBattery = new BufferedWriter(new FileWriter(prefix + ".txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (logToFile) {
            try {
                outputAcc = new BufferedWriter(new FileWriter(prefix + ".wear.acc"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wl.acquire();

//        // Handler to log battery info
//        handlerFlag = true;
//        wakeupHandler.sendEmptyMessage(0);

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
        // Register the listener
        if (mSensorManager != null) {
            if (accelerometerSensor != null) {
                mListener = new TransportationModeListener();
                mSensorManager.registerListener(mListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.w(TAG, "No Accelerometer found");
            }
        }
    }

    private void stopMeasurement() {
        client.sendSensorData(-2, 2, 2, new float[]{2.0f});
        v.vibrate(200);

//        // Stop battery log
//        handlerFlag = false;

        mSensorManager.unregisterListener(mListener);
        mSensorManager = null;

        if (outputBattery != null) {
            try {
                outputBattery.flush();
                outputBattery.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (logToFile) {
            try {
                outputAcc.flush();
                outputAcc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        wl.release();
    }

    private class TransportationModeListener implements SensorEventListener {
        int count = 0;
        Thread thread;
        int resCount = 0;
        long start = 0;
        double[] data = new double[100];

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

                    // Log battery level every minute
                    resCount++;
                    if (resCount >= 60) {
                        client.sendSensorData(-8, 8, 888, new float[]{8.0f});

                        try {
                            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                            Intent batteryStatus = context.registerReceiver(null, ifilter);
                            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            float batteryPct = level / (float)scale;

                            if (batteryPct > 0.2) {
                                outputBattery.append(String.valueOf(System.currentTimeMillis()) + "," + String.valueOf(batteryPct) + "\n");
                                outputBattery.flush();

                                Log.d(TAG, "Battery level = " + batteryPct);
                            }
                            else {
                                outputBattery.flush();
                                outputBattery.close();
                                outputBattery = null;
                            }


                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // Reset counter
                        resCount = 0;
                    }

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
                    int n = mData.length;
                    double sum = 0.0;
                    double mean = 0.0;
                    double var = 0.0;
                    double speed = 0.0;
                    double acc = 0.0;

                    // Get last known gps speed and accuracy
                    synchronized (SensorService.locLock) {
                        speed = locSpeed;
                        acc = locAccuracy;
                    }

                    // Features: mean, var, and fft(5)
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

                    double accFft5 = goertzel(mData, 5., n);

//                    // Decision tree (acc only), 11 nodes, 78.17% accuracy
//                    String activity = "null";
//                    if (var < 2.01) {
//                        if (var < 0.01) {
//                            if (mean < 9.88) {
//                                activity = "still";
//                            }
//                            else {
//                                if (mean < 9.99) {
//                                    activity = "transport";
//                                }
//                                else {
//                                    activity = "walking";
//                                }
//                            }
//                        }
//                        else {
//                            activity = "transport";
//                        }
//                    }
//                    else {
//                        if (accFft5 < 133.17) {
//                            activity = "transport";
//                        }
//                        else {
//                            activity = "walking";
//                        }
//                    }

                    // Decision tree (acc + gps), 29 nodes, 88.81% accuracy
                    String activity = "null";
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
            for (int i = 0; i < data.length; i++)
            {
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
