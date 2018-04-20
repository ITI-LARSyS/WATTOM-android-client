package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.filipe.socketcontroller.motion.PlugMotionHandler;
import com.example.filipe.socketcontroller.util.Alarm;
import com.example.filipe.socketcontroller.util.HttpRequest;
import com.example.filipe.socketcontroller.util.UI;
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

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.filipe.socketcontroller.util.UI.toast;

public class MainActivity extends AppCompatActivity implements  MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final static String TAG = "DeviceSelection";
    private static final int WINDOW_SIZE = 40;  // terá qde ser 80

    private PowerManager.WakeLock cpuWakeLock;
    private boolean inStudy = false;
    private boolean paused = false;

    // communication with the watch
    private GoogleApiClient _client;

    //View stuff
    private TextView _counter;
    private EditText _pId;

    //private UI_Handler _ui_handler = new UI_Handler();

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
    private Node _wear;

    //plugs url for notifying good correlation
    private static String BASE_URL = "http://0.0.0.0:3000";
    //  private final static String BASE_URL = "http://192.168.1.7:3000";
    private final static String EnergyData = "http://aveiro.m-iti.org/sinais_energy_production/services/today_production_request.php?date=";

    private static String PLUGS_URL =BASE_URL+"/plug/";
    private static String SELECTED_URL = PLUGS_URL +"%/selected/";
    private static String PLUG_URL = BASE_URL+"/plug/%";
    private int _plug_selected = 0;
    private static String ChangeEnergyURL = PLUGS_URL+"energy/";
    private int renewableEnergy = 0;
    private int vez = 0;

    //Pedro stuff, for schedule
    private int hourStart,minStart,hourEnd,minEnd;
    private int HourScheduleStart, MinutesScheduleStart,HourScheduleEnd, MinutesScheduleEnd;


    //handlers and receivers for the targets
    private ArrayList<PlugMotionHandler> _handlers;
    //private ArrayList<IntentFilter> _filters;
    private ArrayList<String> _plug_names;
    private ArrayList<DataAggregator> _aggregators;

    //devices
    private int _devices_count;


    //target
    private int _target[];

    //for debug
    private boolean _debug_thread = false;
    private SimulationView _simuView;

    //for the study
    //private int _participant;
    //String _studyResult = "Participant,Target_selection,Acquisition_time,Angle,Pointing,Timestamp";
    private long _aquisition_time=0;
    private int _angle=0;
    private boolean _target_selection = true;
    private String _pointing =  "towards";
    private long _match_limit=7000;
    private boolean _countingTime=false;
    //    private UpdateStudy _updateStudyWorker;
