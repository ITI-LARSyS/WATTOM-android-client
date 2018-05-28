package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.wearable.view.drawer.WearableNavigationDrawer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.filipe.socketcontroller.charts.DynamicLineChart;
import com.example.filipe.socketcontroller.charts.DynamicPieChart;
import com.example.filipe.socketcontroller.motion.PlugMotionHandler;
import com.example.filipe.socketcontroller.tabs.TabAdapter;
import com.example.filipe.socketcontroller.tabs.TabConfig;
import com.example.filipe.socketcontroller.util.Alarm;
import com.example.filipe.socketcontroller.util.HttpRequest;
import com.example.filipe.socketcontroller.util.UI;
import com.google.android.gms.wearable.Node;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.filipe.socketcontroller.util.UI.fitToScreen;
import static com.example.filipe.socketcontroller.util.UI.hide;
import static com.example.filipe.socketcontroller.util.UI.toast;
import static com.example.filipe.socketcontroller.util.UI.toggleVisibility;
import static com.example.filipe.socketcontroller.util.UI.unhide;
import static com.example.filipe.socketcontroller.util.UI.updateTime;

public class MainActivity extends Activity implements SensorEventListener
{
    private static final String TAG = "Main Activity Watch";
    private static final int WINDOW_SIZE = 40;  // terá qde ser 80

    private int indexLuz = -1, indexChaleira = -1;

    private TimerTask powerTask, energyTask, personTask;

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
    private int HourScheduleStart,MinutesScheduleStart,HourScheduleEnd,MinutesScheduleEnd;


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


    /* ***** */
    /* ????? */
    /* ***** */
    private int Primeiroconsumo;
    private int consumo;
    private int primeiro;
    private int consumoTotal;
    private int seconds;

    /* **************** */
    /* BACK-END (GERAL) */
    /* **************** */
    private PowerManager.WakeLock cpuWakeLock;
    private PushThread pushThread;
    private long _last_push;
    private long _sampling_diff = 40;        // alterei o sampling rate aqui
    private boolean paused = false;
    private boolean inStudy = false;
    private Vibrator vibrator;

    /* ***************** */
    /* BACK-END (SENSOR) */
    /* ***************** */
    private float[] _rotationMatrix = new float[16];
    private float x;
    private float z;
    private boolean _sensor_running = false;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    /* **************** */
    /* NAVEGAÇÃO E AÇÃO */
    /* **************** */
    private WearableNavigationDrawer navigationDrawer;

    /* ************** */
    /* START/STOP TAB */
    /* ************** */
    private int _factor;
    private CheckBox _leftHanded;

    /* ************ */
    /* SCHEDULE TAB */
    /* ************ */
    private Button _buttonSchedule;
    private TextView _textInitTime;
    private TextView _textEndTime;
    private static final int SELECT_TIME_START = 0;
    private static final int SELECT_TIME_END = 1;
    private static final int TIME_CONFIRMED = 2;
    private int scheduleState = 0;
    private TimePicker _pickerInitTime;
    private TimePicker _pickerEndTime;
    private boolean changedStart;
    private boolean changedEnd;
    private Button _buttonStart;
    private Button _buttonEnd;

    /* ***** */
    /* STATS */
    /* ***** */
    private DynamicPieChart piePessoas;
    private DynamicLineChart linePessoas;
    private DynamicLineChart linePlugs;
    private DynamicPieChart piePlugs;
    private DynamicPieChart pieEnergias;
    private DynamicLineChart lineDevices;
    private float mediaConsumoPessoa = -1;


    /* *** */
    /* LOG */
    /* *** */
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private TextView _consumo;

    private int currentMode;
    private static final int NO_MODE = -1;
    private static final int SELECT_TARGET_MODE = 0;
    private static final int STANDARD_MODE = 1;
    private static final int SCHEDULE_MODE = 2;

    private static final String[] PLUG_NAMES = { "Cozinha" , "Sala de estar" , "Quarto de dormir" , "Casa de banho" , "Hall de entrada" , "Escritório" , "Lavandaria" , "Dispensa" , "Arrecadação" };

