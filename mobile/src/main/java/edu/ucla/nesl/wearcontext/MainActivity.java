package edu.ucla.nesl.wearcontext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.PowerManager;
import android.os.PowerManager.*;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.widget.Toast;
import edu.ucla.nesl.wearcontext.shared.TimeString;

import java.util.*;
import java.io.*;


public class MainActivity extends ActionBarActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "WearContext/Mobile/MainActivity";

    private RemoteSensorManager remoteSensorManager;

    private TextViewBuf textStorage;

    private TextViewBuf textActivity;
    private TextViewBuf textGps;
    private TextViewBuf textAcc;
    private TextViewBuf textGyro;
    private TextViewBuf textStep;
    private TextViewBuf textBaro;


    private TextViewBuf textAccWear;
    private TextViewBuf textGyroWear;
    private TextViewBuf textStepWear;
    private TextViewBuf textHeartRateWear;

    private BufferedWriter loggerGps;
    private BufferedWriter loggerActivity;

    private BufferedWriter loggerAcc;
    private BufferedWriter loggerGyro;
    private BufferedWriter loggerStep;
    private BufferedWriter loggerBaro;


    private TimeString timeString = new TimeString();

    private int gpsGCnt = 0;
    private int gpsNCnt = 0;

    private SensorManager sensorManager;
    private LocationManager locationManager;

    private WakeLock wl;

    private BroadcastReceiver updateUIReciver;
    private IntentFilter filter;

    private SensorEventListener mSensorListener;
    private LocationListener mLocationListener;
    private Activity mThis;

    private int mCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mThis = this;

        long elapsedRealTime = SystemClock.elapsedRealtime();
        long current = System.currentTimeMillis();
        Log.i(TAG, "elapsedRealTime=" + elapsedRealTime + ", current time=" + current + ", diff=" + (current - elapsedRealTime));

//        Intent intent = new Intent(MainActivity.this, SensorReceiverService.class);
//        startService(intent);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        remoteSensorManager = RemoteSensorManager.getInstance(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(toolbar);

        // log file
        String pathRoot = Environment.getExternalStorageDirectory() + "/wear_data/new/weardata_" + timeString.currentTimeForFile();
        try {
            loggerGps  = new BufferedWriter(new FileWriter(pathRoot + ".phone.gps"));
            loggerActivity  = new BufferedWriter(new FileWriter(pathRoot + ".phone.activity"));

            loggerAcc  = new BufferedWriter(new FileWriter(pathRoot + ".phone.acc"));
            loggerGyro = new BufferedWriter(new FileWriter(pathRoot + ".phone.gyro"));
            loggerStep  = new BufferedWriter(new FileWriter(pathRoot + ".phone.step"));
            loggerBaro = new BufferedWriter(new FileWriter(pathRoot + ".phone.baro"));

//            loggerAccWear  = new BufferedWriter(new FileWriter(pathRoot + ".wear.acc"));
//            loggerGyroWear = new BufferedWriter(new FileWriter(pathRoot + ".wear.gyro"));
//            loggerStepWear  = new BufferedWriter(new FileWriter(pathRoot + ".wear.step"));
//            loggerHeartRateWear = new BufferedWriter(new FileWriter(pathRoot + ".wear.heartrate"));

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Register phone sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wl.acquire();

        filter = new IntentFilter();
        filter.addAction("nesl.wear.sensordata");

        updateUIReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int sensorType = intent.getIntExtra("t", 0);
                if (sensorType == 0) {
                    Toast.makeText(mThis, "empty sensor data received ", Toast.LENGTH_SHORT).show();
                }
                if (sensorType == -1) {
                    Toast.makeText(mThis, "wear data collection started", Toast.LENGTH_SHORT).show();
                    textAccWear.setStr("Wear data collection started");
                }
                else if (sensorType == -2) {
                    Toast.makeText(mThis, "wear data collection stopped", Toast.LENGTH_SHORT).show();
                    textAccWear.setStr("Wear data collection stopped");
                }
//                else if (sensorType == -5) {
//                    long timestamp = intent.getLongExtra("ts", 0);
//                    String content = intent.getStringExtra("d");
//                    updateSensorData(sensorType, content, timestamp);
//                    textActivity.setStr("Device activity: " + content);
//                }
                else if (sensorType == -8) {
                    mCount++;
                    textActivity.setStr("Wear activity count: " + mCount);
                }
            }
        };

        // UI
        LinearLayout la = (LinearLayout) findViewById(R.id.sensor_panel);
        // basic information
        TextViewBuf.createText(la, this, "File name: " + pathRoot + ".*");
        textStorage  = TextViewBuf.createText(la, this, "Storage: --");
        TextViewBuf.createText(la, this, "");

        // Google play service activity recognition
        textActivity      = TextViewBuf.createText(la, this, "Detected Activity: ");
        TextViewBuf.createText(la, this, "");

        // GPS from phone
        textGps      = TextViewBuf.createText(la, this, "GPS from gps: --  from network: --");
        TextViewBuf.createText(la, this, "");

        // sensor data from phone
        textAcc     = TextViewBuf.createText(la, this, "ACC x:------,y:------,z:------");
        // textAccHz    = TextViewBuf.createText(la, this, "ACC freq: -- Hz");
        textGyro    = TextViewBuf.createText(la, this, "GYRO x:------,y:------,z:------");
        // textGyroHz   = TextViewBuf.createText(la, this, "GYRO freq: -- Hz");
        textStep     = TextViewBuf.createText(la, this, "StepCount: x:--");
        // textStepHz    = TextViewBuf.createText(la, this, "StepCount freq: --");
        textBaro     = TextViewBuf.createText(la, this, "BARO value: --");
        // textBaroHz   = TextViewBuf.createText(la, this, "BARO freq: -- Hz");
        TextViewBuf.createText(la, this, "");

        // sensor data from wearable device
        textAccWear     = TextViewBuf.createText(la, this, "ACC_WEAR x:------,y:------,z:------");
        // textAccWearHz    = TextViewBuf.createText(la, this, "ACC_W freq: -- Hz");
        textGyroWear    = TextViewBuf.createText(la, this, "GYRO_WEAR x:------,y:------,z:------");
        // textGyroWearHz   = TextViewBuf.createText(la, this, "GYRO_W freq: -- Hz");
        textStepWear     = TextViewBuf.createText(la, this, "StepCount_Wear x:--");
        // textStepWearHz    = TextViewBuf.createText(la, this, "StepCount_W freq: --");
        textHeartRateWear     = TextViewBuf.createText(la, this, "HeartRate_Wear --");
        // textHeartRateWearHz   = TextViewBuf.createText(la, this, "HeartRate_W freq: -- Hz");
        TextViewBuf.createText(la, this, "");

        frameUpdateHandler.sendEmptyMessage(0);
        storageCheckHandler.sendEmptyMessage(0);

        mSensorListener = this;
        mLocationListener = this;

        Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start data collection on wear
                remoteSensorManager.startMeasurement();

