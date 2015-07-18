package edu.ucla.nesl.wearcontext;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.PowerManager;
import android.os.PowerManager.*;


public class MainActivity extends ActionBarActivity implements LocationListener {
    private static final String TAG = "WearContext/Mobile/MainActivity";

    private RemoteSensorManager remoteSensorManager;

    private int gpsGCnt = 0;
    private int gpsNCnt = 0;

    private LocationManager locationManager;

    private LocationListener mLocationListener;
    private WakeLock wl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(toolbar);

        // Wakelock and keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wl.acquire();

        mLocationListener = this;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        remoteSensorManager = RemoteSensorManager.getInstance(this);

        Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // location update
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            }
        });

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locationManager.removeUpdates(mLocationListener);
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");
        remoteSensorManager.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause() called");
        remoteSensorManager.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy() called");

        // Release the wakelock
        wl.release();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getProvider());
        int gpsType = (location.getProvider().equals(LocationManager.GPS_PROVIDER)) ? 0 : 1;
        if (gpsType == 0)
            gpsGCnt++;
        else
            gpsNCnt++;
        Log.i(TAG, "GPS from gps:" + gpsGCnt + "  from network:" + gpsNCnt);

        // Notify the watch
        remoteSensorManager.sendLocationUpdate(location);
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
}