    private int plugSelected = -1;

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ****************** */
    /* ANDROID LIFE-CYCLE */
    /* ****************** */

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        askIP();
        setContentView(R.layout.general_layout);
      //  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // São obtidos os IDs dos elementos da View e estes são configurados
        setupView();

        // Inicializações
        seconds = 0;
        Primeiroconsumo=0;
        consumo = 0;
        primeiro = 0;
        consumoTotal = 0;
        _sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        _sensor = _sensorManager.getDefaultSensor( Sensor.TYPE_ORIENTATION);
        _last_push = System.currentTimeMillis();
      //  _simuView       = (SimulationView) findViewById(R.id.tab_simulation);

        hourlyTimer           =  new Timer ();
        minTimer           =  new Timer ();
        PowerTimer          =  new Timer ();
        isScheduleMode = false;

        Device_Name = Settings.Secure.getString(getContentResolver(), "bluetooth_name");

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        _queue = Volley.newRequestQueue(this);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initTasks();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onStart()
    { super.onStart(); }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(cpuWakeLock.isHeld()) cpuWakeLock.release();
        _sensorManager.unregisterListener(this);
        _sensor_running = false;
        Log.d("FIM","----FIM----");
    }

    @Override
    public void onResume()
    {
        super.onResume();
        paused = false;
        if(cpuWakeLock.isHeld()) cpuWakeLock.release();
        Log.i(TAG, "On resume called");
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

    @Override
    public void onSensorChanged(SensorEvent event)
    {
            //Log.wtf(TAG,event.toString());

/*
        SensorManager.getRotationMatrixFromVector(_rotationMatrix,
                event.values);
        SensorManager
                .remapCoordinateSystem(_rotationMatrix,
                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                        _rotationMatrix);
        SensorManager.getOrientation(_rotationMatrix, _orientationVals);

        // Optionally convert the result from radians to degrees
        _orientationVals[0] = (float) Math.toDegrees(_orientationVals[0]);
        _orientationVals[1] = (float) Math.toDegrees(_orientationVals[1]);
        _orientationVals[2] = (float) Math.toDegrees(_orientationVals[2]);

//        Yaw:  _orientationVals[0]
//        Pitch:  _orientationVals[1]
//        Roll:     _orientationVals[2]

        float x = _orientationVals[0];//event.values[0];
        float y = _orientationVals[1];//event.values[1];
        float z = _orientationVals[2];
        int val =4;*/

            // if(x>val||y>val||z>val) {
            // float y = event.values[1];





            //}
            //  Log.i(TAG,"Sending data");

            //  float[] data = {x,y};
            x = event.values[0];
            // _x_acc.setText(x+"");
            z = event.values[2];
            z = _factor*z;
            // _y_acc.setText(z+"");

//            Log.i("DEBUG",x+","+z);

            //Log.i(TAG,"sending data form watch");
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ** */
    /* UI */
    /* ** */

    public void showStartPicker(View v)
    { toggleVisibility(_pickerInitTime); }

    public void showEndPicker(View v)
    { toggleVisibility(_pickerEndTime); }

    public void handleSensorClick(View v)
    {
        if(!_sensor_running)
        {
            _factor = _leftHanded.isChecked()? -1 : 1;
            _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_FASTEST);
            _sensor_running = true;
            pushThread = new PushThread();
            pushThread.start();
        }
        else
        {
            //cpuWakeLock.release();
            _sensorManager.unregisterListener(this);
            _sensor_running = false;

            try
            { pushThread.join(); }
            catch (InterruptedException e)
            { e.printStackTrace(); }
        }
    }

    public void handleScheduleButton(View v)
    {
        String[][] time = new String[2][2];
        String init = _textInitTime.getText().toString();
        String end =_textEndTime.getText().toString();
        time[0] = init.split(":");
        time[1] = end.split(":");
        switch(scheduleState)
        {
            case SELECT_TIME_START:
                hourStart = Integer.parseInt(time[0][0]);
                minStart = Integer.parseInt(time[0][1]);
                hourEnd = Integer.parseInt(time[1][0]);
                minEnd = Integer.parseInt(time[1][1]);
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
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_START);
                scheduleState++;
                break;

            case SELECT_TIME_END:
                SelectedTime(hourEnd,minEnd);
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_END);
                scheduleState++;
                break;

            case TIME_CONFIRMED:
                HttpRequest CrazyLights = new HttpRequest(BASE_URL + "plug/ScheduleMode", getApplicationContext(), _queue);
                try {
                    CrazyLights.start();
                    //CrazyLights.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isScheduleMode = true;
                _buttonSchedule.setText(R.string.SET_SCHEDULE);
                scheduleState = SELECT_TIME_START;
                break;

            default:
                break;
        }
    }

    private void setupView()
    {
        // Possibilita a navegação pelos tabs presentes no TabAdapter
        navigationDrawer = (WearableNavigationDrawer) findViewById(R.id.top_navigation_drawer);
        navigationDrawer.setAdapter(new TabAdapter(this));

        /* ********** */
        /* START/STOP */
        /* ********** */
        _leftHanded     = (CheckBox) findViewById(R.id.checkLeftHanded);

        /* ******** */
        /* SCHEDULE */
        /* ******** */
        _buttonSchedule = (Button) findViewById(R.id.buttonSchedule);
        _buttonStart    = (Button) findViewById(R.id.buttonStart);
        _buttonEnd      = (Button) findViewById(R.id.buttonEnd);
        _textInitTime = (TextView) findViewById(R.id.HoraInicio);
        _textEndTime = (TextView) findViewById(R.id.HoraFim);
        _pickerInitTime = (TimePicker) findViewById(R.id.InitialPicker);
        _pickerEndTime = (TimePicker) findViewById(R.id.EndPicker);
        _pickerInitTime.setIs24HourView(true);
        _pickerInitTime.setOnTimeChangedListener((view, hourOfDay, minute) ->
        {
            updateTime(_textInitTime,hourOfDay,minute);
            changedStart = true;
        });
        _pickerEndTime.setIs24HourView(true);
        _pickerEndTime.setOnTimeChangedListener((view, hourOfDay, minute) ->
        {
            updateTime(_textEndTime,hourOfDay,minute);
            changedEnd = true;
        });

        /* ***** */
        /* STATS */
        /* ***** */
        linePessoas = (DynamicLineChart) findViewById(R.id.tab_line_pessoas);
        piePessoas = (DynamicPieChart) findViewById(R.id.tab_pie_pessoas);
        pieEnergias = (DynamicPieChart) findViewById(R.id.tab_pie_energias);
        piePlugs = (DynamicPieChart) findViewById(R.id.tab_pie_plugs);
        linePlugs = (DynamicLineChart) findViewById(R.id.tab_line_plugs);
        lineDevices = (DynamicLineChart) findViewById(R.id.tab_line_devices);

        fillEazeGraph();
    }

    private void fillEazeGraph()
    {
        piePessoas.setValue("Manel",20);
        piePessoas.setValue("Afonso",40);
        piePessoas.setValue("Dionísio",10);
        piePessoas.setOnLongClickListener((v)->
        {
            navigationDrawer.setCurrentItem(TabConfig.PESSOAS2.ordinal(),true);
            return true;
        });

        linePlugs.addPoint("Sala de estar","21:01",2.4f);
        linePlugs.addPoint("Sala de estar","21:02",1f);
        linePlugs.addPoint("Sala de estar","21:03",4.4f);
        linePlugs.addPoint("Sala de estar","21:04",6.9f);
        linePlugs.addPoint("Sala de estar","21:05",5.4f);
        linePlugs.addPoint("Quarto de dormir","21:01",4.4f);
        linePlugs.addPoint("Quarto de dormir","21:02",2.9f);
        linePlugs.addPoint("Quarto de dormir","21:03",4.0f);
        linePlugs.addPoint("Quarto de dormir","21:04",5f);
        linePlugs.addPoint("Quarto de dormir","21:05",4.4f);
        linePlugs.addPoint("Hall de entrada","21:01",4.4f);
        linePlugs.addPoint("Hall de entrada","21:02",2.9f);
        linePlugs.addPoint("Hall de entrada","21:03",4.0f);
        linePlugs.addPoint("Hall de entrada","21:04",5f);
        linePlugs.addPoint("Hall de entrada","21:05",4.4f);
        linePlugs.setOnLongClickListener((v)->
        {
            navigationDrawer.setCurrentItem(TabConfig.PLUGSTOTAL.ordinal(),true);
            return true;
        });

        lineDevices.addPoint("Chaleira","14:02",2.4f);
        lineDevices.addPoint("Candeeiro","14:02",1.4f);
        lineDevices.addPoint("Chaleira","14:03",4.1f);
        lineDevices.addPoint("Candeeiro","14:03",1.2f);
        lineDevices.addPoint("Chaleira","14:04",2.3f);
        lineDevices.addPoint("Candeeiro","14:04",1.3f);
        lineDevices.addPoint("Chaleira","14:05",3.7f);
        lineDevices.addPoint("Candeeiro","14:05",1.7f);
        lineDevices.addPoint("Chaleira","14:06",2.5f);
        lineDevices.addPoint("Candeeiro","14:06",1.2f);
        lineDevices.addPoint("Chaleira","14:07",3.0f);
        lineDevices.addPoint("Candeeiro","14:07",1.9f);
        lineDevices.addPoint("Chaleira","14:08",3.1f);
        lineDevices.addPoint("Candeeiro","14:08",1.6f);

        linePessoas.addPoint("Manel","14:01",10);
        linePessoas.addPoint("Afonso","14:01",20);
        linePessoas.addPoint("Dionísio","14:01",30);
        mediaConsumoPessoa += 20;
        linePessoas.addPoint(mediaConsumoPessoa);

        linePessoas.addPoint("Manel","14:02",11);
        mediaConsumoPessoa += 11;
        mediaConsumoPessoa /= 2;
        linePessoas.addPoint("Afonso","14:02",19);
        mediaConsumoPessoa += 19;
        mediaConsumoPessoa /= 2;
        linePessoas.addPoint("Dionísio","14:02",20);
        mediaConsumoPessoa += 20;
        mediaConsumoPessoa /= 2;
        linePessoas.addPoint(mediaConsumoPessoa);

        linePessoas.addPoint("Manel","14:03",15);
        mediaConsumoPessoa += 15;
        mediaConsumoPessoa /= 3;
        linePessoas.addPoint("Afonso","14:03",3);
        mediaConsumoPessoa += 3;
        mediaConsumoPessoa /= 2;
        linePessoas.addPoint("Dionísio","14:03",12);
        mediaConsumoPessoa += 12;
        mediaConsumoPessoa /= 2;
        linePessoas.addPoint(mediaConsumoPessoa);

        linePessoas.setOnLongClickListener((v)->
        {
            navigationDrawer.setCurrentItem(TabConfig.PESSOAS.ordinal(),true);
            return true;
        });

        piePlugs.incValue("Sala de estar",30);
        piePlugs.incValue("Quarto de dormir",20);
        piePlugs.incValue("Hall de entrada",20);
        piePlugs.incValue("Sala de estar",20);
        piePlugs.incValue("Escritório",20);
        piePlugs.setOnLongClickListener((v)->
        {
            navigationDrawer.setCurrentItem(TabConfig.PLUGS.ordinal(),true);
            return true;
        });

        pieEnergias.setValue("Eólica",20);
        pieEnergias.setValue("Não renovável",50);
        pieEnergias.setValue("Hídrica",10);
    }

    public void notify(String title, String message)
    {
        if(!paused) toast(getApplicationContext(),title + " - " + message);
        else UI.notify(this,MainActivity.class,title,message);
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ******* */
    /* THREADS */
    /* ******* */

    private class PushThread extends Thread
    {
        public void run()
        {
            while(_sensor_running)
            {
                _last_acc_x = x;            // updatre the global variables to be used elsewhere in the code
                _last_acc_y = z;
                Log.d("SENSOR","x:"+x+" z:"+z);

                try
                { Thread.sleep(_sampling_diff); }
                catch (InterruptedException e)
                { e.printStackTrace(); }
            }
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
        ask.setView(input);
        ask.setCancelable(false);

        ask.setPositiveButton("Set new IP", (dialog, whichButton) ->
        {
            String newIP = input.getText().toString();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("IP",newIP);
            editor.apply();
            setIP(newIP);
            toast(getApplicationContext(),"You chose to set new IP ("+newIP+")");
        });
        ask.setNegativeButton("Keep previous IP", (dialog, whichButton) ->
        {
            setIP(oldIP);
            toast(getApplicationContext(),"You chose to keep previous IP ("+oldIP+")");
        });
        ask.show();
    }

    public void setIP(String ip)
    {
        BASE_URL        = "http://"+ip+":3000/";
        PLUGS_URL       = BASE_URL +"plug/";
        PLUG_URL        = PLUGS_URL +"%/";
        SELECTED_URL    = PLUG_URL + "selected/";
        ChangeEnergyURL = PLUGS_URL+"%/energy/";
    }

    public static String getBaseURL() { return BASE_URL; }

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

                    if((_led_target ==_target[0])) {     // used to print the simulation on the screen
                        //_simuView.setCoords((float) _handlers.get(_led_target).getPosition()[0], (float) _handlers.get(_led_target).getPosition()[1],(float)_last_acc_x,(float)_last_acc_y);
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

    private class CorrelationHandler extends Thread {

        double _correlations[][];
        int _correlations_count[];
        private Thread _togglers[];

        private void updateCorrelations(int index, int[] correlations) {
            int temp = correlations[index] + 1;

            for (int i = 0; i < correlations.length; i++)
                correlations[i] = 0;

            correlations[index] = temp;
        }

        //
        @Override
        public void run() {

            _correlationRunning = true;
            _correlations = new double[2][_devices_count];
            _correlations_count = new int[_devices_count];
            _togglers = new Thread[_devices_count];
            while (_correlationRunning) {
                //if(_countingTime)     // check if we are counting time in the current matching process
                //   checkRunningTime();
                /**
                 *  -0.1 to 0.1 indicates no linear correlation
                 -0.5 to -0.1 or 0.1 to 0.5 indicates a “weak” correlation
                 -1 to -0.5 or 0.5 to 1 indicates a “strong” correlation
                 */
                for (int i = 0; (i < _devices_count) && (_plug_data_indexes[_target[i]] == WINDOW_SIZE); i++) {
                    _correlations[0][i] = pc.correlation(_plug_target_data[i][0], _acc_data[i][0]);
                    _correlations[1][i] = pc.correlation(_plug_target_data[i][1], _acc_data[i][1]);
                    Log.d("CORR0", "" + _correlations[0][i]);
                    Log.d("CORR1", "" + _correlations[1][i]);
                }
                for (int i = 0; i < _devices_count; i++) {
                    //Log.i("Corr","correlation "+ i +" "+_correlations[0][i]+","+_correlations[1][i]);
                    if (((_correlations[0][i] >= 0.8 && _correlations[0][i] < 1) && (_correlations[1][i] >= 0.8 && _correlations[1][i] < 1))) {  // sometimes at the start we get 1.0 we want to avoid that
                        if (!_updating)
                            updateCorrelations(i, _correlations_count);
                        // Log.i("Corr","correlation "+i+" "+_correlations[0][i]+","+_correlations[1][i]);
                        if (_correlations_count[i] == 3) {
                            _correlations_count[i] = 0;
                            //if (i == _target[i]){
                            _target_selection = true;
                            // Beep to show its the correct match
                            // ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                            //toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT,150);
                            if (_togglers[i] == null || !_togglers[i].isAlive()) {
                                final int finalI = i;
                                _togglers[i] = new Thread(() ->
                                {
                                    updateTarget(finalI, true);
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
                }

                try {
                    Thread.sleep(_correlationInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private synchronized void updateTarget(final int led_target, boolean match){
//            _countingTime     = false;
//            _aquisition_time = System.currentTimeMillis()-_aquisition_time;
//            _studyResult = _studyResult +"\n"+_participant+","+_target_selection+","+_aquisition_time+","+_angles[_angleCount]+","+_pointing+","+(System.currentTimeMillis());
            //Log.wtf("Corr", "aq time= "+_aquisition_time);

            // Haptic feedback para a ocorrência de um evento
            vibrator.vibrate(250);

            for(int j = 0; j<_devices_count;j++){
                if(match && led_target == _target[j]){
                    index = j;

                    switch(currentMode)
                    {
                        case NO_MODE:
                            // (does nothing)
                            break;

                            /* Seleção da plug */
                        case SELECT_TARGET_MODE:
                            currentMode = STANDARD_MODE;
                            plugSelected = Integer.parseInt(_plug_names.get(led_target));
                            ChangeColorByEnergy(renewableEnergy,plugSelected);
                            new RefreshData().start();
                            // ...
                            // ...
                            break;

                            /* Ligar/desligar uma plug */
                        case STANDARD_MODE:
                            // Se tiver mais que um device
                            // (vai para o gráfico desse device)
                            if(isMultiTarget())
                            {
                                if(j == indexLuz)
                                {
                                    navigationDrawer.setCurrentItem(TabConfig.DEVICES.ordinal(),true);
                                    lineDevices.switchSeries("Candeeiro");
                                }
                                else
                                {
                                    if(j == indexChaleira)
                                    {
                                        navigationDrawer.setCurrentItem(TabConfig.DEVICES.ordinal(),true);
                                        lineDevices.switchSeries("Chaleira");
                                    }
                                }
                            }

                            // Se tiver só um device
                            // (vai para o gráfico desse plug)
                            else
                            {
                                if(IsOn)
                                {
                                    TurnOffAndRemove(j);
                                }
                                else
                                {
                                    TurnOnAndAdd(j);
                                }
                            }
                            break;

                            /* (Confirmação do) schedule */
                        case SCHEDULE_MODE:
                            if(IsOn) TurnOffAndRemove(j);
                            ChangeColorByEnergy(renewableEnergy,plugSelected);
                            currentMode = NO_MODE;
                            new Alarm(HourScheduleStart, MinutesScheduleStart,()->
                            {
                                TurnOnAndAdd(index);
                                new Alarm(HourScheduleEnd, MinutesScheduleEnd, ()->
                                {
                                    TurnOffAndRemove(index);
                                    currentMode = STANDARD_MODE;
                                },false).activate();
                            },false).activate();
                            break;

                        default:
                            break;
                    }
                }
            }
        }
    }

    public synchronized void ChangeColorByEnergy(int percent, int plugId){
        HttpRequest novo = new HttpRequest(ChangeEnergyURL
                .replace("%", ""+plugId) +percent, getApplicationContext() ,_queue);
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
                //notify("Wattapp","plug"+_plug_names.get(j)+".local has been turned off");

            }catch (Exception e){
                e.printStackTrace();
            }
            // ConsultUsers();
        }

        public void TurnOnAndAdd(int j) {
            try {
                HttpRequest selected_request = new HttpRequest(BASE_URL + "plug/" + _plug_names.get(j) + "/relay/0", getApplicationContext(), _queue);
                HttpRequest enviaNome = new HttpRequest(BASE_URL + "plug/InsertNewPerson/" + Device_Name + "-" + _plug_names.get(j), getApplicationContext(), _queue);
                selected_request.start();
                enviaNome.start();
                selected_request.join();
                enviaNome.join();
                Log.d("PLUGS", "plug" + _plug_names.get(j) + ".local has been turned on by " + Device_Name);
                IsOn = true;
                navigationDrawer.setCurrentItem(TabConfig.PLUGS.ordinal(),true);
                linePlugs.switchSeries("plug" + _plug_names.get(j) + ".local");
                //notify("Wattapp", "plug" + _plug_names.get(j) + ".local has been turned on");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void getPlugsData(){
            try {
                _plug_names = new ArrayList<>();
                HttpRequest novo = new HttpRequest(BASE_URL + "plug/AvailablePlugs", getApplicationContext());
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

        private void ConsultUsers(){
            HttpRequest CheckUsers = new HttpRequest(BASE_URL + "plug/Power", getApplicationContext(),_queue);
            try{
                CheckUsers.start();
                CheckUsers.join();
                ArrayList<String> users = new ArrayList<>();
                ArrayList<Float> powers = new ArrayList<>();
                String ArrayIdPower = CheckUsers.getData();
                JSONArray IdPower = new JSONArray(ArrayIdPower);
                for(int i = 0; i < IdPower.length(); i++)
                {
                    JSONObject User = (JSONObject) IdPower.get(i);
                    piePessoas.setValue(User.get("id").toString(), Float.parseFloat(User.get("power").toString()));
                }
                piePessoas.startAnimation();
                if(!paused) toast(getApplicationContext(),"Person consumption" + " - " + "Updated data!" );
                else UI.notify(getApplicationContext(),MainActivity.class,"Person consumption","Updated data!");
            }catch (Exception e){
                e.printStackTrace();
            }
        }

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

    public void handleStartStudyClick(View v)
    {
        prepareStudy();
        new Thread(()->
        {
            //_participant = Integer.parseInt(_pId.getText().toString());
            //_angle       = 0;
            //_pointing    = _condition.getText().toString();
            //SELECTED_URL =  SELECTED_URL.replace("%",_plug+"");

            inStudy = true;
            toast(getApplicationContext(),"Study started!");

            IsOn = false;
            currentMode = SELECT_TARGET_MODE;

            new Thread(()->
            {
                HttpRequest start = new HttpRequest(PLUGS_URL+"start/6",getApplicationContext(),_queue);
                start.start();
                try {
                    start.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            new StartUp(PLUGS_URL).start();
            new Timer().schedule(energyTask,0,1000*60*15);
            new Timer().schedule(powerTask,250,1000*60);
            new Timer().schedule(personTask,500,1000*60);
        }).start();
    }

        public void handleStopStudyClick(View v)
        {
            new Thread(()->
            {
                HttpRequest stopMoving = new HttpRequest(PLUGS_URL + "StopMoving", getApplicationContext(), _queue);
                stopMoving.start();
                try
                {
                    stopMoving.join();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }).start();
            startActivity(new Intent(this,MainActivity.class));
            this.finish();
        }

        private void prepareStudy()
        {
            Button start = (Button) findViewById(R.id.btnStartStudy);
            hide(start);

            Button demo = (Button) findViewById(R.id.btnDemo3);
            hide(demo);

            Button stop = (Button) findViewById(R.id.btnStopStudy);
            unhide(stop);
        }

        public void demo3(View v)
        {
            prepareStudy();
            new Thread(()->
            {
                HttpRequest demo = new HttpRequest(PLUGS_URL + "Demo3/2", getApplicationContext() ,_queue);
                demo.start();
                try {
                    demo.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                currentMode = STANDARD_MODE;
                inStudy = true;
                toast(getApplicationContext(),"Study started!");

                IsOn = false;


                new StartUp(PLUGS_URL).start();


                new Timer().schedule(powerTask,250,1000*60);
                new Timer().schedule(personTask,500,1000*60);
            }).start();
        }

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
            HttpRequest TurnOff;
            HttpRequest Delete;
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
                JSONArray json_array = new JSONArray(data);
                for(int i=0;i<_handlers.size();i++) {
                    JSONObject json_message = json_array.getJSONObject(i);
                    _handlers.get(i).handlePlugMessage(json_message);
                    Thread.sleep(0);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
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
                        linePlugs.addPoint(plugName,power);
                        piePlugs.setValue(plugName,power);
                    }

                    lineDevices.addPoint("Chaleira",new Random().nextInt( 20) + 7);
                    lineDevices.addPoint("Candeeiro",(new Random().nextInt(22) + 11));

                    /*if (!paused)
                        toast(getApplicationContext(), "Device consumption" + " - " + "Updated data!");
                    else
                        UI.notify(getApplicationContext(), MainActivity.class, "Device consumption", "Updated data!");

                    if (!paused)
                        toast(getApplicationContext(), "Plug consumption" + " - " + "Updated data!");
                    else
                        UI.notify(getApplicationContext(), MainActivity.class, "Plug consumption", "Updated data!");*/

                } catch (Exception e) {
                    e.printStackTrace();
                }
                //ConsultUsers();
            }
        };

        energyTask = new TimerTask () {
            @Override
            public void run () {

                float total = 0;
                float termica = 0;
                float hidrica = 0;
                float eolica = 0;
                float biomassa = 0;
                float foto = 0;

                Date dNow = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat ("yyyy-MM-dd");
                String data = dateFormat.format(dNow);
                String DataURL = EnergyData+data;

                HttpRequest request = new HttpRequest(DataURL, getApplicationContext() ,_queue);

                try
                {
                    request.start();
                    request.join();
                    String StringData = request.getData();
                    JSONObject JSONData = new JSONObject(StringData);
                    JSONArray aux = (JSONArray) JSONData.get("prod_data");
                    JSONData = (JSONObject) aux.get(0);

                    total = JSONData.getInt("total");
                    termica = JSONData.getInt("termica");
                    hidrica = JSONData.getInt("hidrica");
                    eolica = JSONData.getInt("eolica");
                    biomassa = JSONData.getInt("biomassa");
                    foto = JSONData.getInt("foto");
                }

                catch(Exception e)
                {
                    e.printStackTrace();
                    total = 110;
                    termica = 9;
                    hidrica = 3;
                    eolica = 7;
                    biomassa = 8;
                    foto = 11;
                }

                finally
                {
                    float finalTotal = total;
                    float finalTermica = termica;
                    float finalHidrica = hidrica;
                    float finalEolica = eolica;
                    float finalBiomassa = biomassa;
                    float finalFoto = foto;
                    runOnUiThread(()->
                    {
                        pieEnergias.setValue("Não renovável", (finalTotal - finalTermica - finalHidrica - finalEolica - finalBiomassa - finalFoto));
                        pieEnergias.setValue("Térmica", finalTermica);
                        pieEnergias.setValue("Hídrica", finalHidrica);
                        pieEnergias.setValue("Eólica", finalEolica);
                        pieEnergias.setValue("Biomassa", finalBiomassa);
                        pieEnergias.setValue("Fotovoltaica", finalFoto);
                        pieEnergias.startAnimation();
                    });
                }
                   /* if(!paused) toast(getApplicationContext(),"Energy consumption" + " - " + "Updated data!");
                    else UI.notify(getApplicationContext(),MainActivity.class,"Energy consumption","Updated data!"); */

                    // falta enviar para o wear (para atualizar o pie chart)
                    float percentage = ((termica+hidrica+eolica+biomassa+foto) / total);
                    percentage *= 100;
                    renewableEnergy =  Math.round(percentage);
                    ChangeColorByEnergy(plugSelected,renewableEnergy);
                    new RefreshData().start();
            }
        };

        personTask = new TimerTask()
        {
            @Override
            public void run() {
                HttpRequest CheckUsers = new HttpRequest(BASE_URL + "plug/Power", getApplicationContext(),_queue);
                try{
                    CheckUsers.start();
                    CheckUsers.join();
                    ArrayList<String> users = new ArrayList<>();
                    ArrayList<Float> powers = new ArrayList<>();
                    String ArrayIdPower = CheckUsers.getData();
                    JSONArray IdPower = new JSONArray(ArrayIdPower);
                    for(int i = 0; i < IdPower.length(); i++)
                    {
                        JSONObject User = (JSONObject) IdPower.get(i);
                        piePessoas.setValue(User.get("id").toString(), Float.parseFloat(User.get("power").toString()));
                    }
                    piePessoas.startAnimation();
                    if(!paused) toast(getApplicationContext(),"Person consumption" + " - " + "Updated data!" );
                    else UI.notify(getApplicationContext(),MainActivity.class,"Person consumption","Updated data!");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        };
    }

    private boolean isMultiTarget()
    {
        return indexLuz != -1 && indexChaleira != -1;
    }
}