//    int _plug     = 3;
//    int _trialCount = 0;
//    int _angleCount = 0;
//    final int _angles[]={1, 2, 3, 4, 5, 6};
    private TextView _instructions;
    private TextView _condition;
    private TextView _trial_field;

    //stats



    //On/Off
    private boolean IsOn = false;

    //Dados energia renovavel
    private Timer hourlyTimer;
    private Timer minTimer;
    private Timer PowerTimer;
    private int index;

    private boolean isScheduleMode;
    private String Device_Name;

    private int indexLuz = -1, indexChaleira = -1;

    private TimerTask powerTask, energyTask, personTask;


    private static final int CORR_OFF = android.R.drawable.presence_offline;
    private static final int CORR_NONE = android.R.drawable.presence_invisible;
    private static final int CORR_GOOD = android.R.drawable.presence_online;
    private static final int CORR_BAD = android.R.drawable.presence_busy;
    private static final int CORR_NORMAL = android.R.drawable.presence_away;

    //Ao iniciar a aplicacao
    // - Atribui cada elemento da interface uma variavel
    // - coloca a taba do debug escondida
    // - coloca um Listener no botao do debug
    // - Configura a ligacao com o relogio
    // - Cria a fila de pedidos
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        askIP();

        setContentView(R.layout.activity_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       /* _counter = (TextView) findViewById(R.id.counter);
        _pId = (EditText) findViewById(R.id.participant_id);
        _simuView = (SimulationView) findViewById(R.id.simulation_view);
        _instructions = (TextView) findViewById(R.id.instructions_field);
        _condition = (TextView) findViewById(R.id.condition_field);
        _trial_field = (TextView) findViewById(R.id.trial_field); */
        hourlyTimer = new Timer();
        minTimer = new Timer();
        PowerTimer = new Timer();
//        IsNotFirstTime = 0;
        isScheduleMode = false;

        //_simuView.setVisibility(View.GONE);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
       /* final View debug_view = findViewById(R.id.debug_view);
        debug_view.setVisibility(View.GONE); */

        Device_Name = Settings.Secure.getString(getContentResolver(), "bluetooth_name");

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();

        _client.connect();
        Wearable.MessageApi.addListener(_client, this);
        _queue = Volley.newRequestQueue(getApplicationContext());

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        initTasks();
    }

    // Ao receber uma mensagem:
    // - Separa os valores de x e z como ultimos valores recebidos;
    // - Indica que já iniciou
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.i(TAG,messageEvent.toString());
        String merda = messageEvent.getPath();
        String data = merda.replace("acc", "");
        String [] horas = data.split("/");
        if(horas.length == 2){
            if(vez == 0){
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
                SelectedTime(hourStart,minStart);
                vez++;
            }else if(vez == 1){
                SelectedTime(hourEnd,minEnd);
                vez++;
            }else {
                HttpRequest CrazyLights = new HttpRequest(BASE_URL + "plug/ScheduleMode", getApplicationContext(), _queue);
                try {
                    CrazyLights.start();
                    //CrazyLights.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isScheduleMode = true;
                vez = 0;
            }
        }else{
            if(!_started)
            {
                notify("Wattapp (Sensor)","Started receiving coordinates!");
                Log.d("SENSOR","Started receiving coordinates!");
            }
            _started = true;

            String[] tokens = data.split("#");

            try {
                double x = Double.parseDouble(tokens[0]);
                double z = Double.parseDouble(tokens[1])*-1;
                _last_acc_x = x;            // updatre the global variables to be used elsewhere in the code
                _last_acc_y = z;

                Log.i(TAG,"got data from watch x "+x+","+z);
                Log.d("SENSOR","x:"+x+" z:"+z);
                //toast("got data from watch x "+x+","+z);
            } catch (NumberFormatException e) {
                //Log.e(TAG, "format exception data " + data);
            }
        }
    }

    //Vai colocando os valores no array ate encher e depois vai substituindo o mais antigo
    private void push(int index, double[][] data,double x, double y){
        if(index < WINDOW_SIZE){
            data[0][index] = x;
            data[1][index] = y;
        }else{
            int i;
            for(i=1; i< WINDOW_SIZE; i++){
                data[0][i-1] = data[0][i];
                data[1][i-1] = data[1][i];
            }
            data[0][i-1] = x;
            data[1][i-1] = y;
        }
    }

    //Termina os servicos em todos os PlugMotionHandler
    private void stopServices(){
        if(_handlers!=null){
            for(PlugMotionHandler handler : _handlers) {
                if (handler != null)
                    handler.stopSimulation();
                //Log.i(TAG, "stoping simulation");
            }
        }
    }

    // Quando a app ou vai ser destruida/falta de memoria/foreground
    // - Remove o Listener _cliente(relogio)
    // - retira todos os handlers
    // - Guarda o ficheiro
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cpuWakeLock.isHeld()) cpuWakeLock.release();
        sendMessage("STOP");
        Wearable.MessageApi.removeListener(_client, this);
        //Log.wtf(TAG, " wtf called from main activity");
        stopServices();
        _correlationRunning = false;
        //saveFile();
        _client.disconnect();
    }

    //Guarda todos os dados do utilizador
//    private boolean saveFile(){
//        String root = Environment.getExternalStorageDirectory().toString();
//        File myDir = new File(root + "/participants_data"); // creates the dir
//        myDir.mkdirs();                                     // builds the dir, (only returns true once)
//        String fname = _participant+"__"+System.currentTimeMillis()+".txt"; // file name with the pId plus the current tms
//        File file = new File (myDir, fname);
//        try {
//            FileOutputStream out = new FileOutputStream(file);
//            out.write(_studyResult.getBytes());
//            out.flush();
//            out.close();
//            return true;
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//    }

    public void onConnected(@Nullable Bundle bundle) {
        //Log.d(TAG, "Google API Client was connected");
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                        for (Node node : nodes.getNodes()) {
                            _wear = node;
                            toast(getApplicationContext(), "Connected to `"+node.getDisplayName()+"`!");
                        }
                        //Log.i(TAG,"watch connected");
                    }
                });
    }

    /*
        Handlers for the user interaction most of these are for the debug purposes
     */

    //Quando carrega no botao do debug
