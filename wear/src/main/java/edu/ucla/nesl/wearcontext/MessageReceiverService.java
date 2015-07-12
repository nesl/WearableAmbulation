package edu.ucla.nesl.wearcontext;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import edu.ucla.nesl.wearcontext.shared.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MessageReceiverService extends WearableListenerService {
    private static final String TAG = "WearContext/Wear/MessageReceiverService";

    private DeviceClient deviceClient;

    @Override
    public void onCreate() {
        super.onCreate();

        deviceClient = DeviceClient.getInstance(this);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.startsWith("/filter")) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    int filterById = dataMap.getInt(DataMapKeys.FILTER);
                    deviceClient.setSensorFilter(filterById);
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        // Log.d(TAG, "Received message: " + messageEvent.getPath());

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
                    // Log.d(TAG, "Update loc: speed=" + SensorService.locSpeed + ", acc=" + SensorService.locAccuracy);
                }
            }
        }
    }
}
