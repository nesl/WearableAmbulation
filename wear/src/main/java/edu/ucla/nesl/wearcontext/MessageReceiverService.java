package edu.ucla.nesl.wearcontext;

import android.content.Intent;
import android.util.Log;
import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MessageReceiverService extends WearableListenerService {
    private static final String TAG = "Wear/MsgReceiverService";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Received message: " + messageEvent.getPath());

        if (messageEvent.getPath().equals(ClientPaths.START_MEASUREMENT)) {
            startService(new Intent(this, SensorService.class));
        }

        if (messageEvent.getPath().equals(ClientPaths.STOP_MEASUREMENT)) {
            stopService(new Intent(this, SensorService.class));
        }

        if (messageEvent.getPath().startsWith(ClientPaths.LOCATION_UPDATE)) {
            String[] loc = messageEvent.getPath().split("/");
            if (loc.length == 3) {
                synchronized (SensorService.locLock) {
                    SensorService.locSpeed = Double.parseDouble(loc[1]);
                    SensorService.locAccuracy = Double.parseDouble(loc[2]);
                    Log.d(TAG, "Update loc: speed=" + SensorService.locSpeed + ", acc=" + SensorService.locAccuracy);
                }
            }
        }
    }
}
