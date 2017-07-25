package com.example.filipe.socketcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements  MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static String TAG = "DeviceSelection";
    public static final int WINDOW_SIZE = 40;  // terá qde ser 80

    GoogleApiClient _client;

    // arrays to store acc and plug data
    private double[][][] _acc_data;
    private double[][][] _plug_target_data;
    private int[] _plug_data_indexes;

    //communication stuff
    private boolean _started=false;
    private boolean _updating=false;

    //average stuff for the data from the watch
    private long   _lastAverage = 0;
    private long _simulationSpeed;      // alterei isto

    //correlation stuff
    private final PearsonsCorrelation pc = new PearsonsCorrelation();
    private boolean _correlationRunning = false;
    private long _correlationInterval   = 120;
    private CorrelationHandler _corrHandler;// = new CorrelationHandler();
    private  double  _last_acc_x = 0;
    private double _last_acc_y = 0;

    //plugs url for notifying good correlation
    private final static String PLUGS_URL = "http://192.168.8.113:3000/plug/";
    private  String SELECTED_URL = "http://192.168.8.113:3000/plug/%/selected/";
    private  String PLUG_URL = "http://192.168.8.113:3000/plug/%";
    private int _plug_selected = 0;


    //handlers and receivers for the targets
    private ArrayList<PlugMotionHandler> _handlers;
    private ArrayList<PlugMotionBroadcastReceiver> _receivers;
    private ArrayList<IntentFilter> _filters;

    private ArrayList<String> _plug_names;

    //devices
    private int _devices_count;

    int tonto =0;

    //target
    private int _target;

    //testar isto
      boolean _debug_thread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                cleanUp();
            }
        });


        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();

        Wearable.MessageApi.addListener(_client, this);

        new StartUp(PLUGS_URL).start();

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        _started = true;

        String merda = messageEvent.getPath();
        String data = merda.replace("acc", "");
        String[] tokens = data.split("#");
        // Log.i(TAG,"message received from watch");
        try {

                double x = Double.parseDouble(tokens[0]);
                double z = Double.parseDouble(tokens[1])*-1;
                _last_acc_x = x;
                _last_acc_y = z;
                testStuff(_last_acc_x,_last_acc_y);
                // Log.i("WATCH","from watch "+_last_acc_x+","+_last_acc_y);
        } catch (NumberFormatException e) {
            Log.e(TAG, "format exception data " + data);
        }
    }

    /*

            NEW STUFF

     */
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

    private void testStuff(double accx, double accy) {

        //   Log.i("TESTSTUFF", _handlers.get(0).getPosition()[0] + "," + _handlers.get(0).getPosition()[1]+","+(accx-150)+","+(accy-40));

       // if(System.currentTimeMillis()-_lastAverage>40){
        for(int i=0;i<_devices_count && !_updating;i++){
                _plug_data_indexes[i] = _plug_data_indexes[i] >= (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_data_indexes[i] + 1;
                push(_plug_data_indexes[i], _plug_target_data[i], _handlers.get(i).getPosition()[0], _handlers.get(i).getPosition()[1]);
                push(_plug_data_indexes[i], _acc_data[i], accx, accy);
    }
            //_lastAverage=System.currentTimeMillis();
        }
   // }
    /*


                END OF NEW STUFF


     */
    private void stopServices(){

      //  for(PlugMotionBroadcastReceiver receiver : _receivers)
        //    unregisterReceiver(receiver);

        for(PlugMotionHandler handler : _handlers) {
            if (handler != null)
                handler.stopSimulation();
        }


    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        Log.wtf(TAG," wtf called from main activity");
        stopServices();
        _correlationRunning = false;
    }

    public void handleDebugClick(View v){

     //  Log.i("DEBUG","x,watch_x,y,watch_y");
     //       for(int i=0;i<WINDOW_SIZE;i++){
            //PearsonsCorrelation pc = new PearsonsCorrelation();
    //        Log.i("DEBUG",_plug_target_data[_target][0][i]+","+ _acc_data[_target][0][i]+","
      //              +_plug_target_data[_target][1][i]+","+_acc_data[_target][1][i]);
      //  }

        _debug_thread = !_debug_thread;
    }

    private void cleanUp(){
        _acc_data = new double[_devices_count][2][WINDOW_SIZE];
        _plug_target_data = new double[_devices_count][2][WINDOW_SIZE];

        for(int i=0;i<_devices_count;i++){
            for(int j=0;j<WINDOW_SIZE;j++){
                _acc_data[i][0][j]=0;
                _acc_data[i][1][j]=0;
            }
        }

        for(int i=0;i<_devices_count;i++){
            for(int j=0;j<WINDOW_SIZE;j++){
                _plug_target_data[i][0][j]=0;
                _plug_target_data[i][1][j]=0;
            }
        }

    }

    public void handleTestClick(View v){


       HttpRequest novo = new HttpRequest("http://192.168.8.113:3000/plug/2/selected/"+_target, getApplicationContext());
       novo.start();
    }

    public void handleRefreshClick(View v){

        new RefreshTarget().start();

    }
    public void handleStartClick(String url){

        _corrHandler = new CorrelationHandler();
        // _corrHandler.start();

        _handlers = new ArrayList<>();
        //_receivers = new ArrayList<>();
        //_filters = new ArrayList<>();

        _simulationSpeed = 40;                  // alterei aqui
        _acc_data = new double[_devices_count][2][WINDOW_SIZE];
        _plug_target_data = new double[_devices_count][2][WINDOW_SIZE];
        _plug_data_indexes = new int[_devices_count];
        /* _new_acc = new boolean[_devices_count];

        for(int i=0;i<_devices_count;i++)
            _new_acc[i]=false;
*/
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        for(int i=0;i<_devices_count;i++){
            _handlers.add(new PlugMotionHandler(getApplicationContext(),(int)_simulationSpeed,i,url,queue));
            //_receivers.add(new PlugMotionBroadcastReceiver(i));
            //_filters.add(new IntentFilter(PlugMotionHandler.DATA_KEY+i));
        }

        for(int i=0;i<_devices_count;i++) {
            _handlers.get(i).start();
            //registerReceiver(_receivers.get(i),_filters.get(i));
        }

        _corrHandler.start();
        _correlationRunning = true;
        _started = true;
        _updating = false;
        new PrintCurrentData().start();
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

    private void updateTarget(String server_url) {
        try{
            _plug_names = new ArrayList<>();

            HttpRequest novo = new HttpRequest(server_url, getApplicationContext());
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            _devices_count = json_array.length();

            for (int i = 0; i < _devices_count; i++) {
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
                    if ((int) (obj.get("blue")) == 0)
                        _target = i;

                } catch (JSONException e) {
                    Log.wtf(TAG, " não devia dar prob aqui");
                }
            }
            _acc_data = new double[_devices_count][2][WINDOW_SIZE];
            _plug_target_data = new double[_devices_count][2][WINDOW_SIZE];
            _plug_data_indexes = new int[_devices_count];

            Log.i(TAG,"TARGET: "+_target);
            _started = true;
            _updating = false;

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void getPlugsData(String server_url){
        try {

            _plug_names = new ArrayList<>();

            HttpRequest novo = new HttpRequest(server_url, getApplicationContext());
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            _devices_count = json_array.length();

            for(int i=0;i<_devices_count;i++){
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
                    if ((int) (obj.get("blue")) == 0)
                        _target = i;

                    _plug_names.add(obj.getString("name").substring(0, obj.getString("name").indexOf(".")).replace("plug", ""));

                }catch (JSONException e){
                    Log.wtf(TAG," não devia dar prob aqui");
                }
            }
            handleStartClick(server_url);
            Log.i(TAG,"TARGET: "+_target);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch(InterruptedException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //correlation stuff
    private class PlugMotionBroadcastReceiver extends BroadcastReceiver {

        int target;

        public PlugMotionBroadcastReceiver(int target){
            this.target = target;
        }


        @Override
        public void onReceive(Context context, Intent intent) {
            if(_started) {


                for(int i=0;i<_devices_count;i++){
                    if(target==i){
                        //_new_acc[i] = false;
                        _plug_data_indexes[i] = _plug_data_indexes[i] >= (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_data_indexes[i] + 1;
                        push(_plug_data_indexes[i], _plug_target_data[i], intent.getDoubleExtra("x", -1), intent.getDoubleExtra("y", -1));
                        push(_plug_data_indexes[i], _acc_data[i], _last_acc_x, _last_acc_y);
                    }
                    if(target ==_target)
                       Log.i("PUSH","simulation");
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

            _correlations = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];

            while(_correlationRunning){
                try {
                    sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for(int i=0;i<_devices_count;i++){
                    //PearsonsCorrelation pc = new PearsonsCorrelation();
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                }
                for(int i=0;i<_devices_count;i++){

                  //  Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);

                    if (_correlations[0][i] > 0.85 && _correlations[1][i]>0.85) {
                        updateCorrelations(i,_correlations_count);
                        Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                        if(_correlations_count[i]==5) {
                            Log.i(TAG,"**************************");
                            Log.i(TAG,"* Seleccionei o  device "+i+"*");
                            Log.i(TAG,"**************************");
                            if(tonto==0) {
                                HttpRequest novo = new HttpRequest(PLUGS_URL + "/" + _plug_names.get(i) + "/selected", getApplicationContext());
                                novo.start();
                                try {
                                    novo.join();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                stopServices();
                                tonto++;
                                _correlationRunning = false;
                                PLUG_URL = PLUG_URL.replace("%",_plug_names.get(i)+"");
                                SELECTED_URL =  SELECTED_URL.replace("%",_plug_names.get(i)+"");
                                _plug_selected = Integer.parseInt(_plug_names.get(i));
                                new StartUp(PLUG_URL).start();
                            }else {
                                if (i == _target){
                                    HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" + i, getApplicationContext());
                                    selected_request.start();
                                }else
                                    Log.i(TAG,"Wrong correlation");
                            }

                            /* Intent targetSelection = new Intent(getApplicationContext(),TargetSelectionActivity.class);
                            targetSelection.putExtra("plug",_plug_names.get(i));
                            startActivity(targetSelection);*/
                            _correlations_count[i] = 0;
                        }

                    }
                }

            }

        }
    }

    private class StartUp extends Thread{

        private String _url;

        public StartUp(String url){
            this._url = url;
        }

        @Override
        public void run(){
          // try {
                _updating = true;
                _started = false;
               // Thread.sleep(1000);
                getPlugsData(_url);

               // updateTarget(_url);
           // } catch (InterruptedException e) {
            //    e.printStackTrace();
           // }

        }

    }

    private class RefreshTarget extends Thread{

        private final static String URL_PLUG = "http://192.168.8.113:3000/plug/";
        private  String SELECTED_URL = "http://192.168.8.113:3000/plug/%/selected/";

        @Override
        public void run(){
            //  HttpRequest novo = new HttpRequest(URL_PLUG+ _plug_selected +"/stopLeds/", getApplicationContext());
            //  novo.start();

            try {
                _updating = true;
                _started = false;

                HttpRequest novo = new HttpRequest(URL_PLUG+"/start/6", getApplicationContext());
                novo.start();
                novo.join();

                Thread.sleep(1000);

                novo = new HttpRequest(URL_PLUG+_plug_selected+"/selected", getApplicationContext());
                novo.start();
                novo.join();

                for(PlugMotionHandler tes: _handlers) {
                    Thread.sleep(55);
                    tes.forceUpdate();
                }

                updateTarget(URL_PLUG+_plug_selected);

               // Thread.sleep(3000);
                _updating = false;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class PrintCurrentData extends Thread{

        public void run(){

            while(true){


                try {
                        //testStuff(_last_acc_x,_last_acc_y);
                    sleep(_simulationSpeed);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }
    }

}
