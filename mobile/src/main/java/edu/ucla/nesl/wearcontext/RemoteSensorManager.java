package edu.ucla.nesl.wearcontext;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.common.api.GoogleApiClient.*;
import android.os.Bundle;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RemoteSensorManager {
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
        this.context = _context;

        this.googleApiClient = new Builder(context)
                .addConnectionCallbacks(new ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                .addApi(Wearable.API)
                .build();


        this.executorService = Executors.newCachedThreadPool();
    }

    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);

        return result.isSuccess();
    }

    public void startMeasurement() {
        handlerFlag = true;
        wakeupHandler.sendEmptyMessage(0);
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