//    public void handleDebugClick(View v){
//        if(v.getId() == R.id.debug_btn)
//            _debug_thread = !_debug_thread;
//        else if(v.getId()==R.id.start_thread){
//            new PrintCurrentData().start();
//            _debug_thread= false;
//        }
//    }

    //Quando carrega no startStudy
    // - Retira o id do participante
    // - Retira o angulo inicial
    // - Retira a condicao
    // - O URL especifico
    // E inicia a thread StartUp
    public void handleStartStudyClick(View v)
    {
        start();
        ((TextView)v).setText(R.string.stop_study);
        v.setOnClickListener((x)-> handleStopStudyClick(x));
    }

    public void handleStopStudyClick(View v)
    {
        toast(getApplicationContext(),"Study ended!");
        startActivity(new Intent(this,MainActivity.class));
        this.finish();
      //  ((TextView)v).setText(R.string.start_study);
      //  v.setOnClickListener((x)-> handleStartStudyClick(x));
    }

    public void start()
    {
        new Thread(()->
        {
            //_participant = Integer.parseInt(_pId.getText().toString());
            //_angle       = 0;
            //_pointing    = _condition.getText().toString();
            //SELECTED_URL =  SELECTED_URL.replace("%",_plug+"");

            inStudy = true;
            sendMessage("START");
            toast(getApplicationContext(),"Study started!");

            IsOn = false;

            fakeMessages();

            new Timer().schedule(energyTask,10,1000*60*15);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            new StartUp(PLUGS_URL).start();
            new Timer().schedule(powerTask,10,1000*60);
            new Timer().schedule(personTask,10,1000*60);
        }).start();
    }

    public void stop()
    {
        inStudy = false;
        sendMessage("STOP");
        toast(getApplicationContext(),"Study ended!");
        stopServices();
        _correlationRunning = false;
        startActivity(new Intent(this,MainActivity.class));
        this.finish();
    }

    // Obriga um handler a fazer um forceUpdate()
//    public void handleUpdateClick(View v){
//        new AsyncTask(){
//            @Override
//            protected Object doInBackground(Object[] objects) {
//                _handlers.get(_target[0]).forceUpdate();
//                return null;
//            }
//        }.execute();
//    }

    //Acho que já deve usar outra
//    public void handleTestClick(View v){
//        _participant = Integer.parseInt(_pId.getText().toString());
//        _angle       = 0;
//        _pointing    = "towards";
//    }

    //Faz u updateTarget do _corrHandler
//    public void handleRefreshClick(View v){
//        _corrHandler.updateTarget(0,false);
//    }

    //Coloca o array todo a 0
//    private void cleanUp(){
//        _acc_data = new double[_devices_count][2][WINDOW_SIZE];
//        _plug_target_data = new double[_devices_count][2][WINDOW_SIZE];
//
//        for(int i=0;i<_devices_count;i++){
//            for(int j=0;j<WINDOW_SIZE;j++){
//                _acc_data[i][0][j]=0;
//                _acc_data[i][1][j]=0;
//            }
//        }
//
//        for(int i=0;i<_devices_count;i++){
//            for(int j=0;j<WINDOW_SIZE;j++){
//                _plug_target_data[i][0][j]=0;
//                _plug_target_data[i][1][j]=0;
//            }
//        }
//    }

    //- Inicializa o correlationHandler
    //- Cria um arrayList de _handlers e de _aggreagators
    //- Inicializa o simulationSpeed e os array dos dados dos leds e do relogio
    // - Para cada device adiciona a cada array list o PlugMotionHandler e o DataAggrgator
    // - E iniciam as tarefas de cada um dos dispositivos
    // - Comeca a coordenar o movimento e a guardar os dados
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



    // com a conexao suspensa
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google API client was suspended");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(cpuWakeLock.isHeld()) cpuWakeLock.release();
        paused = false;

        //ConsultUsers();
    }

    //com falha na conexao
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection to Google API client was failed");
        notify("Wattapp (Connection)","Connection Failed! ("+connectionResult.toString()+")");
    }



