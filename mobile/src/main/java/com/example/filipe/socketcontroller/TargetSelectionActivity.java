package com.example.filipe.socketcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;


public class TargetSelectionActivity extends AppCompatActivity implements  MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final int WINDOW_SIZE = 40;
    private MotionWearListenerService _listenerService;
    private final String TAG = "main";
    GoogleApiClient _client;

    // arrays to store acc and plug data
    private double[][][] _acc_data;
    private double[][][] _plug_target_data;
    private int[] _plug_data_indexes;

    //communication stuff
    private boolean _started=false;

    //average stuff for the data from the watch
    private long   _lastAverage;
    private long _simulationSpeed;

    //correlation stuff
    private PearsonsCorrelation[] _pc;
    private boolean _correlationRunning = false;
    private long _correlationInterval   = 80;
    private CorrelationHandler _corrHandler = new CorrelationHandler();
    private double _last_acc_x = 0;
    private double _last_acc_y = 0;

    //plugs url for notifying good correlation
    private  String SELECTED_URL = MainActivity.getBaseURL()+"/plug/%/selected/";
    private  String PLUG_URL = MainActivity.getBaseURL()+"/plug/%";

    //handlers and receivers for the targets
    private ArrayList<PlugMotionHandler> _handlers = new ArrayList<PlugMotionHandler>();
    private ArrayList<PlugMotionBroadcastReceiver> _receivers = new ArrayList<>();
    private ArrayList<IntentFilter> _filters = new ArrayList<>();

    //target suff
    private int _targets=0;
    private TextView _targets_label;

    private String _socket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        _targets_label = (TextView) findViewById(R.id.targets_count);



        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();

        Wearable.MessageApi.addListener(_client, this);

        Intent i = getIntent();
        _socket = i.getStringExtra("plug");
        SELECTED_URL =  SELECTED_URL.replace("%",_socket);
        PLUG_URL = PLUG_URL.replace("%",_socket);

        Log.i(TAG,SELECTED_URL);
        Log.i(TAG,PLUG_URL);

        new TargetCounterWorker().start();

       //
    }

    public void handleStartClick(View v){

        _simulationSpeed = 40;
        _acc_data = new double[_targets][2][WINDOW_SIZE];
        _plug_target_data = new double[_targets][2][WINDOW_SIZE];
        _plug_data_indexes = new int[_targets];
        _pc = new PearsonsCorrelation[_targets];

        for(int i=0;i<_targets;i++){
            _pc[i] = new PearsonsCorrelation();
        }

        _correlationRunning = true;
        _corrHandler.start();

        for(int i=0;i<_targets;i++){
            _handlers.add(new PlugMotionHandler(getApplicationContext(),40,i,PLUG_URL));
            _receivers.add(new PlugMotionBroadcastReceiver(i));
            _filters.add(new IntentFilter(PlugMotionHandler.DATA_KEY+i));
        }

        for(int i=0;i<_targets;i++) {
            _handlers.get(i).start();
            registerReceiver(_receivers.get(i),_filters.get(i));
        }

    }

    public void startSimulation(View v){
        handleStartClick(null);
        _corrHandler.start();
    }

    public void handleTargetClick(View v){

        if(v.getId()==R.id.plus_button){
            _targets++;
            _targets_label.setText(_targets+"");
        }else if(v.getId() == R.id.minus_button){
            _targets--;
            _targets_label.setText(_targets+"");
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
       for(PlugMotionBroadcastReceiver receiver : _receivers)
            unregisterReceiver(receiver);

       for(PlugMotionHandler handler : _handlers) {
           if (handler != null)
               handler.stopSimulation();
       }

        _correlationRunning = false;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        _started = true;

        String merda = messageEvent.getPath();
        String data = merda.replace("WearAccService--", "");
        String[] tokens = data.split("#");
        try {

            if((System.currentTimeMillis()-_lastAverage>_simulationSpeed)&&_correlationRunning) {
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

    private synchronized void playSound(){
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void countTargets(){
        try {
            HttpRequest novo = new HttpRequest(PLUG_URL, getApplicationContext());
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            _targets = json_array.length();
            Log.i(TAG, "Targets:"+_targets);
            handleStartClick(null);

        } catch (JSONException e) {
            e.printStackTrace();
        } catch(InterruptedException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //correlation stuff
    private class PlugMotionBroadcastReceiver extends BroadcastReceiver{

        int target;

        public PlugMotionBroadcastReceiver(int target){
            this.target = target;
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

        @Override
        public void onReceive(Context context, Intent intent) {
            if(_started) {

                //Log.d("receiver","my target"+(this.target));
                for(int i=0;i<_targets;i++){
                    if(target==i){
                        _plug_data_indexes[i] = _plug_data_indexes[i] >= (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_data_indexes[i] + 1;
                       // Log.i(TAG,"index "+_plug_data_indexes[i]);
                        push(_plug_data_indexes[i], _plug_target_data[i], intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                        push(_plug_data_indexes[i], _acc_data[i], _last_acc_x, _last_acc_y);
                    }
                }
            }
        }
    }

    private class CorrelationHandler extends Thread{

        double _correlations[][];
        int _correlations_count[];

        private void updateCorrelations(int index, int[] correlations){

            int temp = correlations[index]+1;
            for(int i=0;i<correlations.length;i++)
                correlations[i]=0;

            correlations[index] = temp;
        }

        @Override
        public void run(){

           /* try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            countTargets();*/

            _correlations = new double[2][_targets];
            _correlations_count = new int[_targets];

            while(_correlationRunning){
                try {
                    sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for(int i=0;i<_targets;i++){
                    _correlations[0][i] = _pc[i].correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = _pc[i].correlation(_plug_target_data[i][1], _acc_data[i][1]);
                }
                for(int i=0;i<_targets;i++){
                    if (_correlations[0][i] > 0.8 && _correlations[1][i]>0.8) {
                        updateCorrelations(i,_correlations_count);
                        Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                        if(_correlations_count[i]>4) {
                            HttpRequest selected_request = new HttpRequest(SELECTED_URL + ""+i, getApplicationContext());
                            selected_request.start();
                            _correlations_count[i]=0;
                        }
                    }
                }
            }
        }
    }

    private class TargetCounterWorker extends Thread{

        @Override
        public void run(){
            try {
                Thread.sleep(1000);
                countTargets();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }
}
