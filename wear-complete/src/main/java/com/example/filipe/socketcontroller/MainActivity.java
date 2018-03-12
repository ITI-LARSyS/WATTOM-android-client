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
import com.example.filipe.socketcontroller.util.HttpRequest;
import com.example.filipe.socketcontroller.util.UI;
import com.google.android.gms.wearable.Node;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.eazegraph.lib.charts.BarChart;
import org.eazegraph.lib.models.BarModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.filipe.socketcontroller.util.UI.fitToScreen;
import static com.example.filipe.socketcontroller.util.UI.toast;
import static com.example.filipe.socketcontroller.util.UI.toggleVisibility;
import static com.example.filipe.socketcontroller.util.UI.updateTime;

public class MainActivity extends Activity implements SensorEventListener
{
    private static final String TAG = "Main Activity Watch";
    private static final int WINDOW_SIZE = 40;  // terá qde ser 80

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
    private int HourScheduleStart,MinScheduleStart,HourScheduleEnd,MinScheduleEnd;


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
    private DynamicPieChart piePessoasAcum;
    private DynamicLineChart linePlugs;
    private DynamicPieChart piePlugsAcum;
    private DynamicPieChart pieEnergias;
    private BarChart mBarChart;
    private TextView textCurSeries;


    /* *** */
    /* LOG */
    /* *** */
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private TextView _consumo;

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
        _simuView       = (SimulationView) findViewById(R.id.tab_simulation);

        hourlyTimer           =  new Timer ();
        minTimer           =  new Timer ();
        PowerTimer          =  new Timer ();
        isScheduleMode = false;

        Device_Name = Settings.Secure.getString(getContentResolver(), "bluetooth_name");

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        _queue = Volley.newRequestQueue(this);
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
                MinScheduleStart = minStart;
                HourScheduleEnd = hourEnd;
                MinScheduleEnd = minEnd;

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
        piePessoasAcum = (DynamicPieChart) findViewById(R.id.tab_power_pessoas_total);
        pieEnergias = (DynamicPieChart) findViewById(R.id.tab_energias);
        piePlugsAcum = (DynamicPieChart) findViewById(R.id.tab_power_plugs_total);
        linePlugs = (DynamicLineChart) findViewById(R.id.linechartplugs);
        textCurSeries = (TextView) findViewById(R.id.textCurSeries);
        textCurSeries.bringToFront();
        linePlugs.setLegend(textCurSeries);
        mBarChart = (BarChart) findViewById(R.id.tab_stats_bar_test);
        fitToScreen(this,piePessoasAcum);
        fitToScreen(this,pieEnergias);
        fitToScreen(this,piePlugsAcum);
        fitToScreen(this,linePlugs,50);
        fitToScreen(this,mBarChart);

        /* *** */
        /* LOG */
        /* *** */
        _x_acc          = (TextView) findViewById(R.id.x_text_field);
        _y_acc          = (TextView) findViewById(R.id.y_text_field);
        _z_acc          = (TextView) findViewById(R.id.z_text_field);
        _tms            = (TextView) findViewById(R.id.tms_text_field);
        _consumo        = (TextView) findViewById(R.id.ConsumoInsert);

