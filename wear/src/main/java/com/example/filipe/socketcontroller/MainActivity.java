package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.drawer.WearableActionDrawer;
import android.support.wearable.view.drawer.WearableNavigationDrawer;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TimePicker;

import com.example.filipe.socketcontroller.charts.DynamicLineChart;
import com.example.filipe.socketcontroller.charts.DynamicPieChart;
import com.example.filipe.socketcontroller.tabs.TabAdapter;
import com.example.filipe.socketcontroller.tabs.TabConfig;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.eazegraph.lib.charts.BarChart;
import org.eazegraph.lib.models.BarModel;

import java.util.Timer;
import java.util.TimerTask;

import static com.example.filipe.socketcontroller.util.UI.fitToScreen;
import static com.example.filipe.socketcontroller.util.UI.hide;
import static com.example.filipe.socketcontroller.util.UI.toast;
import static com.example.filipe.socketcontroller.util.UI.toggleVisibility;
import static com.example.filipe.socketcontroller.util.UI.unhide;
import static com.example.filipe.socketcontroller.util.UI.updateTime;

public class MainActivity extends Activity implements MessageApi.MessageListener, SensorEventListener , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
    private static final String TAG = "Main Activity Watch";

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

    /* ***************** */
    /* BACK-END (SENSOR) */
    /* ***************** */
    private float[] _rotationMatrix = new float[16];
    private float x;
    private float z;
    private boolean _sensor_running = false;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    /* ****************** */
    /* BACK-END (CONEXÃO) */
    /* ****************** */
    private GoogleApiClient _client;
    private Node _phone; // the connected device to send the message to
    public static final String WEAR_ACC_SERVICE = "acc";

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
        setContentView(R.layout.general_layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onConnectionSuspended(int i) { }

    @Override
    public void onStart()
    { super.onStart(); }

    @Override
    protected void onStop()
    {
        super.onStop();
        cpuWakeLock.release();
        _sensorManager.unregisterListener(this);
        _sensor_running = false;
        Wearable.MessageApi.removeListener(_client, this);
        _client.disconnect();
        Log.d("FIM","----FIM----");
    }

    @Override
    public void onResume()
    {
        super.onResume();

        new Thread(()->
        {
            _client = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            _client.connect();
            Wearable.MessageApi.addListener(_client, this);


            Log.i(TAG, "On resume called");

            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            cpuWakeLock.acquire();

            // _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_UI);
        }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        new Thread(()->
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
        }).start();
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* *********************** */
    /* COMUNICAÇÃO WEAR-MOBILE */
    /* *********************** */
    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(nodes -> {
                    for (Node node : nodes.getNodes())
                    {
                        _phone = node;
                        toast(getApplicationContext(),"Connected to mobile device!");
                    }
                });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    { toast(getApplicationContext(),"Connection failed! ("+connectionResult.toString()+")"); }

    private void sendMessage(String key)
    {
        new Thread(()->
        {
            if (_phone != null && _client!= null && _client.isConnected())
            {
                Wearable.MessageApi.sendMessage(
                        _client, _phone.getId(), WEAR_ACC_SERVICE + "" + key, null).setResultCallback(
                        sendMessageResult -> {
                            if (!sendMessageResult.getStatus().isSuccess())
                            {
                                Log.e(TAG, "Failed to send message with status code: "
                                        + sendMessageResult.getStatus().getStatusCode());
                            }
                            else
                            {
                                Log.d("SENDMESSAGE","MESSAGE SENT - "+key);
                                Log.d("SENDMESSAGE","status "+sendMessageResult.getStatus().isSuccess());
                            }
                        }
                );
            }
            else
            {
                Log.d("SENDMESSAGE","Failed to send a message!");
                Log.d("SENDMESSAGE","client = "+_client);
                Log.d("SENDMESSAGE","phone = "+_phone);
                Log.d("SENDMESSAGE","isConnected = "+_client.isConnected());
            }
        }).start();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        try
        {
            // Parse das mensagens
            // (formato: "EVENTO-XXX-YYY-ZZZ-WWW")
            String [] valores = messageEvent.getPath().split("-");

            // É obtido o identificador da mensagem
            String event = valores[0];

            switch(event)
            {
                // Mensagem com o consumo atual de cada pessoa
                // (formato: "Person consumption-Maria-22-Pedro-11-Joao-7")
                case "Person consumption":

                    int nrPessoas = (valores.length - 1 )/ 2;
                    for(int i = 0; i < nrPessoas; i++)
                    {
                        piePessoasAcum.incValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                    }
                    piePessoasAcum.startAnimation();
                    toast(getApplicationContext(),"Person consumption has been updated!");
                    break;

                // Mensagem com as percentagens de energias renováveis e não renováveis
                // (formato: "Energy-hidrica-32-termica-9-nao renovavel-70")
                case "Energy":
                    int tamanho = (valores.length - 1 )/ 2;
                    for(int i = 0; i < tamanho; i++)
                    {
                        pieEnergias.setValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                    }
                    pieEnergias.startAnimation();
                    toast(getApplicationContext(),"Energy data has been updated!");
                    break;

                // Mensagem com o consumo geral total
                // (formato: "Total overall power-900")
                case "Total overall power":
                    _consumo.setText(valores[1]);
                    toast(getApplicationContext(),"Total overall consumption has been updated!");
                    break;

                // Mensagem com o consumo atual de uma plug
                // (formato: "Plug consumption-plug1.local-500")
                case "Plug consumption":
                    String plugName = valores[1];
                    float value = Float.parseFloat(valores[2]);
                    linePlugs.addPoint(plugName,value);
                    piePlugsAcum.incValue(plugName,value);
                    toast(getApplicationContext(),"Plug consumption has been updated!");
                    break;

                // Mensagem inválida
                default:
                    Log.d("MESSAGERECEIVED","Error: evento `"+event+"` desconhecido");
                    toast(getApplicationContext(),"Invalid message received!");
                    break;
            }
        }
        catch(Exception e)
        {
            Log.i("Error",messageEvent.getPath());
            e.printStackTrace();
        }
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
        String time = _textInitTime.getText().toString()+"/";
        time += _textEndTime.getText().toString();
        switch(scheduleState)
        {
            case SELECT_TIME_START:
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_START);
                scheduleState++;
                break;

            case SELECT_TIME_END:
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_END);
                scheduleState++;
                break;

            case TIME_CONFIRMED:
                _buttonSchedule.setText(R.string.SET_SCHEDULE);
                scheduleState = SELECT_TIME_START;
                break;

            default:
                break;
        }
        sendMessage(time);
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
        linePlugs = (DynamicLineChart) findViewById(R.id.tab_power_plugs_current);
        mBarChart = (BarChart) findViewById(R.id.tab_stats_bar_test);
        fitToScreen(this,piePessoasAcum);
        fitToScreen(this,pieEnergias);
        fitToScreen(this,piePlugsAcum);
        fitToScreen(this,linePlugs);
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

        pieEnergias.setValue("Térmica",20);
        pieEnergias.setValue("Hídrica",10);
        pieEnergias.setValue("Fotovoltaica",50);

        mBarChart.addBar(new BarModel(2.3f, 0xFF123456));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(3.3f, 0xFF563456));
        mBarChart.addBar(new BarModel(1.1f, 0xFF873F56));
        mBarChart.addBar(new BarModel(2.7f, 0xFF56B7F1));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(0.4f, 0xFF1FF4AC));
        mBarChart.addBar(new BarModel(4.f,  0xFF1BA4E6));

        mBarChart.startAnimation();

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
        linePlugs.startAnimation();

        piePlugsAcum.incValue("plug1.local",30);
        piePlugsAcum.incValue("plug2.local",20);
        piePlugsAcum.incValue("plug3.local",20);
        piePlugsAcum.incValue("plug1.local",20);
        piePlugsAcum.incValue("plug5.local",20);
        piePlugsAcum.startAnimation();
    }

    public void handleLineChartClick(View v)
    {
        linePlugs.switchSeries();
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
                sendMessage(x+"#"+z);
                //Log.i("DEBUG",x+"#"+z);

                try
                { Thread.sleep(_sampling_diff); }
                catch (InterruptedException e)
                { e.printStackTrace(); }
            }
        }
    }
}