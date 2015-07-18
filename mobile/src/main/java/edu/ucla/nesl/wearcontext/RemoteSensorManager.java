package edu.ucla.nesl.wearcontext;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import edu.ucla.nesl.wearcontext.shared.DataMapKeys;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.common.api.GoogleApiClient.*;
import android.os.Bundle;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RemoteSensorManager implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener   {
    private static final String TAG = "WearContext/Mobile/RemoteSensorManager";
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;

    private static RemoteSensorManager instance;

    private final Context context;
    private ExecutorService executorService;
    private boolean handlerFlag;

    private GoogleApiClient googleApiClient;

    public static synchronized RemoteSensorManager getInstance(Context context) {
        if (instance == null) {
            instance = new RemoteSensorManager(context.getApplicationContext());
        }

        return instance;
    }

    private RemoteSensorManager(Context _context) {
        context = _context;

        googleApiClient = new Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        executorService = Executors.newCachedThreadPool();
    }

    public void connect() {
        googleApiClient.connect();
    }

    public void disconnect() {
        Log.i(TAG, "RemoveSensorManager disconencted...");
        Wearable.DataApi.removeListener(googleApiClient, this);
        googleApiClient.disconnect();
    }

    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
        return result.isSuccess();
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
                    unpackSensorData(DataMapItem.fromDataItem(dataItem).getDataMap());
                }
            }
        }
    }

    private void unpackSensorData(DataMap dataMap) {
        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        byte[] values = dataMap.getByteArray(DataMapKeys.VALUES);
        Log.d(TAG, "Received sensor data, ts=" + timestamp + ", size=" + values.length);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
        Wearable.DataApi.addListener(googleApiClient, this);
        Log.d(TAG, "DataApi listener registered");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: " + connectionResult);
    }

    public void startMeasurement() {
        handlerFlag = true;
        // wakeupHandler.sendEmptyMessage(0);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                controlMeasurementInBackground(ClientPaths.START_MEASUREMENT);
            }
        });
    }

    public void stopMeasurement() {
        handlerFlag = false;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                controlMeasurementInBackground(ClientPaths.STOP_MEASUREMENT);
            }
        });
    }

    public void sendLocationUpdate(final Location loc) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                controlMeasurementInBackground(ClientPaths.LOCATION_UPDATE + "/" + loc.getSpeed() + "/" + loc.getAccuracy());
            }
        });
    }

    private void controlMeasurementInBackground(final String path) {
        if (validateConnection()) {
            List<Node> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await().getNodes();

            Log.d(TAG, "Sending to nodes: " + nodes.size());

            for (Node node : nodes) {
                Wearable.MessageApi.sendMessage(
                        googleApiClient, node.getId(), path, null
                ).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "controlMeasurementInBackground(" + path + "): " + sendMessageResult.getStatus().isSuccess());
                    }
                });
            }
        } else {
            Log.w(TAG, "No connection possible");
        }
    }

    private Handler wakeupHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    controlMeasurementInBackground(ClientPaths.BEACON);
                }
            });
            if (handlerFlag) {
                sendEmptyMessageDelayed(0, 10000);
            }
        }
    };
}