       fillEazeGraph();
    }

    private void fillEazeGraph()
    {
        piePessoasAcum.setValue("Manel",20);
        piePessoasAcum.setValue("Afonso",40);
        piePessoasAcum.setValue("Dionísio",10);

        mBarChart.addBar(new BarModel(2.3f, 0xFF123456));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(3.3f, 0xFF563456));
        mBarChart.addBar(new BarModel(1.1f, 0xFF873F56));
        mBarChart.addBar(new BarModel(2.7f, 0xFF56B7F1));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(0.4f, 0xFF1FF4AC));
        mBarChart.addBar(new BarModel(4.f,  0xFF1BA4E6));

        linePlugs.addPoint("plug1.local","21:01",2.4f);
        linePlugs.addPoint("plug2.local","21:01",4.4f);
        linePlugs.addPoint("plug2.local","21:02",2.9f);
        linePlugs.addPoint("plug1.local","21:02",1f);
        linePlugs.addPoint("plug1.local","21:03",4.4f);
        linePlugs.addPoint("plug2.local","21:03",4.0f);
        linePlugs.addPoint("plug1.local","21:04",4.4f);
        linePlugs.addPoint("plug2.local","21:04",5f);
        linePlugs.addPoint("plug1.local","21:05",4.4f);
        linePlugs.addPoint("plug2.local","21:05",4.4f);

        piePlugsAcum.incValue("plug1.local",30);
        piePlugsAcum.incValue("plug2.local",20);
        piePlugsAcum.incValue("plug4.local",20);
        piePlugsAcum.incValue("plug1.local",20);
        piePlugsAcum.incValue("plug5.local",20);
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

    public void ola(View v)
    {
        navigationDrawer.setCurrentItem(TabConfig.PLUGS.ordinal(),true);
        linePlugs.switchSeries("plug3.local");
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
        ChangeEnergyURL = PLUGS_URL+"energy/";
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
                        _simuView.setCoords((float) _handlers.get(_led_target).getPosition()[0], (float) _handlers.get(_led_target).getPosition()[1],(float)_last_acc_x,(float)_last_acc_y);
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

        private synchronized void updateTarget(final int led_target, boolean match) {
//            _countingTime     = false;
//            _aquisition_time = System.currentTimeMillis()-_aquisition_time;
//            _studyResult = _studyResult +"\n"+_participant+","+_target_selection+","+_aquisition_time+","+_angles[_angleCount]+","+_pointing+","+(System.currentTimeMillis());
            //Log.wtf("Corr", "aq time= "+_aquisition_time);
            for (int j = 0; j < _devices_count; j++) {
                if (match && led_target == _target[j]) {
                    index = j;
                    if (isScheduleMode) {
                        TurnOffAndRemove(j);
                        ChangeColorByEnergy(renewableEnergy);
                        isScheduleMode = false;
                        TimerTask minTask = new TimerTask() {
                            @Override
                            public void run() {
                                Calendar calendar = Calendar.getInstance();
                                int actualMinute = calendar.get(Calendar.MINUTE);
                                int actualHour = calendar.get(Calendar.HOUR_OF_DAY);
                                HttpRequest selected_request;
                                if (actualMinute == MinScheduleStart && actualHour == HourScheduleStart) {
                                    TurnOnAndAdd(index);
                                } else if (actualMinute == MinScheduleEnd && actualHour == HourScheduleEnd) {
                                    TurnOffAndRemove(index);
                                }
                            }
                        };
                        minTimer.schedule(minTask, 10, 1000 * 60/*1min*/);
                    } else {
                        if (IsOn) {
                            TurnOffAndRemove(j);
                        } else {
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
                notify("Wattapp", "plug" + _plug_names.get(j) + ".local has been turned on");
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
                    piePessoasAcum.incValue(User.get("id").toString(), Float.parseFloat(User.get("power").toString()));
                }
                piePessoasAcum.startAnimation();
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
            start();
            v.setOnClickListener((x)-> handleStopStudyClick(x));
        }

        public void handleStopStudyClick(View v)
        {
            stop();
            v.setOnClickListener((x)-> handleStartStudyClick(x));
        }

        public void start()
        {
            //_participant = Integer.parseInt(_pId.getText().toString());
            //_angle       = 0;
            //_pointing    = _condition.getText().toString();
            //SELECTED_URL =  SELECTED_URL.replace("%",_plug+"");

            inStudy = true;
            toast(getApplicationContext(),"Study started!");

            IsOn = false;

            TimerTask hourlyTask = new TimerTask () {
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

                    pieEnergias.setValue("Não renovável",(total-termica-hidrica-eolica-biomassa-foto));
                    pieEnergias.setValue("Térmica",termica);
                    pieEnergias.setValue("Hídrica",hidrica);
                    pieEnergias.setValue("Eólica",eolica);
                    pieEnergias.setValue("Biomassa",biomassa);
                    pieEnergias.setValue("Fotovoltaica",foto);
                    pieEnergias.startAnimation();

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
            hourlyTimer.schedule (hourlyTask, 0 ,1000*60*15);

        /* */
        /* */
            pieEnergias.setValue("Não renovável",72);
            pieEnergias.setValue("Térmica",9);
            pieEnergias.setValue("Hídrica",3);
            pieEnergias.setValue("Eólica",7);
            pieEnergias.setValue("Biomassa",8);
            pieEnergias.setValue("Fotovoltaica",11);
            pieEnergias.startAnimation();
        /* */
        /* */

            new StartUp(PLUGS_URL).start();

            TimerTask checkPower = new TimerTask () {
                @Override
                public void run () {
                    if(IsOn){
                        int powerTotal = 0;
                        HttpRequest Pessoas = new HttpRequest(BASE_URL + "plug/Persons", getApplicationContext() ,_queue);
                        try{
                            Pessoas.start();
                            Pessoas.join();
                            String StringData = Pessoas.getData();
                            JSONArray JSONPerson = new JSONArray(StringData);
                            for(int i = 0; i < JSONPerson.length(); i++){
                                JSONObject temp = (JSONObject) JSONPerson.get(i);
                                if(temp.get("id").equals(Device_Name)){
                                    int plugs [];
                                    JSONArray intPlugs = temp.getJSONArray("plugs");
                                    plugs = new int[intPlugs.length()];
                                    for (int j = 0; j < intPlugs.length(); ++j) {
                                        String plug = intPlugs.getString(j);
                                        plugs[j] = Integer.parseInt(plug.substring(0, plug.indexOf(".")).replace("plug", ""));
                                    }

                                    for(int w = 0; w < plugs.length;w++)
                                    {
                                        String DataURL = BASE_URL + "plug/"+plugs[w]+"/Power";
                                        HttpRequest request = new HttpRequest(DataURL, getApplicationContext() ,_queue);
                                        request.start();
                                        request.join();
                                        String dado = request.getData();
                                        JSONObject JSONData = new JSONObject(dado);
                                        int power = JSONData.getInt("power");
                                        linePlugs.addPoint("plug"+plugs[w]+".local",power);
                                        piePlugsAcum.incValue("plug"+plugs[w]+".local",power);
                                        Log.d("STATISTICS","plug"+plugs[w]+".local is consuming "+power);
                                        powerTotal += power;
                                    }
                                    if(!paused) toast(getApplicationContext(),"Plug consumption" + " - " + "Updated data!" );
                                    else UI.notify(getApplicationContext(),MainActivity.class,"Plug consumption","Updated data!");
                                }
                            }
                            _consumo.setText(powerTotal);
                            if(!paused) toast(getApplicationContext(),"Overall power consumption" + " - " + "Updated data!" );
                            else UI.notify(getApplicationContext(),MainActivity.class,"Overall power consumption","Updated data!");
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    ConsultUsers();
                }
            };
            PowerTimer.schedule(checkPower, 0 ,1000*60/*1 min*/);
        }

        public void stop()
        {
            inStudy = false;
           // sendMessage("STOP");
            toast(getApplicationContext(),"Study ended!");
            stopServices();
            _correlationRunning = false;
            startActivity(new Intent(this,MainActivity.class));
            this.finish();
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
}