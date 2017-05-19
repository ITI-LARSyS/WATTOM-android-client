package com.example.filipe.socketcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;


public class MainActivity extends AppCompatActivity implements  MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final int INT = 50;
    private MotionWearListenerService _listenerService;
    private final String TAG = "main";
    private PlugMotionHandler _handler;
    GoogleApiClient _client;

    //graph shit
    private LinearLayout _x_bar;
    private LinearLayout _y_bar;
    private LinearLayout _z_bar;

    private int _max_size = 0;

    // arrays to store acc and plug data
    private double[][] _acc_buffer;
    private double[][] _plug_buffer;
    private double[][] _acc_data;
    private double[][] _plug_data;
    // indexes, remove with buffer in the future
    private int _acc_buffer_index  = 0;
    private int _plug_buffer_index = 0;

    //communication stuff
    private PlugMotionBroadcastReceiver _receiver;
    private IntentFilter _filter;
    private boolean _started=false;

    //correlation stuff
    private final PearsonsCorrelation pc = new PearsonsCorrelation();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _x_bar = (LinearLayout) findViewById(R.id.x_bar);
        _y_bar = (LinearLayout) findViewById(R.id.y_bar);
        _z_bar = (LinearLayout) findViewById(R.id.z_bar);

        _max_size = findViewById(R.id.dummy_linear).getLayoutParams().height;

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();

        Wearable.MessageApi.addListener(_client, this);

        _handler = new PlugMotionHandler(getApplicationContext());
        _handler.start();

        _acc_buffer  = new double[2][INT];
        _plug_buffer = new double[2][INT];
        _acc_data    = new double[2][INT];
        _plug_data   = new double[2][INT];

        _receiver = new PlugMotionBroadcastReceiver();
        _filter = new IntentFilter(PlugMotionHandler.DATA_KEY);
        registerReceiver(_receiver,_filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        unregisterReceiver(_receiver);
        if(_handler!=null)
            _handler.stopSimulation();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        _started = true;
        String merda = messageEvent.getPath();
        String data = merda.replace("WearAccService--", "");
        String[] tokens = data.split("#");
        try {
            double x = Double.parseDouble(tokens[0]);
           // double y = Double.parseDouble(tokens[1]);
            double z = Double.parseDouble(tokens[1]);

            Log.i("receiver", x+","+z);
            adjustSize(Math.abs(x),0,Math.abs(z));
            if(_acc_buffer_index< INT){
                _acc_buffer[0][_acc_buffer_index]=x;
                _acc_buffer[1][_acc_buffer_index]=z;
                _acc_buffer_index++;
            }else {
                _acc_buffer_index = 0;
                _acc_data = _acc_buffer;
                Log.e(TAG,"---> Array cheio acc");
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "format exception data " + data);
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

    private void adjustSize(double x, double y,double z) {
// Gets the layout params that will allow you to resize the layout

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                (int) Math.round(x * 15f),
                0.25f
        );
        _x_bar.setLayoutParams(params);

        params = new LinearLayout.LayoutParams(
                0,
                (int) Math.round(y * 15f),
                0.25f
        );

        _y_bar.setLayoutParams(params);

        params = new LinearLayout.LayoutParams(
                0,
                (int) Math.round(z * 15f),
                0.25f
        );

        _z_bar.setLayoutParams(params);


        //  Log.i(TAG,Math.round(x*20000)+"|"+Math.round(y*20000));
    }

    //correlation stuff
    private class PlugMotionBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if(_started) {
                if (_plug_buffer_index < INT) {
                    _plug_buffer[0][_plug_buffer_index] = intent.getDoubleExtra("x", -1);
                    _plug_buffer[1][_plug_buffer_index] = intent.getDoubleExtra("y", -1);
                    _plug_buffer_index++;
                } else {
                    _plug_buffer_index = 0;
                    _plug_data = _plug_buffer;
                 //   Log.e(TAG, "---> Array cheio plug");
                  //  Log.i(TAG, "--- calculating correlation");
                  //  Log.i(TAG, "X corr:"+(pc.correlation(_plug_data[0],_acc_data[0]))+" | Y corr:"+pc.correlation(_plug_data[1],_acc_data[1]));
                }
            }
        }
    }

}