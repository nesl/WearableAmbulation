package edu.ucla.nesl.wearcontext;


import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String TAG = "WearContext/Wear/MainActivity";

    private DeviceClient client;
    private Random random;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        client = DeviceClient.getInstance(this);
        random = new Random();

//        // TODO: Keep the Wear screen always on (for testing only!)
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onBeep(View view) {
        client.sendSensorData(0, 1, 555, new float[]{random.nextFloat()});
    }

}
