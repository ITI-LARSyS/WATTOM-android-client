package com.example.filipe.socketcontroller.motion;

import android.annotation.SuppressLint;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Filipe on 12/05/2017.
 */
public class MotionWearListenerService extends WearableListenerService {

    private final String TAG = "MWL";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.d(TAG,"messagem recebida");
        Toast.makeText(getApplicationContext(),"Message received",Toast.LENGTH_LONG).show();
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        Log.d(TAG,"messagem recebida");
        Toast.makeText(getApplicationContext(),"Message received",Toast.LENGTH_LONG).show();
    }
}
