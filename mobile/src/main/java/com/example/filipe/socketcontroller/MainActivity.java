package com.example.filipe.socketcontroller;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends AppCompatActivity implements  MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private  MotionWearListenerService _listenerService;
    private final String TAG = "main";
    private PlugMotionHandler _handler;
    GoogleApiClient _client;

    private LinearLayout _x_bar;
    private LinearLayout _y_bar;
    private LinearLayout _z_bar;

    private int _max_size=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _x_bar = (LinearLayout)findViewById(R.id.x_bar);
        _y_bar = (LinearLayout)findViewById(R.id.y_bar);
        _z_bar = (LinearLayout)findViewById(R.id.z_bar);

        _max_size = findViewById(R.id.dummy_linear).getLayoutParams().height;

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();

        Wearable.MessageApi.addListener(_client, this);

        _handler = new PlugMotionHandler();
        _handler.start();

    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        _handler.stopSimulation();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String merda = messageEvent.getPath();
        String data = merda.replace("WearAccService--","");
        String[] tokens = data.split("#");
        try {
            double y = Double.parseDouble(tokens[0]);
            double z = Double.parseDouble(tokens[1]);

            Log.i(TAG, "y : " + y + "   z : " + z);
            adjustSize(Math.abs(y),Math.abs(z));
        }catch (NumberFormatException e){
            Log.e(TAG,"format exception data "+data);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Google API Client was connected");

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection to Google API client was failed");
    }

    private void adjustSize(double x,double y){
// Gets the layout params that will allow you to resize the layout

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                (int)Math.round(x*150f),
                0.25f
        );
        _x_bar.setLayoutParams(params);

        params = new LinearLayout.LayoutParams(
                0,
                (int)Math.round(y*150f),
                0.25f
        );

        _y_bar.setLayoutParams(params);


      //  Log.i(TAG,Math.round(x*20000)+"|"+Math.round(y*20000));
    }
}
