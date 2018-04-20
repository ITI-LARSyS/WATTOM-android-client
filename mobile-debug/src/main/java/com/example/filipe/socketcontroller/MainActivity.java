package com.example.filipe.socketcontroller;

import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
import com.example.filipe.socketcontroller.motion.PlugMotionHandler;
import com.example.filipe.socketcontroller.util.HttpRequest;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
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
    private RequestQueue _queue; // will use always the same RequestQueue to avoid memory overflow

    //average stuff for the data from the watch
    private long   _lastAverage = 0;
    private long _simulationSpeed;      // alterei isto

    //correlation stuff
    private final PearsonsCorrelation pc = new PearsonsCorrelation();
    private boolean _correlationRunning = false;
    private long _correlationInterval   = 60;
    private CorrelationHandler _corrHandler;// = new CorrelationHandler();
    private  double  _last_acc_x = 0;
    private double _last_acc_y = 0;

    //plugs url for notifying good correlation
    private final static String BASE_URL = "http://192.168.8.196:3000";
    //  private final static String BASE_URL = "http://192.168.1.7:3000";

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
    private int _participant;
    String _studyResult = "Participant,Target_selection,Acquisition_time,Angle,Pointing,Timestamp";
    private long _aquisition_time=0;
    private int _angle=0;
    private boolean _target_selection = true;
    private String _pointing =  "towards";
    private long _match_limit=7000;
    private boolean _countingTime=false;
    private UpdateStudy _updateStudyWorker;
    int _plug     = 3;
    int _trialCount = 0;
    int _angleCount = 0;
    final int _angles[]={1, 2, 3, 4, 5, 6};
    private TextView _instructions;
    private TextView _condition;
    private TextView _trial_field;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       // _counter        = (TextView) findViewById(R.id.counter);
      //  _pId            = (EditText) findViewById(R.id.participant_id);
        _simuView       = (SimulationView) findViewById(R.id.simulation_view);
       // _instructions   = (TextView) findViewById(R.id.instructions_field);
       // _condition      = (TextView) findViewById(R.id.condition_field);
       // _trial_field    = (TextView) findViewById(R.id.trial_field);

        //_simuView.setVisibility(View.GONE);
      /*  FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        final View debug_view = findViewById(R.id.debug_view);
        debug_view.setVisibility(View.GONE);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //_debug_thread = !_debug_thread;
                if(debug_view.getVisibility()==View.VISIBLE)
                    debug_view.setVisibility(View.GONE);
                else
                    debug_view.setVisibility(View.VISIBLE);
            }
        }); */

       _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();

       _client.connect();
        Wearable.MessageApi.addListener(_client, this);
       _queue = Volley.newRequestQueue(getApplicationContext());

    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        _client.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        _started = true;

        String merda = messageEvent.getPath();
        String data = merda.replace("acc", "");
        String[] tokens = data.split("#");
        Log.i(TAG,"test");
        try {
            double x = Double.parseDouble(tokens[0]);
            double z = Double.parseDouble(tokens[1])*-1;
            _last_acc_x = x;            // updatre the global variables to be used elsewhere in the code
            _last_acc_y = z;
            Log.i(TAG,"got data from watch x "+x+","+z);
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

        if(_handlers!=null){
            for(PlugMotionHandler handler : _handlers) {
                if (handler != null)
                    handler.stopSimulation();
                Log.i(TAG, "stoping simulation");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        Log.wtf(TAG, " wtf called from main activity");
        stopServices();
        _correlationRunning = false;
   //     saveFile();
        _client.disconnect();
    }

    private boolean saveFile(){
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/participants_data"); // creates the dir
        myDir.mkdirs();                                     // builds the dir, (only returns true once)
        String fname = _participant+"__"+System.currentTimeMillis()+".txt"; // file name with the pId plus the current tms
        File file = new File (myDir, fname);
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(_studyResult.getBytes());
            out.flush();
            out.close();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
//        _participant = Integer.parseInt(_pId.getText().toString());
     //   _angle       = 0;
   //     _pointing    = _condition.getText().toString();
        SELECTED_URL =  SELECTED_URL.replace("%",_plug+"");
        new StartUp(PLUGS_URL+_plug).start();
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

        _participant = Integer.parseInt(_pId.getText().toString());
        _angle       = 0;
        _pointing    = "towards";
    }

    public void handleRefreshClick(View v){

        _corrHandler.updateTarget(0,false);
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

    public void firstStartup(String url){

        _corrHandler = new CorrelationHandler();

        _handlers = new ArrayList<>();
        _aggregators = new ArrayList<>();

        _simulationSpeed    = 40;   // alterei aqui
        _acc_data           = new double[_devices_count][2][WINDOW_SIZE];
        _plug_target_data   = new double[_devices_count][2][WINDOW_SIZE];
        _plug_data_indexes  = new int[_devices_count];

        for(int i=0;i<_devices_count;i++){
            _handlers.add(new PlugMotionHandler(getApplicationContext(),(int)_simulationSpeed,i,url,_queue));
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
            HttpRequest novo = new HttpRequest(server_url, getApplicationContext(),_queue);
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
            _correlationRunning = true;
            new CorrelationHandler().start();

        }catch (Exception e){
            Log.wtf("UPDATE","Exception aqui");
            e.printStackTrace();
        }
    }

    private void getPlugsData(String server_url){
        try {

            _plug_names = new ArrayList<>();

            HttpRequest novo = new HttpRequest(server_url, getApplicationContext(),_queue);
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            _devices_count = json_array.length();
            Log.i(TAG, "---- AVAILABLE PLUGS ---");
            for(int i=0;i<_devices_count;i++){
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
//                    if ((int) (obj.get("blue")) == 0)
                        _target = i;
                        /*String red = Integer.toHexString(Integer.parseInt(obj.getString("red")));
                        String green =  Integer.toHexString(Integer.parseInt(obj.getString("green")));
                    String blue =  Integer.toHexString(Integer.parseInt(obj.getString("blue")));
                    _simuView.setColor(i,"#"+red+green+blue);*/
                    int red = Integer.parseInt(obj.getString("red"));
                    int green =  Integer.parseInt(obj.getString("green"));
                    int blue =  Integer.parseInt(obj.getString("blue"));
                    String hex = String.format("#%02x%02x%02x",red,green,blue);
                    _simuView.setColor(i,hex);
                    System.out.println("cor:"+"#"+red+green+blue);
                    _plug_names.add("3");
                    //_plug_names.add(obj.getString("name").substring(0, obj.getString("name").indexOf(".")).replace("plug", ""));
                    Log.i(TAG, "plug "+_plug_names.get(i));

                }catch (JSONException e){
                    Log.wtf(TAG," não devia dar prob aqui");
                    e.printStackTrace();
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

    private class StartUp extends Thread{

        private String _url;

        public StartUp(String url){
            this._url = url;
        }

        @Override
        public void run(){
            try {

                Log.e(TAG, "Starging up");

                _updating = true;
                _started = false;
                getPlugsData(_url);
                Thread.sleep(500);
                firstStartup(_url);
                Thread.sleep(1000);
                _correlationRunning = true;
                _started = true;
                _updating = false;
                _corrHandler.start();

                for(int i=1;i<_handlers.size();i++) {
                    Log.d(TAG,"forcing the update of the handlers");
                    _handlers.get(i).forceUpdate();
                    Thread.sleep(100);
                }
                _aquisition_time = System.currentTimeMillis()+5000;
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

                Log.e(TAG, "Refreshing");


                _updating = true;
                _started = false;
                _correlationRunning = false;

                Message msg = Message.obtain();
                msg.arg1 = 3;
                _ui_handler.sendMessage(msg);

                // Thread.sleep(1000);

                //   refresh = new HttpRequest(URL_PLUG+"/start/6", getApplicationContext(),_queue);
                // refresh.start();
                //    refresh.join();

                Thread.sleep(1000);

                HttpRequest refresh = new HttpRequest(URL_PLUG+_plug+"/refresh/", getApplicationContext(),_queue);
                refresh.start();
                refresh.join();

                Thread.sleep(300);

                for(PlugMotionHandler tes: _handlers) {
                    Log.i(TAG,"forcing the update of the handlers");
                    tes.forceUpdate();
                    Thread.sleep(60);
                }

                Thread.sleep(100);

                updateTarget(URL_PLUG+_plug);

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

                    if((_led_target==_target)) {     // used to print the simulation on the screen
                        //if((_led_target ==_target[0])) {     // used to print the simulation on the screen
                        for(int i = 0; i < _devices_count; i++)
                        {
                            _simuView.setCoords(i,(float) _handlers.get(i).getPosition()[0], (float) _handlers.get(i).getPosition()[1]);
                            //_simuView.setCoords((float) _handlers.get(_led_target).getPosition()[0], (float) _handlers.get(_led_target).getPosition()[1],(float)_last_acc_x,(float)_last_acc_y);
                        }
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
                for(int i=_countDown;i>=1;i--) {
                    Message msg = Message.obtain();
                    msg.arg1 = 1;
                    msg.arg2 = i;
                    _ui_handler.sendMessage(msg);
                    Thread.sleep(1000);
                    Log.i(TAG,"Sleeping "+i+" "+_countDown);
                    // _handlers.get(0).forceUpdate();
                }
                //meio hack

                // Start!
                Message msg = Message.obtain();
                msg.arg1=2;
                _ui_handler.sendMessage(msg);
                // Beep to start
                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                toneGen1.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE,150);
                // reset the vars that count the aquisition time
                _handlers.get(0).forceUpdate();

                _countingTime    = true;
                _updating        = false;
                _aquisition_time = System.currentTimeMillis();

            } catch (InterruptedException e) {
                e.printStackTrace();
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
        //
        @Override
        public void run(){

            _correlations       = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];

            while(_correlationRunning){
                if(_countingTime)       // check if we are counting time in the current matching process
                    checkRunningTime();

                for(int i=0;(i<_devices_count) && (_plug_data_indexes[_target] == WINDOW_SIZE);i++){
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                }
                for(int i=0;i<_devices_count;i++){
                  //  Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);

                    if ((_correlations[0][i] > 0.8 && _correlations[0][i] < 0.9999) && (_correlations[1][i]>0.8 &&  _correlations[1][i]<0.9999)) {  // sometimes at the start we get 1.0 we want to avoid that
                        if(!_updating)
                            updateCorrelations(i,_correlations_count);



                        if(_correlations_count[i]==3) {
                            _correlations_count[i] = 0;

                            Log.i("Positive Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);

                            try {
                                HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" + i, getApplicationContext(),_queue);
                                selected_request.start();
                                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                                selected_request.join();
                                Log.e(TAG, "-----   running "+SELECTED_URL + "" + i+" request  ------");

                                Thread.sleep(6000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                           /* if (i == _target){
                                _target_selection = true;
                                // Beep to show its the correct match
                                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                                updateTarget(i,true);
                            }else{
                                Log.i(TAG,"Wrong correlation");
                                _target_selection = false;
                                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                                updateTarget(i,true);
                            }*/
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

        public boolean checkRunningTime(){

            if((System.currentTimeMillis()-_aquisition_time)>_match_limit){
                _target_selection = false;
                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                toneGen1.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,150);
                Log.wtf("RUNNING TIME","... OVERTIME ...");
                if(_countingTime)       // make sure we do not do this while other updates are going on
                    updateTarget(_target,false);
                return false;
            }else
                return true;
        }

        private synchronized void updateTarget(int led_target, boolean match){
            _countingTime     = false;
            _aquisition_time = System.currentTimeMillis()-_aquisition_time;
            _studyResult = _studyResult +"\n"+_participant+","+_target_selection+","+_aquisition_time+","+_angles[_angleCount]+","+_pointing+","+(System.currentTimeMillis());
            Log.wtf("Corr", "aq time= "+_aquisition_time);
            if(match){
                try {
                    HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" + led_target, getApplicationContext(),_queue);
                    selected_request.start();
                    selected_request.join();
                    Log.e(TAG, "-----   running "+SELECTED_URL + "" + led_target+" request  ------");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(_updateStudyWorker==null || !_updateStudyWorker.isAlive()) {
                _trialCount++;
                Log.wtf("Corr", "trialCount = "+_trialCount);

                Message msg = Message.obtain();
                msg.arg1= 0;
                _ui_handler.sendMessage(msg);

                msg = Message.obtain();
                msg.arg1 = 5;
                msg.arg2 = _trialCount;
                _ui_handler.sendMessage(msg);

                // if(_trialCount==1){
                if(_trialCount==21){

                    _trialCount = 0;
                    _angleCount++;

                    msg = Message.obtain();
                    msg.arg1= 4;
                    msg.arg2 = _angleCount+1;
                    _ui_handler.sendMessage(msg);

                    Log.wtf("Corr", "angle count="+_angleCount);

                    if(_angleCount==6){
                        msg = Message.obtain();
                        msg.arg1= 99;
                        _ui_handler.sendMessage(msg);
                        Log.wtf("Corr", "AQUI");

                       // stopServices();
                       // _correlationRunning = false;
                    }else{
                        _updateStudyWorker = new UpdateStudy(10);
                        _updateStudyWorker.start();
                    }
                }else{
                    _updateStudyWorker = new UpdateStudy(3);
                    _updateStudyWorker.start();
                }
            }
        }
    }

    private class UI_Handler extends Handler{

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.arg1 == 0){
                _instructions.setText("-");
            }else if(msg.arg1==1){
                _counter.setText(msg.arg2+"");
            }else if(msg.arg1==2){
                _counter.setText("Start !!");
            }else if(msg.arg1==3){
                _counter.setText("Please wait!!");
            }else if(msg.arg1==4){
                _instructions.setText("Pease move to the next position");
            }else if(msg.arg1==5){
                _trial_field.setText("Trial "+msg.arg2+" out of 21");
            }else if(msg.arg1==99){
                _counter.setText("Thank you");
                _instructions.setText("-");
            }

        }
    }
  /*  private class MatchTimer implements Runnable{

        private final String TAG = "Timer";

        @Override
        public void run() {
            try {
                Thread.sleep(_match_limit);
                _target_selection = false;
                _aquisition_time = System.currentTimeMillis()-_aquisition_time;
                _studyResult = _studyResult +"\n"+_participant+","+_target_selection+","+_aquisition_time+","+_angle+","+_pointing;
                //HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" +_target, getApplicationContext());
                //selected_request.start();
                //new UpdateStudy(3).start();
                Log.i(TAG,_studyResult);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }*/
}