//                // Start data collection on phone
//                Sensor barometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
//                sensorManager.registerListener(mSensorListener, barometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
//
//                Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//                sensorManager.registerListener(mSensorListener, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
//
//                Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//                sensorManager.registerListener(mSensorListener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
//
//                Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
//                sensorManager.registerListener(mSensorListener, stepCounter, SensorManager.SENSOR_DELAY_FASTEST);
//
//                // location update
//                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
//                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
//                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
//                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            }
        });

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop data collection on wear
                remoteSensorManager.stopMeasurement();

//                // Stop data collection on phone
//                sensorManager.unregisterListener(mSensorListener);
//                locationManager.removeUpdates(mLocationListener);
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updateUIReciver,filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the boardcast receiver
        unregisterReceiver(updateUIReciver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called - closing all file logs...");
        //sensorManager.unregisterListener(this);
        try {
            loggerBaro.close();
            loggerAcc.close();
            loggerGyro.close();
            loggerStep.close();
            loggerGps.close();
            loggerActivity.close();

//            loggerHeartRateWear.close();
//            loggerAccWear.close();
//            loggerGyroWear.close();
//            loggerStepWear.close();

            // Release the wakelock
            wl.release();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Handler frameUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextViewBuf.update();
            sendEmptyMessageDelayed(0, 1000);
        }
    };

    private Handler storageCheckHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            stat.restat(Environment.getDataDirectory().getPath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            textStorage.setStr("Remaining storage: " + String.format("%.2f GB", (double)(bytesAvailable >> 20) / (1 << 10)));
            sendEmptyMessageDelayed(0, 10 * 60 * 1000L);
        }
    };

    @Override
    public void onLocationChanged(Location location) {
        //Log.i("GPS", location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getProvider());
        int gpsType = (location.getProvider().equals(LocationManager.GPS_PROVIDER)) ? 0 : 1;
        if (gpsType == 0)
            gpsGCnt++;
        else
            gpsNCnt++;
        try {
            loggerGps.write(location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getAccuracy() + "," + location.getSpeed() + "," + gpsType);
            loggerGps.newLine();
            loggerGps.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        textGps.setStr("GPS from gps:" + gpsGCnt + "  from network:" + gpsNCnt);
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

    private void updateSensorData(int sensorType, String content, long timestamp) {
        try {
            switch (sensorType) {
                case Sensor.TYPE_PRESSURE: {
                    // baroCnt++;
                    textBaro.setStr("BARO: " + content);
                    // textBaroHz.setStr("BARO freq: " + freqStr(timestamp, baroCnt));
                    loggerBaro.write(timestamp + "," + content);
                    loggerBaro.newLine();
                    loggerBaro.flush();
                }
                break;
                case Sensor.TYPE_ACCELEROMETER: {
                    // accCnt++;
                    textAcc.setStr("ACC " + content);
                    // textAccHz.setStr("ACC freq: " + freqStr(timestamp, accCnt));
                    loggerAcc.write(timestamp + "," + content);
                    loggerAcc.newLine();
                    loggerAcc.flush();
                }
                break;
                case Sensor.TYPE_GYROSCOPE: {
                    // gyroCnt++;
                    textGyro.setStr("GYRO " + content);
                    // textGyroHz.setStr("GYRO freq: " + freqStr(timestamp, gyroCnt));
                    loggerGyro.write(timestamp + "," + content);
                    loggerGyro.newLine();
                    loggerGyro.flush();
                }
                break;
                case Sensor.TYPE_STEP_COUNTER: {
                    // stepCnt++;
                    textStep.setStr("StepCounter: " + content);
                    // textStepHz.setStr("StepCounter freq: " + freqStr(timestamp, stepCnt));
                    loggerStep.write(timestamp + "," + content);
                    loggerStep.newLine();
                    loggerStep.flush();
                }
                break;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // long elapsed = SystemClock.elapsedRealtimeNanos();
        // Log.i(TAG, "event timestamp=" + event.timestamp + ", elapsed=" + elapsed + ", diff=" + (event.timestamp - elapsed));
        // updateSensorData(event.sensor.getType(), Arrays.toString(event.values), event.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
