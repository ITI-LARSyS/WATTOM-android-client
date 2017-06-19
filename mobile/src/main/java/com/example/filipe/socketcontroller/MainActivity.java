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
import android.widget.RadioGroup;
import android.widget.Switch;

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

    private double[][] _plug_target_1_data;
    private int _plug_target_1_index = 0;

    private double[][] _plug_target_2_data;
    private int _plug_target_2_index = 0;

    private double[][] _plug_target_3_data;
    private int _plug_target_3_index = 0;

    // indexes, remove with buffer in the future
    private int _acc_buffer_index  = 0;

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
    private long _correlationInterval   = 120;
    private CorrelationHandler _corrHandler = new CorrelationHandler();
    private double _last_acc_x = 0;
    private double _last_acc_y = 0;

    //plugs url for notifying good correlation
    private final static String SELECTED_URL = "http://192.168.8.113:3000/plug/2/selected/";

    //direction selecter
    private Switch _direction_selector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _x_bar = (LinearLayout) findViewById(R.id.x_bar);
        _y_bar = (LinearLayout) findViewById(R.id.y_bar);
        _z_bar = (LinearLayout) findViewById(R.id.z_bar);

        _max_size = findViewById(R.id.dummy_linear).getLayoutParams().height;

        _direction_selector = (Switch)findViewById(R.id.direction_selector);

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();

        Wearable.MessageApi.addListener(_client, this);

        _handler = new PlugMotionHandler(getApplicationContext(),40,0);
        _handler.set_target(1);
        _handler.start();
        _receiver   = new PlugMotionBroadcastReceiver(0);
        _filter     = new IntentFilter(PlugMotionHandler.DATA_KEY+"0");
        registerReceiver(_receiver,_filter);

        PlugMotionHandler handler_2 = new PlugMotionHandler(getApplicationContext(),40,1);
        handler_2.set_target(1);
        handler_2.start();
        PlugMotionBroadcastReceiver receiver2   = new PlugMotionBroadcastReceiver(1);
        IntentFilter filter2     = new IntentFilter(PlugMotionHandler.DATA_KEY+"1");
        registerReceiver(receiver2,filter2);

