package com.example.filipe.socketcontroller;

import android.content.ContentValues;
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
import com.example.filipe.socketcontroller.util.Alarm;
import com.example.filipe.socketcontroller.util.HttpRequest;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.example.filipe.socketcontroller.util.UI.toast;

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
    // private final static String BASE_URL = "http://192.168.0.112:3000";
    private final static String BASE_URL = "http://192.168.1.8:3000";

    //  private final static String BASE_URL = "http://192.168.1.7:3000";

    private final static String PLUGS_URL =BASE_URL+"/plug/";
    private  String SELECTED_URL =BASE_URL+"/plug/%/selected/";
    private  String PLUG_URL =BASE_URL+"/plug/%";
    private String _plug_selected = "";


    //handlers and receivers for the targets
    private ArrayList<PlugMotionHandler> _handlers;
    private ArrayList<IntentFilter> _filters;
    private ArrayList<String> _plug_names;
    private ArrayList<DataAggregator> _aggregators;

    //devices
    private int _devices_count;
    private Node _wear;

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

    //Tei DEMO STUFF
    private int mode = 0;
    private int relay = 0;
    private JSONArray activeLeds;
    //0 - plug selection
    //1 - ON/OFF or Disagregation
    //3 - Disagregation
    //4 - Scheduling

    // Scheduling stuff
    private int vez = 0;
    private int hourStart,minStart,hourEnd,minEnd;
    private int HourScheduleStart, MinutesScheduleStart,HourScheduleEnd, MinutesScheduleEnd;
    private ArrayList<Alarm>  _alarms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        _simuView       = (SimulationView) findViewById(R.id.simulation_view);

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();

        _client.connect();
        Wearable.MessageApi.addListener(_client, this);
        _queue = Volley.newRequestQueue(getApplicationContext());
        _alarms = new ArrayList<Alarm>();

    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        _client.connect();
        //new InitAvailablePlugs(InitAvailablePlugs.FIRST_STARTUP).start();
        // lets startup the system no matter what
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        _started = true;

        String merda = messageEvent.getPath();
        String data = merda.replace("acc", "");
        String [] horas = data.split("/");
        if(merda.contains("start")) {
            Log.e(TAG," Recebi mensagem para começar");
            startUpDemo();
        }else if(merda.contains("stop")){
            Log.e(TAG,"Recebi mensagem para para parar");
            _correlationRunning=false;
            mode = 0;
            stopServices();
        }
        else if(horas.length == 2){
            Log.i(TAG, "horas: "+horas.toString());
            //parar tudo aqui:
            _correlationRunning = false;
            stopServices();
            String [] detailsStart = horas[0].split(":");
            hourStart = Integer.parseInt(detailsStart[0]);
            minStart = Integer.parseInt(detailsStart[1]);
            detailsStart = horas[1].split(":");
            hourEnd = Integer.parseInt(detailsStart[0]);
            minEnd = Integer.parseInt(detailsStart[1]);
            HourScheduleStart = hourStart;
            MinutesScheduleStart = minStart;
            HourScheduleEnd = hourEnd;
            MinutesScheduleEnd = minEnd;
            Log.i(TAG, "horas "+HourScheduleStart+":"+MinutesScheduleStart+"  "+HourScheduleEnd+":"+MinutesScheduleEnd);
            /*if(vez == 0){
                // _correlationRunning = false;
                String [] detailsStart = horas[0].split(":");
                hourStart = Integer.parseInt(detailsStart[0]);
                minStart = Integer.parseInt(detailsStart[1]);
                detailsStart = horas[1].split(":");
                hourEnd = Integer.parseInt(detailsStart[0]);
                minEnd = Integer.parseInt(detailsStart[1]);
                HourScheduleStart = hourStart;
                MinutesScheduleStart = minStart;
                HourScheduleEnd = hourEnd;
                MinutesScheduleEnd = minEnd;

                if(hourStart > 12){
                    hourStart = hourStart - 12;
                }else if (hourStart == 0){
                    hourStart = 0;
                }
                if(minStart < 5){
                    minStart = 0;
                }else{
                    minStart = Math.round(minStart/5);
                }

                if(hourEnd > 12){
                    hourEnd = hourEnd - 12;
                }else if(hourEnd == 0){
                    hourEnd = 0;
                }
                if(minEnd < 5){
                    minEnd = 0;
                }else{
                    minEnd = Math.round(minEnd/5);
                }
                selectedTimeRequest(hourStart,minStart);
                vez++;
            }else if(vez == 1){
                selectedTimeRequest(hourEnd,minEnd);
                vez++;
            }else {*/
            mode = 4;
            vez = 0;
            updateStudyMode(0);
            //}
        }else{
            String[] tokens = data.split("#");
            //Log.i(TAG,"test");
            try {
                double x = Double.parseDouble(tokens[0]);
                double z = Double.parseDouble(tokens[1]);
                _last_acc_x = x;            // updatre the global variables to be used elsewhere in the code
                _last_acc_y = z;
                //Log.i(TAG,"got data from watch x "+x+","+z);
            } catch (NumberFormatException e) {
                Log.e(TAG, "format exception data " + data);
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Google API Client was connected");
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                        for (Node node : nodes.getNodes()) {
                            _wear = node;
                            toast(getApplicationContext(), "Connected to `"+node.getDisplayName()+"`!");
                            Log.i(TAG,"Connected to `"+node.getDisplayName()+"`!");
                        }

                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection to Google API client was failed");
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
        }if(_aggregators!=null){
            for(DataAggregator agg: _aggregators){
                if(agg!= null)
                    agg.stopAgregator();
                Log.i(TAG,"stopoing aggregators");
            }
        }
    }

    private synchronized void selectedTimeRequest(int hour, int min){
        HttpRequest showTime;
        showTime = new HttpRequest(BASE_URL + "/plug/SelectedTime/"+ hour+"-"+min, getApplicationContext(),_queue);
        try{
            showTime.start();
            // showTime.join();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

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
        new StartUp(PLUGS_URL).start();
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

    //Sends Message Back to the watch
    private void sendMessage(String key){
        if (_wear != null && _client!= null && _client.isConnected()) {
            //   Log.d(TAG, "-- " + _client.isConnected());
            Wearable.MessageApi.sendMessage(
                    _client, _wear.getId(), "" + key, null).setResultCallback(

                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {

//                            if (!sendMessageResult.getStatus().isSuccess()) {
//                                Log.e(TAG, "Failed to send message with status code: "
//                                        + sendMessageResult.getStatus().getStatusCode());
//                            }else{
//                                //  Log.i(TAG,"status "+sendMessageResult.getStatus().isSuccess());
//                            }
                        }
                    }
            );
        }
        else
        {
            Log.d("ERROR","Failed to send message!");
        }
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
                    _simuView.setColor(i,"#"+red+green+blue);
                    int red = Integer.parseInt(obj.getString("red"));
                    int green =  Integer.parseInt(obj.getString("green"));
                    int blue =  Integer.parseInt(obj.getString("blue"));
                    String hex = String.format("#%02x%02x%02x",red,green,blue);
                    _simuView.setColor(i,hex);
                    System.out.println("cor:"+"#"+red+green+blue);
                    _plug_names.add("3");*/
                    // Log.i(TAG,obj.toString());
                    _plug_names.add(obj.getString("name").substring(0, obj.getString("name").indexOf(".")).replace("plug", ""));
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

    private void initCorrelationHandlers(int selectedPlug){
        try{
            //find the new targets
            Log.i(TAG,"Finding new targets");
            HttpRequest novo = new HttpRequest(BASE_URL+"/plug/"+_plug_selected, getApplicationContext(),_queue);
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            activeLeds = new JSONArray(data);
            Log.e(TAG,"Active Leds: "+activeLeds.toString());
            _devices_count = json_array.length();
            Log.i(TAG,"Devices count "+_devices_count);
            for (int i = 0; i < _devices_count; i++) {
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
                    if ((int) (obj.get("red")) == 255)
                        _target = i;

                } catch (JSONException e) {
                    Log.wtf(TAG, " não devia dar prob aqui");
                }
            }
            // creates clean arrays for the new correlations;
            Log.i(TAG,"Creating new arrays for "+_devices_count+" targets");
            _acc_data           = new double[_devices_count][2][WINDOW_SIZE];
            _plug_target_data   = new double[_devices_count][2][WINDOW_SIZE];
            _plug_data_indexes  = new int[_devices_count];

            Log.i(TAG,"TARGET: "+_target);
            _started            = true;
            // creates handlers and aggregators for everything
            _handlers = new ArrayList<>();
            _aggregators = new ArrayList<>();

            for(int i=0;i<_devices_count;i++){
                Log.i(TAG,"Creating handler and aggregator for "+i);
                _handlers.add(new PlugMotionHandler(getApplicationContext(),(int)_simulationSpeed,i,BASE_URL+"/plug/"+_plug_names.get(selectedPlug),_queue));
                _aggregators.add(new DataAggregator(i,_simulationSpeed));
            }

            for(int i=0;i<_devices_count;i++) {
                _handlers.get(i).start();
                _aggregators.get(i).start();
            }


            Thread.sleep(2000);

            _corrHandler = new CorrelationHandler();
            _correlationRunning=true;
            _corrHandler.start();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateStudyMode(int index){
        Log.i(TAG," Updating Study");
        if(mode==0){
            mode=1;
            try {
                _started = false;
                // selects the plug
                _plug_selected = _plug_names.get(index);
                HttpRequest select_plug = new HttpRequest(BASE_URL+"/plug/"+_plug_selected+"/selected/", getApplicationContext(),_queue);
                select_plug.start();
                // select_plug.join();
                Thread.sleep(500);

                initCorrelationHandlers(index);



            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else if(mode==1){
            try {
                relay = relay == 0 ? 1 : 0;
                JSONObject selected_led = (JSONObject) activeLeds.get(index);
                int blue = Integer.parseInt(selected_led.getString("blue"));
                int red = Integer.parseInt(selected_led.getString("red"));
                int green = Integer.parseInt(selected_led.getString("green"));
                if (blue == 255) {
                    if (red == 0)
                        Log.i(TAG, "TURNING ON");
                    else
                        Log.i(TAG, "TURNIN OFF");

                    // turn on or off the plug relay to turn on/off the appliances
                    HttpRequest select_plug = new HttpRequest(BASE_URL+"/plug/"+_plug_names.get(index)+"/relay/"+relay, getApplicationContext(),_queue);
                    select_plug.start();
                    select_plug.join();

                    //turn the leds off once we stop interacting
                    HttpRequest turn_off_stuff  = new HttpRequest(BASE_URL+"/plug/"+_plug_names.get(index)+"/stopLeds/", getApplicationContext(),_queue);
                    turn_off_stuff.start();
                    turn_off_stuff.join();
                    _correlationRunning = false;
                    stopServices();
                    mode=0;
                    sendMessage("Device reboot");

                } else if (green == 0){
                    Log.i(TAG,"MENU MODE");
                    mode=3;
                    // init = new InitAvailablePlugs();
                    //init.start();
                    //init.join();
                    //Thread.sleep(50);
                    HttpRequest select_plug = new HttpRequest(BASE_URL+"/plug/"+"/Demo3/"+"2", getApplicationContext(),_queue);
                    select_plug.start();
                    select_plug.join();
                    Thread.sleep(500);
                    //find the new targets
                    Log.i(TAG,"Finding new targets");
                    HttpRequest novo = new HttpRequest(BASE_URL+"/plug/"+_plug_selected, getApplicationContext(),_queue);
                    novo.start();
                    novo.join();
                    String data = novo.getData();
                    activeLeds = new JSONArray(data);
                    Log.e(TAG,"Active Leds: "+activeLeds.toString());
                    Message msg = Message.obtain();
                    msg.arg1=6;
                    _ui_handler.sendMessage(msg);

                }

            } catch (JSONException e){
                e.printStackTrace();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else if(mode==3){
            try {
                Log.i(TAG,"CORRELATING MENU "+activeLeds.get(index).toString());
                if(((JSONObject)activeLeds.get(index)).getInt("red")==0) {
                    sendMessage("Device start" + "-" + "Desk Lamp");
                }else{
                    sendMessage("Device start"+"-"+"Kettle");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else if(mode == 4){
            try {
                HttpRequest CrazyLights = new HttpRequest(BASE_URL + "/plug/ScheduleMode", getApplicationContext(), _queue);
                CrazyLights.start();
                //CrazyLights.join();
                new InitAvailablePlugs(InitAvailablePlugs.UPDATE).start();

                Log.d(TAG,"Initialized crazy lights");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startUpDemo(){
        new InitAvailablePlugs(InitAvailablePlugs.FIRST_STARTUP).start();
        new StartUp(PLUGS_URL).start();

    }

    private void scheduleTimeOff(int sleep) {

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                HttpRequest turn_off_stuff = new HttpRequest(BASE_URL + "/plug/" + _plug_selected + "/stopLeds/", getApplicationContext(), _queue);
                turn_off_stuff.start();
                _correlationRunning = false;
                stopServices();
                mode = 0;
                sendMessage("Device reboot");
            }
        }, sleep*1000);

    }

    private void removeScheduleTimeOff(int plug){
        for(int i=0; i<_alarms.size();i++){
            if(_alarms.get(i).getPlug()==plug)
                _alarms.get(i).setActive(false);

        }
    }

    public synchronized void TurnOffAndRemove(int j){
        if (_alarms.get(j).isActive()) {
            try {
                HttpRequest selected_request = new HttpRequest(BASE_URL + "/plug/" + _plug_names.get(j) + "/relay/0", getApplicationContext(), _queue);
                //HttpRequest enviaNome = new HttpRequest(BASE_URL + "plug/RemovePerson/"+_plug_names.get(j),getApplicationContext(),_queue);
                selected_request.start();
                //enviaNome.start();
                //selected_request.join();
                //enviaNome.join();
                //Log.d("PLUGS","Plug '"+PLUG_NAMES[Integer.parseInt(_plug_names.get(j)) % PLUG_NAMES.length]+"' has been turned off by "+Device_Name);
                relay = 0;
                //notify("Wattapp","Plug '"+PLUG_NAMES[Integer.parseInt(_plug_names.get(j)) % PLUG_NAMES.length]+"' has been turned off");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        // ConsultUsers();
    }

    public synchronized void TurnOnAndAdd(int j){
        if (_alarms.get(j).isActive()) {
            try {
                HttpRequest selected_request = new HttpRequest(BASE_URL + "/plug/" + _plug_names.get(j) + "/relay/1", getApplicationContext(), _queue);
                //HttpRequest enviaNome = new HttpRequest(BASE_URL + "plug/InsertNewPerson/"+Device_Name+"-"+_plug_names.get(j),getApplicationContext(),_queue);
                selected_request.start();
                //enviaNome.start();
                selected_request.join();
                //enviaNome.join();
                //Log.d("PLUGS","Plug '"+PLUG_NAMES[Integer.parseInt(_plug_names.get(j)) % PLUG_NAMES.length]+"' has been turned on by "+Device_Name);
                relay = 1;
                //sendMessage("Plug start"+"-"+PLUG_NAMES[Integer.parseInt(_plug_names.get(j)) % PLUG_NAMES.length]);
                //notify("Wattapp","Plug '"+PLUG_NAMES[Integer.parseInt(_plug_names.get(j)) % PLUG_NAMES.length]+"' has been turned on");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //ConsultUsers();
    }

    private void createSchedule(int index){
        Log.i(TAG,"Creating schedule");
        Alarm novo = new Alarm(HourScheduleStart, MinutesScheduleStart,()->
        {
            TurnOnAndAdd(index);
            new Alarm(HourScheduleEnd, MinutesScheduleEnd, ()->
            {
                TurnOffAndRemove(index);
            },false,index).activate();
        },false,index);
        _alarms.add(novo);
        _alarms.get(0).activate();
    }

    private class StartUp extends Thread{

        private String _url;

        public StartUp(String url){
            this._url = url;
        }

        @Override
        public void run(){
            try {

                Log.e(TAG, "Starting up");

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

                //   Thread.sleep(1000);

                //   refresh = new HttpRequest(URL_PLUG+"/start/6", getApplicationContext(),_queue);
                //   refresh.start();
                //   refresh.join();

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
            }
            Log.i(TAG,"Agragattor dying");
        }

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
            Log.i(TAG,"Starting to run correlation "+_correlationRunning+" devices targets: "+_devices_count);
            _correlations       = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];
            long _lastCorr      = 0;
            while(_correlationRunning){
                if(_countingTime)       // check if we are counting time in the current matching process
                    checkRunningTime();

                //Log.i(TAG," problema aqui "+_plug_data_indexes[_target]+" ou aqui "+WINDOW_SIZE);
                //Log.i(TAG,"problema aqui "+_correlations[0].length+"  "+_correlations[1].length+"   "+_plug_data_indexes.length+"   "+_acc_data.length+" "+this.toString());
                for(int i=0;(i<_devices_count) && (_plug_data_indexes[_target] == WINDOW_SIZE) && _correlationRunning;i++){
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                }
                for(int i=0;i<_devices_count;i++){
                    //Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]+" targets "+_devices_count);

                    if ((_correlations[0][i] > 0.8 && _correlations[0][i] < 0.9999) && (_correlations[1][i]>0.8 &&  _correlations[1][i]<0.9999)) {  // sometimes at the start we get 1.0 we want to avoid that
                        if(!_updating)
                            updateCorrelations(i,_correlations_count);
                        if(_correlations_count[i]==3) {
                            _correlations_count[i] = 0;
                            Log.i("Positive Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                            if(System.currentTimeMillis()>_lastCorr+3000) {
                                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                                sendMessage("Corr" + "-" + "Positive");
                                _lastCorr = System.currentTimeMillis();
                                if (mode == 0) {
                                    _correlationRunning = false;
                                    stopServices();
                                    updateStudyMode(i);
                                    return;
                                } else if (mode == 1) {
                                    updateStudyMode(i);
                                } else if (mode == 3) {
                                    updateStudyMode(i);
                                } else if (mode == 4) {
                                    mode = 0;
                                    _correlationRunning = false;
                                    createSchedule(i);
                                    stopServices();
                                    updateStudyMode(i);
                                    return;
                                }
                            }


                            //                          selected_request.join();
                            // Thread.sleep(200);

                            //  } catch (InterruptedException e) {
                            //      e.printStackTrace();
                            //  }

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
            Log.i(TAG,"Correlation handler dying");
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
            }else if(msg.arg1==5) {
                _trial_field.setText("Trial " + msg.arg2 + " out of 21");
            }else if(msg.arg1==6){
                scheduleTimeOff(10);
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

    private class InitAvailablePlugs extends Thread{

        private static final int FIRST_STARTUP = 0;
        private static final int UPDATE = 1;

        private int _mode;

        public InitAvailablePlugs(int mode){
            this._mode = mode;
        }

        @Override
        public void run() {

            //Testes
            try {
                if(this._mode == FIRST_STARTUP){
                    HttpRequest select_plug = new HttpRequest(BASE_URL+"/plug/"+"start/"+"2", getApplicationContext(),_queue);
                    select_plug.start();
                    select_plug.join();
                }else if(this._mode == UPDATE){
                    initCorrelationHandlers(_target);
                }
            /*  HttpRequest showTime;
              showTime = new HttpRequest(BASE_URL + "/plug/SelectedTime/" + 1 + "-" + 5, getApplicationContext(), _queue);
              showTime.start();
              showTime.join();
              HttpRequest CrazyLights = new HttpRequest(BASE_URL + "/plug/ScheduleMode", getApplicationContext(), _queue);
              CrazyLights.start();
              CrazyLights.join();*/
            }catch(Exception e){
                e.printStackTrace();
            }
            //

        }}
}
