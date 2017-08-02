package com.example.filipe.socketcontroller;

import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

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

    // communication with the watch
    GoogleApiClient _client;

    //View stuff
    private TextView _counter;
    private EditText _pId;
    private UI_Handler _ui_handler = new UI_Handler();

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
    private final static String BASE_URL = "http://192.168.8.113:3000";
    private final static String PLUGS_URL =BASE_URL+"/plug/";
    private  String SELECTED_URL =BASE_URL+"/plug/%/selected/";
    private  String PLUG_URL =BASE_URL+"/plug/%";
    private int _plug_selected = 0;


    //handlers and receivers for the targets
    private ArrayList<PlugMotionHandler> _handlers;
    private ArrayList<IntentFilter> _filters;
    private ArrayList<String> _plug_names;
    private ArrayList<DataAggregator> _aggregators;

    //devices
    private int _devices_count;

    int tonto =0;

    //target
    private int _target;

    //for debug
    boolean _debug_thread = false;
    SimulationView _simuView;

    //for the study

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

        _counter    = (TextView)findViewById(R.id.counter);
        _pId        = (EditText)findViewById(R.id.participant_id);
        _simuView   = (SimulationView)findViewById(R.id.simulation_view);
        _client     = new GoogleApiClient.Builder(this)
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
        try {
            double x = Double.parseDouble(tokens[0]);
            double z = Double.parseDouble(tokens[1])*-1;
            _last_acc_x = x;            // updatre the global variables to be used elsewhere in the code
            _last_acc_y = z;
        } catch (NumberFormatException e) {
            Log.e(TAG, "format exception data " + data);
        }
    }

    private void push(int index, double[][] data,double x, double y){


        if(index < WINDOW_SIZE){
            data[0][index] = x;
            data[1][index] = y;

        }else{

            int i=0;
            for(i=1; i< WINDOW_SIZE; i++){
                data[0][i-1] = data[0][i];
                data[1][i-1] = data[1][i];
            }
            data[0][i-1] = x;
            data[1][i-1] = y;
        }
    }

    private void stopServices(){


        for(PlugMotionHandler handler : _handlers) {
            if (handler != null)
                handler.stopSimulation();
            Log.i(TAG, "stoping simulation");
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

    /*
        Handlers for the user interaction most of these are for the debug purposes
     */

    public void handleDebugClick(View v){
        if(v.getId() == R.id.debug_btn)
            _debug_thread = !_debug_thread;
        else if(v.getId()==R.id.start_thread){
            new PrintCurrentData().start();
            _debug_thread= false;
        }
    }

    public void handleStartStudyClick(View v){
        HttpRequest novo = new HttpRequest(BASE_URL+"/plug/start/6", getApplicationContext());
        novo.start();
    }

    public void handleUpdateClick(View v){
        new AsyncTask(){
            @Override
            protected Object doInBackground(Object[] objects) {
                _handlers.get(_target).forceUpdate();
                return null;
            }
        }.execute();
    }

    public void handleTestClick(View v){

        HttpRequest novo = new HttpRequest("http://192.168.8.113:3000/plug/2/selected/"+_target, getApplicationContext());
        novo.start();
    }

    public void handleRefreshClick(View v){

        new RefreshTarget().start();

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

    public void handleStartClick(String url){

        _corrHandler = new CorrelationHandler();

        _handlers = new ArrayList<>();
        _aggregators = new ArrayList<>();

        _simulationSpeed    = 40;   // alterei aqui
        _acc_data           = new double[_devices_count][2][WINDOW_SIZE];
        _plug_target_data   = new double[_devices_count][2][WINDOW_SIZE];
        _plug_data_indexes  = new int[_devices_count];

        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        for(int i=0;i<_devices_count;i++){
            _handlers.add(new PlugMotionHandler(getApplicationContext(),(int)_simulationSpeed,i,url,queue));
            _aggregators.add(new DataAggregator(i,_simulationSpeed));
        }

        for(int i=0;i<_devices_count;i++) {
            _handlers.get(i).start();
            _aggregators.get(i).start();
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
            _acc_data           = new double[_devices_count][2][WINDOW_SIZE];
            _plug_target_data   = new double[_devices_count][2][WINDOW_SIZE];
            _plug_data_indexes  = new int[_devices_count];

            Log.i(TAG,"TARGET: "+_target);
            _started            = true;
            _updating           = false;
            _correlationRunning = true;
            new CorrelationHandler().start();

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
            Log.i(TAG, "---- AVAILABLE PLUGS ---");
            for(int i=0;i<_devices_count;i++){
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
                    if ((int) (obj.get("blue")) == 0)
                        _target = i;

                    _plug_names.add(obj.getString("name").substring(0, obj.getString("name").indexOf(".")).replace("plug", ""));
                    Log.i(TAG, "plug "+_plug_names.get(i));

                }catch (JSONException e){
                    Log.wtf(TAG," não devia dar prob aqui");
                }
            }
            Log.i(TAG,"-------");
            Log.i(TAG,"TARGET: "+_target);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch(InterruptedException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
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

        //
        @Override
        public void run(){

            _correlations       = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];

            while(_correlationRunning){

                for(int i=0;i<_devices_count;i++){
                    //PearsonsCorrelation pc = new PearsonsCorrelation();
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                }
                for(int i=0;i<_devices_count;i++){


                    if (_correlations[0][i] > 0.85 && _correlations[1][i]>0.85) {
                        updateCorrelations(i,_correlations_count);
                        Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                        if(_correlations_count[i]==5) {
                            Log.i(TAG,"* Seleccionei o  device "+i+"*");

                            if(tonto==0) {

                                _updating = true;
                                _correlationRunning = false;
                                stopServices();
                                HttpRequest novo = new HttpRequest(PLUGS_URL + "/" + _plug_names.get(i) + "/selected", getApplicationContext());
                                novo.start();
                                try {
                                    novo.join();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                tonto++;
                                PLUG_URL = PLUG_URL.replace("%",_plug_names.get(i)+"");
                                SELECTED_URL =  SELECTED_URL.replace("%",_plug_names.get(i)+"");
                                _plug_selected = Integer.parseInt(_plug_names.get(i));

                                new StartUp(PLUG_URL).start();

                            }else {
                                if (i == _target){
                                    HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" + i, getApplicationContext());
                                    selected_request.start();
                                    try {
                                        Thread.sleep(1500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    new UpdateStudy(3).start();

                                }else
                                    Log.i(TAG,"Wrong correlation");
                            }
                            _correlations_count[i] = 0;
                        }
                    }
                }
                try {
                    sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
            try {
                _updating = true;
                _started = false;
                getPlugsData(_url);
                Thread.sleep(1000);
                handleStartClick(_url);
                Thread.sleep(1000);
                _correlationRunning = true;
                _started = true;
                _updating = false;
                _corrHandler.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class RefreshTarget extends Thread{

        private final static String URL_PLUG =BASE_URL+"/plug/";

        @Override
        public void run(){

            try {
                _updating = true;
                _started = false;
                _correlationRunning = false;

                HttpRequest novo = new HttpRequest(URL_PLUG+"/start/6", getApplicationContext());
                novo.start();
                novo.join();

                Thread.sleep(300);

                novo = new HttpRequest(URL_PLUG+_plug_selected+"/selected", getApplicationContext());
                novo.start();
                novo.join();

                for(PlugMotionHandler tes: _handlers) {
                    Log.i(TAG,"forcing the update of the handlers");
                    //Thread.sleep(60);
                    tes.forceUpdate();
                }

                updateTarget(URL_PLUG+_plug_selected);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class PrintCurrentData extends Thread{

        public void run(){

            while(true){

                if(_debug_thread){
                    try {
                        Log.i("TARGET POS Thread","current led x pos "+_handlers.get(_target).getPosition()[0]);  //.getPosition()[0]+","+_handlers.get(_led_target).getPosition()[1]);
                        sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
    }

    private class DataAggregator extends Thread {

        private int _led_target;
        private long _samplingDiff;
        private boolean _aggregatorRunning = true;
        public DataAggregator(int target, long samplingDiff){
            _led_target = target;
            _samplingDiff = samplingDiff;
        }

        public void run(){

            while(_aggregatorRunning){

                if(!_updating){
                    _plug_data_indexes[_led_target] = _plug_data_indexes[_led_target] >= (WINDOW_SIZE - 1) ? WINDOW_SIZE : _plug_data_indexes[_led_target] + 1;
                    push(_plug_data_indexes[_led_target], _plug_target_data[_led_target], _handlers.get(_led_target).getPosition()[0], _handlers.get(_led_target).getPosition()[1]);
                    push(_plug_data_indexes[_led_target], _acc_data[_led_target], _last_acc_x,_last_acc_y);

                    if((_led_target==_target)&&_debug_thread) {     // used to print the simulation on the screen
                        _simuView.setCoords((float) _handlers.get(_led_target).getPosition()[0], (float) _handlers.get(_led_target).getPosition()[1]);
                    }
                }
                try {
                    Thread.sleep(_samplingDiff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }}

        public void stopAgregator(){
            _aggregatorRunning = false;
        }

    }

    private class UI_Handler extends Handler{

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.arg1==1){
                _counter.setText(msg.arg2+"");
            }else if(msg.arg1==2){
                _counter.setText("Start !!");
            }

        }
    }

    private class UpdateStudy extends Thread{

        private int _countDown;

        public UpdateStudy(int countDown){
            _countDown = countDown;
        }

        @Override
        public void run(){
            try {
                // refreshes the view
                RefreshTarget thread = new RefreshTarget();
                thread.start();
                thread.join();

                // count down
                for(int i=_countDown;i>=0;i--) {
                    Message msg = Message.obtain();
                    msg.arg1 = 1;
                    msg.arg2 = i;
                    _ui_handler.sendMessage(msg);
                    Thread.sleep(1000);
                    Log.i(TAG,"Sleeping "+i+" "+_countDown);
                }

                // Start!
                Message msg = Message.obtain();
                msg.arg1=2;
                _ui_handler.sendMessage(msg);

                // Beep to start
                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