/*
    //Inicializa os vetores com os indices consoante o numero de devices
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
    //Envia um pedido HTTTP recebe dados e adiciona as plugs que existir
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
    }*/


    //conecta com o relogio
    @Override
    public void onStart()
    {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        _client.connect();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        paused = true;
        if(inStudy) cpuWakeLock.acquire();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        paused = true;
    }

    private void SelectedTime(int hour, int min){
        HttpRequest showTime;
        showTime = new HttpRequest(BASE_URL + "plug/SelectedTime/"+ hour+"-"+min, getApplicationContext(),_queue);
        try{
            showTime.start();
            //showStartTime.join();
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    // Envia um HTTP Request para receber os dados da plug, inicia os dados para essa plug
    // Inicia os CorrelationHandlers
    // Forca o update em todos os _handlers
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
                getPlugsData();
                Thread.sleep(0);
                firstStartup(_url);
                Thread.sleep(0);
                //_correlationRunning = true;
                _started = true;
                _updating = false;
                _corrHandler.start();

                new RefreshData().start();
                _aquisition_time = System.currentTimeMillis()+5000;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for(int i = 0; i < _plug_names.size();i++){
                TurnOffAndRemove(i);
            }
        }
    }

    private class RefreshData extends Thread{

        @Override
        public void run() {
            try{
                HttpRequest _request;
                _request = new HttpRequest(BASE_URL + "plug", getApplicationContext() ,_queue);
                _request.start();
                //Log.i(TAG,"--- RUNNING COLOR REQUEST : target "+_led_target+" ---");
                _request.join();
                String data = _request.getData();
                // [{"position":2,"velocity":300,"orientation":1,"red":255,"green":255,"blue":255,"name":"plug3.local"},{"position":2,"velocity":300,"orientation":1,"red":0,"green":0,"blue":255,"name":"plug3.local"}]]
                // luz - 255,255,255 , chaleira 0,0,255
                //luz = 0, chaleira =1

                JSONArray json_array = new JSONArray(data);
                for(int i=0;i<_handlers.size();i++) {
                    JSONObject json_message = json_array.getJSONObject(i);
                    if(json_message.getInt("red") == 0)
                    {
                        indexLuz = i;
                    }
                    else
                    {
                        indexChaleira = i;
                    }
                    _handlers.get(i).handlePlugMessage(json_message);
                    Thread.sleep(0);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    //
//    private class RefreshTarget extends Thread{
//
//        private final static String URL_PLUG =BASE_URL+"/plug/";
//
//        @Override
//        public void run(){
//
//            try {
//                _updating = true;
//                _started = false;
//                _correlationRunning = false;
//
//                Message msg = Message.obtain();
//                msg.arg1 = 3;
//                _ui_handler.sendMessage(msg);
//
//                // Thread.sleep(1000);
//
//                //   refresh = new HttpRequest(URL_PLUG+"/start/6", getApplicationContext(),_queue);
//                // refresh.start();
//                //    refresh.join();
//
//                Thread.sleep(1000);
//
//                HttpRequest refresh = new HttpRequest(URL_PLUG+_plug+"/refresh/", getApplicationContext(),_queue);
//                refresh.start();
//                refresh.join();
//
//                Thread.sleep(300);
//
//                for(PlugMotionHandler tes: _handlers) {
//                    Log.i(TAG,"forcing the update of the handlers");
//                    tes.forceUpdate();
//                    Thread.sleep(60);
//                }
//
//                Thread.sleep(100);
//
//                updateTarget(URL_PLUG+_plug);
//
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

//    private class PrintCurrentData extends Thread{
//
//        public void run(){
//
//            while(true){
//
//                if(_debug_thread){
//                    try {
//                        Log.i("TARGET POS Thread","current led x pos "+_handlers.get(_target[0]).getPosition()[0]);  //.getPosition()[0]+","+_handlers.get(_led_target).getPosition()[1]);
//                        sleep(200);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }

    //Classe responsavel por ir atualizando os valores recebidos no vetor
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

                    //if((_led_target ==_target[0])) {     // used to print the simulation on the screen
                        for(int i = 0; i < _devices_count; i++)
                        {
//                           _simuView.setCoords(i,(float) _handlers.get(i).getPosition()[0], (float) _handlers.get(i).getPosition()[1]);
                            //_simuView.setCoords((float) _handlers.get(_led_target).getPosition()[0], (float) _handlers.get(_led_target).getPosition()[1],(float)_last_acc_x,(float)_last_acc_y);
                        }
                    //}
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

    //
//    private class UpdateStudy extends Thread{
//
//        private int _countDown;
//
//        public UpdateStudy(int countDown){
//            _countDown = countDown;
//        }
//
//        @Override
//        public void run(){
//            try {
//                // refreshes the view
//                RefreshTarget thread = new RefreshTarget();
//                thread.start();
//                thread.join();
//                // count down
//                for(int i=_countDown;i>=1;i--) {
//                    Message msg = Message.obtain();
//                    msg.arg1 = 1;
//                    msg.arg2 = i;
//                    _ui_handler.sendMessage(msg);
//                    Thread.sleep(1000);
//                    Log.i(TAG,"Sleeping "+i+" "+_countDown);
//                    // _handlers.get(0).forceUpdate();
//                }
//                //meio hack
//
//                // Start!
//                Message msg = Message.obtain();
//                msg.arg1=2;
//                _ui_handler.sendMessage(msg);
//                // Beep to start
//                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
//                toneGen1.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE,150);
//                // reset the vars that count the aquisition time
//                _handlers.get(0).forceUpdate();
//
//                _countingTime    = true;
//                _updating        = false;
//                _aquisition_time = System.currentTimeMillis();
//
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    private class CorrelationHandler extends Thread{

        double _correlations[][];
        int _correlations_count[];
        private Thread _togglers[];

        private void updateCorrelations(int index, int[] correlations){
            int temp = correlations[index]+1;

            for(int i=0;i<correlations.length;i++)
                correlations[i]=0;

            correlations[index] = temp;
        }

        //
        @Override
        public void run(){

            _correlationRunning = true;
            _correlations       = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];
            _togglers = new Thread[_devices_count];
            Context ctx = getApplicationContext();
            Activity a = MainActivity.this;

            ImageView corrIcons[] = new ImageView[_devices_count];
            LinearLayout container = (LinearLayout) a.findViewById(R.id.corrstates);
            for (int i = 0; i < _devices_count ; i++)
            {
                corrIcons[i] = new ImageView(ctx);
                corrIcons[i].setImageResource(CORR_NONE);
                corrIcons[i].setLayoutParams(new LinearLayout.LayoutParams(50,50));
                int finalI = i;
                runOnUiThread(()->container.addView(corrIcons[finalI]));
            }

            while(_correlationRunning){
                //if(_countingTime)     // check if we are counting time in the current matching process
                //   checkRunningTime();
                /**
                 *  -0.1 to 0.1 indicates no linear correlation
                 -0.5 to -0.1 or 0.1 to 0.5 indicates a “weak” correlation
                 -1 to -0.5 or 0.5 to 1 indicates a “strong” correlation
                 */
                for(int i=0;(i<_devices_count) && (_plug_data_indexes[_target[i]] == WINDOW_SIZE);i++){
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                    //Log.d("CORR0",""+_correlations[0][i]);
                    //Log.d("CORR1",""+_correlations[1][i]);
                }
                for(int i=0;i<_devices_count;i++){
                    int current = i;
                    //Log.i("Corr","correlation "+ i +" "+_correlations[0][i]+","+_correlations[1][i]);
                    if (((_correlations[0][i] >= 0.8 && _correlations[0][i] < 1) && (_correlations[1][i]>=0.8 &&  _correlations[1][i]<1)))
                    {  // sometimes at the start we get 1.0 we want to avoid that

                        runOnUiThread(()->corrIcons[current].setImageResource(CORR_GOOD));

                        if(!_updating)
                            updateCorrelations(i,_correlations_count);
                        // Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                        if(_correlations_count[i] == 3) {
                            _correlations_count[i] = 0;
                            //if (i == _target[i]){
                            _target_selection = true;
                            // Beep to show its the correct match
                           // ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                            //toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                            if(_togglers[i] == null || !_togglers[i].isAlive())
                            {
                                final  int finalI = i; // target correlated
                                _togglers[i] = new Thread(() ->
                                {
                                    updateTarget(finalI,true);
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                });
                                _togglers[i].start();
                            }
                            //}else{
//                                Log.i(TAG,"Wrong correlation");
//                                _target_selection = false;
//                                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
//                                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
//                                updateTarget(i,true);
                            //}
                        }
                    }
                    else  if ( _correlations[0][i] < 0 || _correlations[1][i] < 0)
                    {
                        runOnUiThread(()->corrIcons[current].setImageResource(CORR_BAD));
                    }
                    else
                    {
                        runOnUiThread(()->corrIcons[current].setImageResource(CORR_NORMAL));
                    }
                }

                try {
                    Thread.sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

//        public boolean checkRunningTime(){
//
//            if((System.currentTimeMillis()-_aquisition_time)>_match_limit){
//                _target_selection = false;
//                ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
//                toneGen1.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,150);
//                Log.wtf("RUNNING TIME","... OVERTIME ...");
//                if(_countingTime)       // make sure we do not do this while other updates are going on
//                    updateTarget(_target,false);
//                return false;
//            }else
//                return true;
//        }

        private synchronized void updateTarget(final int led_target, boolean match){
//            _countingTime     = false;
//            _aquisition_time = System.currentTimeMillis()-_aquisition_time;
//            _studyResult = _studyResult +"\n"+_participant+","+_target_selection+","+_aquisition_time+","+_angles[_angleCount]+","+_pointing+","+(System.currentTimeMillis());
            //Log.wtf("Corr", "aq time= "+_aquisition_time);
                for(int j = 0; j<_devices_count;j++){
                    if(match && led_target == _target[j]){
                        index = j;
                        if(isScheduleMode){
                            TurnOffAndRemove(j);
                            ChangeColorByEnergy(renewableEnergy);
                            isScheduleMode = false;
                            new Alarm(HourScheduleStart, MinutesScheduleStart,()->
                            {
                                TurnOnAndAdd(index);
                                new Alarm(HourScheduleEnd, MinutesScheduleEnd, ()->
                                {
                                    TurnOffAndRemove(index);
                                },false).activate();
                            },false).activate();
                        }else{
                            if(IsOn){
                                TurnOffAndRemove(j);
                            }else{
                                TurnOnAndAdd(j);
                            }
                            //HttpRequest selected_request = new HttpRequest(SELECTED_URL + "" + led_target, getApplicationContext(),_queue);
                            // Log.e(TAG, "-----   running "+SELECTED_URL + "" + led_target+" request  ------");
                        }
                        return;
                    }
                }
            }
    }

    public void ChangeColorByEnergy(int percent){
        HttpRequest novo = new HttpRequest(ChangeEnergyURL+percent, getApplicationContext() ,_queue);
        try{
            novo.start();
            //novo.join();
        }catch (Exception e){
            e.printStackTrace();
        }
        new RefreshData().start();
    }

    public void TurnOffAndRemove(int j){
        try{
            HttpRequest selected_request = new HttpRequest(BASE_URL + "plug/"+_plug_names.get(j)+"/relay/1", getApplicationContext(),_queue);
            HttpRequest enviaNome = new HttpRequest(BASE_URL + "plug/RemovePerson/"+_plug_names.get(j),getApplicationContext(),_queue);
            selected_request.start();
            enviaNome.start();
            selected_request.join();
            enviaNome.join();
            Log.d("PLUGS","plug"+_plug_names.get(j)+".local has been turned off by "+Device_Name);
            IsOn = false;
            notify("Wattapp","plug"+_plug_names.get(j)+".local has been turned off");

        }catch (Exception e){
            e.printStackTrace();
        }
       // ConsultUsers();
    }

    public void TurnOnAndAdd(int j){
        try{
            HttpRequest selected_request = new HttpRequest(BASE_URL + "plug/"+_plug_names.get(j)+"/relay/0", getApplicationContext(),_queue);
            HttpRequest enviaNome = new HttpRequest(BASE_URL + "plug/InsertNewPerson/"+Device_Name+"-"+_plug_names.get(j),getApplicationContext(),_queue);
            selected_request.start();
            enviaNome.start();
            selected_request.join();
            enviaNome.join();
            Log.d("PLUGS","plug"+_plug_names.get(j)+".local has been turned on by "+Device_Name);
            IsOn = true;
            notify("Wattapp","plug"+_plug_names.get(j)+".local has been turned on");
        }catch (Exception e){
            e.printStackTrace();
        }

        // Se tiver mais que um device
        // (vai para o gráfico desse device)
        if(indexLuz != -1 && indexChaleira != -1)
        {
            if(j == indexLuz)
            {
                sendMessage("Device start"+"-"+"luz top");
            }
            else
            {
                if(j == indexChaleira)
                {
                    sendMessage("Device start"+"-"+"chaleira top");
                }
            }
        }

        // Se tiver só um device
        // (vai para o gráfico desse plug)
        else
        {
            sendMessage("Plug start"+"-"+"plug"+_plug_names.get(j)+".local");
        }

       //ConsultUsers();
    }

//    private class UI_Handler extends Handler{
//
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            if (msg.arg1 == 0){
//                _instructions.setText("-");
//            }else if(msg.arg1==1){
//                _counter.setText(msg.arg2+"");
//            }else if(msg.arg1==2){
//                _counter.setText("Start !!");
//            }else if(msg.arg1==3){
//                _counter.setText("Please wait!!");
//            }else if(msg.arg1==4){
//                _instructions.setText("Pease move to the next position");
//            }else if(msg.arg1==5){
//                _trial_field.setText("Trial "+msg.arg2+" out of 21");
//            }else if(msg.arg1==99){
//                _counter.setText("Thank you");
//                _instructions.setText("-");
//            }
//
//        }
//    }


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

  public void demo3(View v)
  {
      new Thread(()->
      {
          HttpRequest demo = new HttpRequest(PLUGS_URL + "Demo3/2", getApplicationContext() ,_queue);
          demo.start();
          try {
              demo.join();
          } catch (InterruptedException e) {
              e.printStackTrace();
          }
          inStudy = true;
          sendMessage("START");
          toast(getApplicationContext(),"Study started!");

          IsOn = false;


          new StartUp(PLUGS_URL).start();


          new Timer().schedule(powerTask,10,1000*60);
          new Timer().schedule(personTask,10,1000*60);
      }).start();
  }

    //Envia um pedido HTTTP recebe dados e adiciona as plugs que existir
    private void getPlugsData(){
        try {
            _plug_names = new ArrayList<>();
            HttpRequest novo = new HttpRequest(BASE_URL + "plug", getApplicationContext());
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            _devices_count = json_array.length();
            _target = new int [_devices_count];

            // Log.i(TAG, "---- AVAILABLE PLUGS ---");
            for(int i=0;i<_devices_count;i++){
                JSONObject obj = (JSONObject) json_array.get(i);
                try {
                    _target[i]=i;
                    _plug_names.add(obj.getString("name").substring(0, obj.getString("name").indexOf(".")).replace("plug", ""));
                    // Log.i(TAG, "plug "+_plug_names.get(i));
                }catch (JSONException e){
            		Log.d("PLUGS","No plugs detected!");
                }
            }
            //Log.i(TAG,"-------");
            //Log.i(TAG,"TARGET: "+_target);
        }
        catch (JSONException e)
        {
            Log.d("PLUGS","No plugs detected!");
            //e.printStackTrace();
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /*private void ConsultUsers(){
        HttpRequest CheckUsers = new HttpRequest(BASE_URL + "plug/Power", getApplicationContext(),_queue);
        try{
            CheckUsers.start();
            CheckUsers.join();
            ArrayList<String> users = new ArrayList<>();
            ArrayList<Float> powers = new ArrayList<>();
            String ArrayIdPower = CheckUsers.getData();
            JSONArray IdPower = new JSONArray(ArrayIdPower);
            for(int i = 0; i < IdPower.length(); i++){
                JSONObject User = (JSONObject) IdPower.get(i);
                users.add(User.get("id").toString());
                powers.add(Float.parseFloat(User.get("power").toString()));
            }
            String message = "Person consumption";
            for(int i = 0; i < users.size(); i++){
                String temp = "-"+users.get(i)+"-"+powers.get(i);
                message += temp;
                Log.d("PERSONS","Consumption of "+users.get(i)+": "+powers.get(i));
            }
            sendMessage(message);
            if(!paused) toast(getApplicationContext(),"Person consumption" + " - " + "Updated data!" );
            else UI.notify(getApplicationContext(),MainActivity.class,"Person consumption","Updated data!");
        }catch (Exception e){
            e.printStackTrace();
        }
    }*/

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
            notify("Wattapp (Connection)","Failed to send a message!");
            Log.d("ERROR","Failed to send message!");
        }
    }

    private void askIP()
    {
        final SharedPreferences prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
        final String oldIP = prefs.getString("IP",null);

        AlertDialog.Builder ask = new AlertDialog.Builder(this);

        ask.setTitle("Wattapp's IP");
        ask.setMessage("(Previous IP: "+oldIP+")");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        ask.setView(input);
        ask.setCancelable(false);

        ask.setPositiveButton("Set new IP", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                String newIP = input.getText().toString();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("IP",newIP);
                editor.apply();
                setIP(newIP);
                toast(getApplicationContext(),"You chose to set new IP ("+newIP+")");
            }
        });
        ask.setNegativeButton("Keep previous IP",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                setIP(oldIP);
                toast(getApplicationContext(),"You chose to keep previous IP ("+oldIP+")");
            }
        });
        ask.show();
    }

    public void setIP(String ip)
    {
        BASE_URL        = "http://"+ip+":3000/";
        PLUGS_URL       = BASE_URL +"plug/";
        PLUG_URL        = PLUGS_URL +"%/";
        SELECTED_URL    = PLUG_URL + "selected/";
        ChangeEnergyURL = PLUGS_URL+"energy/";
    }

    public static String getBaseURL() { return BASE_URL; }

    private void fakeMessages()
    {
        float total = 110;
        float termica = 9;
        float hidrica = 3;
        float eolica = 7;
        float biomassa = 8;
        float foto = 11;

        sendMessage("Energy"
                +"-"+"Não renovável"
                +"-"+(total-termica-hidrica-eolica-biomassa-foto)
                +"-"+"Térmica"
                +"-"+termica
                +"-"+"Hídrica"
                +"-"+hidrica
                +"-"+"Eólica"
                +"-"+eolica
                +"-"+"Biomassa"
                +"-"+biomassa
                +"-"+"Fotovoltaica"
                +"-"+foto
        );
        notify("Energy","Updated data!");

        // falta enviar para o wear (para atualizar o pie chart)
        float percentage = ((termica+hidrica+eolica+biomassa+foto) / total);
        percentage *= 100;
        renewableEnergy =  Math.round(percentage);
        Log.d("STATISTICS","renewableEnergy: "+renewableEnergy);
        ChangeColorByEnergy(renewableEnergy);
        new RefreshData().start();

        /*sendMessage("Person consumption-Maria-22-Pedro-11-Joao-7");
        sendMessage("Total overall power-90");
        sendMessage("Plug consumption-plug1.local-52-plug2.local-21");
        sendMessage("Plug consumption-plug1.local-12-plug2.local-25");
        sendMessage("Plug consumption-plug1.local-8-plug2.local-19");
        sendMessage("Plug consumption-plug1.local-42-plug2.local-20");*/
    }

    public void notify(String title, String message)
    {
        if(!paused) toast(getApplicationContext(),title + " - " + message);
        else UI.notify(this,MainActivity.class,title,message);
    }

    public void initTasks()
    {
        powerTask = new TimerTask() {
            @Override
            public void run() {
                HttpRequest request = new HttpRequest(BASE_URL + "plug/AvailablePlugs", getApplicationContext(), _queue);
                try {
                    request.start();
                    request.join();
                    String StringData = request.getData();
                    JSONArray JSONPlugs = new JSONArray(StringData);
                    for (int j = 0; j < JSONPlugs.length(); j++) {
                        JSONObject plug = (JSONObject) JSONPlugs.get(j);
                        String plugName = plug.getString("name");
                        int id = Integer.parseInt(plugName.substring(0, plugName.indexOf(".")).replace("plug", ""));
                        String url = BASE_URL + "plug/" + id + "/Power";
                        HttpRequest plug_power = new HttpRequest(url, getApplicationContext(), _queue);
                        plug_power.start();
                        plug_power.join();
                        String data = plug_power.getData();
                        JSONObject JSONData = new JSONObject(data);
                        int power = JSONData.getInt("power");
                        sendMessage("Plug consumption"
                                + "-"
                                + "plug" + id + ".local"
                                + "-"
                                + power);
                        Log.d("STATISTICS", "plug" + id + ".local is consuming " + power);
                    }

                    sendMessage("Device consumption"
                            + "-"
                            + "chaleira top"
                            + "-"
                            + (new Random().nextInt( 20) + 7));

                    sendMessage("Device consumption"
                            + "-"
                            + "luz top"
                            + "-"
                            + (new Random().nextInt(22) + 11));

                    if (!paused)
                        toast(getApplicationContext(), "Device consumption" + " - " + "Updated data!");
                    else
                        UI.notify(getApplicationContext(), MainActivity.class, "Device consumption", "Updated data!");

                    if (!paused)
                        toast(getApplicationContext(), "Plug consumption" + " - " + "Updated data!");
                    else
                        UI.notify(getApplicationContext(), MainActivity.class, "Plug consumption", "Updated data!");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                //ConsultUsers();
            }
        };

        energyTask = new TimerTask () {
            @Override
            public void run () {
                /*Date dNow = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat ("yyyy-MM-dd");
                String data = dateFormat.format(dNow);
                String DataURL = EnergyData+data;
                HttpRequest request = new HttpRequest(DataURL, getApplicationContext() ,_queue);
                try{
                    request.start();
                    request.join();
                    String StringData = request.getData();
                    JSONObject JSONData = new JSONObject(StringData);
                    JSONArray aux = (JSONArray) JSONData.get("prod_data");
                    JSONData = (JSONObject) aux.get(0);
                    float total = JSONData.getInt("total");
                    float termica = JSONData.getInt("termica");
                    float hidrica = JSONData.getInt("hidrica");
                    float eolica = JSONData.getInt("eolica");
                    float biomassa = JSONData.getInt("biomassa");
                    float foto = JSONData.getInt("foto");

                    sendMessage("Energy"
                            +"-"+"Não renovável"
                            +"-"+(total-termica-hidrica-eolica-biomassa-foto)
                            +"-"+"Térmica"
                            +"-"+termica
                            +"-"+"Hídrica"
                            +"-"+hidrica
                            +"-"+"Eólica"
                            +"-"+eolica
                            +"-"+"Biomassa"
                            +"-"+biomassa
                            +"-"+"Fotovoltaica"
                            +"-"+foto
                    );
                    if(!paused) toast(getApplicationContext(),"Energy consumption" + " - " + "Updated data!");
                    else UI.notify(this,MainActivity.class,"Energy consumption","Updated data!");

                    // falta enviar para o wear (para atualizar o pie chart)
                    float percentage = ((termica+hidrica+eolica+biomassa+foto) / total);
                    percentage *= 100;
                    renewableEnergy =  Math.round(percentage);
                    ChangeColorByEnergy(renewableEnergy);
                    new RefreshData().start();
                }catch(Exception e){
                    e.printStackTrace();
                }*/
            }
        };

        personTask = new TimerTask()
        {
            @Override
            public void run() {
                HttpRequest CheckUsers = new HttpRequest(BASE_URL + "plug/Power", getApplicationContext(), _queue);
                try {
                    CheckUsers.start();
                    CheckUsers.join();
                    ArrayList<String> users = new ArrayList<>();
                    ArrayList<Float> powers = new ArrayList<>();
                    String ArrayIdPower = CheckUsers.getData();
                    JSONArray IdPower = new JSONArray(ArrayIdPower);
                    for (int i = 0; i < IdPower.length(); i++) {
                        JSONObject User = (JSONObject) IdPower.get(i);
                        users.add(User.get("id").toString());
                        powers.add(Float.parseFloat(User.get("power").toString()));
                    }
                    String message = "Person consumption";
                    for (int i = 0; i < users.size(); i++) {
                        String temp = "-" + users.get(i) + "-" + powers.get(i);
                        message += temp;
                        Log.d("PERSONS", "Consumption of " + users.get(i) + ": " + powers.get(i));
                    }
                    sendMessage(message);
                    if (!paused)
                        toast(getApplicationContext(), "Person consumption" + " - " + "Updated data!");
                    else
                        UI.notify(getApplicationContext(), MainActivity.class, "Person consumption", "Updated data!");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

}
