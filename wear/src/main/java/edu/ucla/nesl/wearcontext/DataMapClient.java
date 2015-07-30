package edu.ucla.nesl.wearcontext;

import android.content.Context;
import android.util.Log;

import edu.ucla.nesl.wearcontext.shared.ClientPaths;
import edu.ucla.nesl.wearcontext.shared.DataMapKeys;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DataMapClient {
    private static final String TAG = "Wear/InfDataMapClient";
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;

    public static DataMapClient instance;

    public static DataMapClient getInstance(Context context) {
        if (instance == null) {
            instance = new DataMapClient(context.getApplicationContext());
        }

        return instance;
    }

    private GoogleApiClient googleApiClient;
    private ExecutorService executorService;

    private DataMapClient(Context context) {
        googleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();
        executorService = Executors.newCachedThreadPool();
    }

    public void sendSensorData(final long timestamp, final byte[] values) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                sendSensorDataInBackground(timestamp, values);
            }
        });
    }

    private void sendSensorDataInBackground(long timestamp, byte[] values) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(ClientPaths.TEST);
        dataMap.getDataMap().putLong(DataMapKeys.TIMESTAMP, timestamp);
        dataMap.getDataMap().putByteArray(DataMapKeys.VALUES, values);

        PutDataRequest putDataRequest = dataMap.asPutDataRequest();
        send(putDataRequest);
    }

    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
        return result.isSuccess();
    }

    private void send(PutDataRequest putDataRequest) {
        if (validateConnection()) {
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    Log.v(TAG, "Sending data message: " + dataItemResult.getStatus().isSuccess());
                }
            });
        }
    }
}