/*
        PlugMotionHandler handler_3 = new PlugMotionHandler(getApplicationContext(),40,2);
        handler_3.set_target(1);
        handler_3.start();
        PlugMotionBroadcastReceiver receiver3   = new PlugMotionBroadcastReceiver(2);
        IntentFilter filter3     = new IntentFilter(PlugMotionHandler.DATA_KEY+"2");
        registerReceiver(receiver3,filter3);*/

        _simulationSpeed = 40;

        _acc_data    = new double[2][WINDOW_SIZE];
        _plug_target_1_data = new double[2][WINDOW_SIZE];
        _plug_target_2_data = new double[2][WINDOW_SIZE];
        _plug_target_3_data = new double[2][WINDOW_SIZE];

        _corrHandler.start();
        _correlationRunning = true;

        initOptionSelectors();

    }


    private void push(int index, double[][] data,double x, double y){
        if(index < WINDOW_SIZE){
            data[0][index]=x;
            data[1][index]=y;
        }else{

            int i=0;
            for(i=1; i< WINDOW_SIZE; i++){
                data[0][i-1]=data[0][i];
                data[1][i-1]=data[1][i];
            }
            data[0][i-1]=x;
            data[1][i-1]=y;
        }
    }

    private void initOptionSelectors(){
        RadioGroup targets = (RadioGroup)findViewById(R.id.target_option);
        targets.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i==R.id.main_target){
                    _handler.set_target(1);
                }else if(i==R.id.secondary_target){
                    _handler.set_target(2);
                }else if(i == R.id.tertiary_target){
                    _handler.set_target(3);
                }
            }
        });

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
                _last_acc_x = x;
                _last_acc_y = z*10;
                _lastAverage = System.currentTimeMillis();
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

  /*  private void adjustSize(double x, double y,double z) {
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

    }*/

    //correlation stuff
    private class PlugMotionBroadcastReceiver extends BroadcastReceiver{

        int target;

        public PlugMotionBroadcastReceiver(int target){
            this.target = target;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(_started) {

                //Log.d("receiver","my target"+(this.target));

                if(target==0) {
                    _plug_target_1_index = _plug_target_1_index == (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_target_1_index + 1;
                    push(_plug_target_1_index, _plug_target_1_data, intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                    push(_plug_target_1_index, _acc_data, _last_acc_x, _last_acc_y);
                    //   Log.i(TAG,"inserting target 1");
                }if(target==1){
                    _plug_target_2_index = _plug_target_2_index == (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_target_2_index + 1;
                    push(_plug_target_2_index, _plug_target_2_data, intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                    push(_plug_target_2_index, _acc_data, _last_acc_x, _last_acc_y);
                    //  Log.i(TAG,"inserting target 2");
                }if(target==2){
                    _plug_target_3_index = _plug_target_3_index == (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_target_3_index + 1;
                    push(_plug_target_3_index, _plug_target_3_data, intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                    push(_plug_target_3_index, _acc_data, _last_acc_x, _last_acc_y);
                    //  Log.i(TAG,"inserting target 2");
                }
                //else
                //   Log.i(TAG,"erro target");

            }
        }
    }

    private class CorrelationHandler extends Thread{

        double x_corr_1;
        double y_corr_1;

        double x_corr_2;
        double y_corr_2;

        double x_corr_3;
        double y_corr_3;

        private long _lastCorrelation_1 = 0; // used to make sure we get at least 2 consecutive correlations for a match
        private long _lastCorrelation_2 = 0; // used to make sure we get at least 2 consecutive correlations for a match
        private long _lastCorrelation_3 = 0; // used to make sure we get at least 2 consecutive correlations for a match



        @Override
        public void run(){

            while(_correlationRunning){
                try {
                    sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                x_corr_1 = pc.correlation(_plug_target_1_data[0],_acc_data[0]);
                y_corr_1 = pc.correlation(_plug_target_1_data[1],_acc_data[1]);
                x_corr_2= pc.correlation(_plug_target_2_data[0],_acc_data[0]);
                y_corr_2 = pc.correlation(_plug_target_2_data[1],_acc_data[1]);
                x_corr_3= pc.correlation(_plug_target_3_data[0],_acc_data[0]);
                y_corr_3 = pc.correlation(_plug_target_3_data[1],_acc_data[1]);


                if(x_corr_1>0.8 && y_corr_1>0.8) {
                    Log.wtf(TAG, "00"+" x "+x_corr_1+" y "+y_corr_1);
                    if(_lastCorrelation_1==1) {
                        HttpRequest selected_request = new HttpRequest(SELECTED_URL+"0", getApplicationContext());
                        selected_request.start();
                        _lastCorrelation_1=0;
                    }
                    _lastCorrelation_3=0;
                    _lastCorrelation_2=0;
                    _lastCorrelation_1++;
                }

                if(x_corr_2>0.8 && y_corr_2>0.8) {
                    Log.wtf(TAG, "11"+" x "+x_corr_2+" y "+y_corr_2);
                    if(_lastCorrelation_2==1) {
                        HttpRequest selected_request = new HttpRequest(SELECTED_URL+"1", getApplicationContext());
                        selected_request.start();
                        _lastCorrelation_2=0;
                    }
                    _lastCorrelation_1=0;
                    _lastCorrelation_3=0;
                    _lastCorrelation_2++;// = System.currentTimeMillis();
                }

                if(x_corr_3>0.8 && y_corr_3>0.8) {
                    Log.wtf(TAG, "22"+" x "+x_corr_3+" y "+y_corr_3);
                    if(_lastCorrelation_3==1) {
                        HttpRequest selected_request = new HttpRequest(SELECTED_URL+"2", getApplicationContext());
                        selected_request.start();
                        _lastCorrelation_3=0;
                    }
                    _lastCorrelation_2=0;
                    _lastCorrelation_1=0;
                    _lastCorrelation_3++;
                }
              //  Log.e(TAG, "Corr"+" x "+x_corr_2+" y "+y_corr_2);
            }

        }
    }
}
