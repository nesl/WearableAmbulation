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
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Arrays;

public class SensorReceiverService extends WearableListenerService {
    private static final String TAG = "WearContext/Mobile/SensorReceiverService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Started");
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);

        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);

        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged()");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.equals(ClientPaths.TEST)) {
                    unpackSensorData(
                        Integer.parseInt(uri.getLastPathSegment()),
                        DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                }
            }
        }
    }

    private void unpackSensorData(int sensorType, DataMap dataMap) {
        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        byte[] values = dataMap.getByteArray(DataMapKeys.VALUES);
        Log.d(TAG, "Received sensor data, ts=" + timestamp + ", value=" + Arrays.toString(values));
    }
}
