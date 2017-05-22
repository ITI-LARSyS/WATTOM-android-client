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

    public static final int WINDOW_SIZE = 40;
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
    private double[][] _acc_data;
    private double[][] _plug_data;
    // indexes, remove with buffer in the future
    private int _acc_buffer_index  = 0;
    private int _plug_buffer_index = 0;

    //communication stuff
    private PlugMotionBroadcastReceiver _receiver;
    private IntentFilter _filter;
    private boolean _started=false;

    //average stuff for the data from the watch
    private double _xAverage;
    private double _yAverage;
    private long   _lastAverage;
    private long _simulationSpeed;

    //correlation stuff
    private final PearsonsCorrelation pc = new PearsonsCorrelation();
    private boolean _correlationRunning = false;
    private long _correlationInterval   = 500;
    private CorrelationHandler _corrHandler = new CorrelationHandler();

    private double _last_acc_x = 0;
    private double _last_acc_y = 0;

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

        _handler = new PlugMotionHandler(getApplicationContext(),40);
        _handler.start();

        _simulationSpeed = 40;

        _acc_data    = new double[2][WINDOW_SIZE];
        _plug_data   = new double[2][WINDOW_SIZE];

        _receiver   = new PlugMotionBroadcastReceiver();
        _filter     = new IntentFilter(PlugMotionHandler.DATA_KEY);
       registerReceiver(_receiver,_filter);

        _corrHandler.start();
        _correlationRunning = true;
    }

    private void push(int index, double[][] data,double x, double y){
        if(index < WINDOW_SIZE){
            data[0][index]=x;
            data[1][index]=y;
        }else{

           /* for(int i=0;i<WINDOW_SIZE;i++)
                     Log.i("DEBUG",_plug_data[0][i]+","+_plug_data[1][i]+","+_acc_data[0][i]+","+_acc_data[1][i]);

            _correlationRunning = false;
            unregisterReceiver(_receiver);*/

            int i=0;
            for(i=1; i< WINDOW_SIZE; i++){
                data[0][i-1]=data[0][i];
                data[1][i-1]=data[1][i];
            }
            data[0][i-1]=x;
            data[1][i-1]=y;
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        unregisterReceiver(_receiver);
        _correlationRunning = false;
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

            if(System.currentTimeMillis()-_lastAverage>_simulationSpeed) {
                double x = Double.parseDouble(tokens[0]);
                double z = Double.parseDouble(tokens[1])*-1;
                   // _acc_buffer_index = _acc_buffer_index == (WINDOW_SIZE - 1) ? WINDOW_SIZE : _acc_buffer_index+1;
                  //  push(_acc_buffer_index, _acc_data, x, z);
                    //_acc_insert = false;
                   // _plug_insert = true;
              //  }
                _last_acc_x = x;
                _last_acc_y = z*10;
                _lastAverage = System.currentTimeMillis();
            }
          //  _xAverage = x;
          //  _yAverage = z;
           // adjustSize(Math.abs(x),0,Math.abs(z));
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
                (int) Math.round(x * 5f),
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
                (int) Math.round(z * 100f),
                0.25f
        );

        _z_bar.setLayoutParams(params);

    }

    //correlation stuff
    private class PlugMotionBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if(_started) {

                // if(_plug_insert) {
                    _plug_buffer_index = _plug_buffer_index == (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_buffer_index + 1;
                    push(_plug_buffer_index, _plug_data, intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                    push(_plug_buffer_index,_acc_data,_last_acc_x,_last_acc_y);
                    //Log.i("TAG", intent.getDoubleExtra("y", -1)+","+_last_acc_y);
               //     _acc_insert = true;
                //    _plug_insert = false;
                //}
               /* if (_plug_buffer_index < WINDOW_SIZE) {
                    _plug_buffer[0][_plug_buffer_index] = intent.getDoubleExtra("x", -1);
                    _plug_buffer[1][_plug_buffer_index] = intent.getDoubleExtra("y", -1);
                    _plug_buffer_index++;
                } else {
                    _plug_buffer_index = 0;
                    _plug_data = _plug_buffer;
                    //   Log.e(TAG, "---> Array cheio plug");
                    //  Log.i(TAG, "--- calculating correlation");
                    //  Log.i(TAG, "X corr:"+(pc.correlation(_plug_data[0],_acc_data[0]))+" | Y corr:"+pc.correlation(_plug_data[1],_acc_data[1]));
                }*/
            }
        }
    }

    private class CorrelationHandler extends Thread{

        double x_corr;
        double y_corr;
        @Override
        public void run(){

            while(_correlationRunning){
                try {
                    sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                x_corr = pc.correlation(_plug_data[0],_acc_data[0]);
                y_corr = pc.correlation(_plug_data[1],_acc_data[1]);
               // for(int i=0;i<WINDOW_SIZE;i++){
               //     Log.i("DEBUG",_plug_data[0][i]+","+_plug_data[1][i]+","+_acc_data[0][i]+","+_acc_data[1][i]);

               // }
                if(x_corr>0.8&&y_corr>0.8   )
                    Log.wtf(TAG,"corr Its a match");


                Log.i(TAG, "X corr:"+x_corr+" | Y corr:"+y_corr);
            }
        }
    }

}