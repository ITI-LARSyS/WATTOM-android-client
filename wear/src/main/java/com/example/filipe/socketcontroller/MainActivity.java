package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.example.filipe.socketcontroller.util.UI;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

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
    private DynamicLineChart lineDevices;
    private DynamicPieChart piePlugsAcum;
    private DynamicPieChart pieEnergias;
    private DynamicLineChart linePessoasMedia;
    private float mediaConsumoPessoa = -1;


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

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onConnectionSuspended(int i) { }

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
        Wearable.MessageApi.removeListener(_client, this);
        _client.disconnect();
        Log.d("FIM","----FIM----");
    }

    @Override
    public void onResume()
    {
        super.onResume();

        paused = false;

        _client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        _client.connect();
        Wearable.MessageApi.addListener(_client, this);
        if(cpuWakeLock.isHeld()) cpuWakeLock.release();


        Log.i(TAG, "On resume called");

       // PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
       // cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
       //  cpuWakeLock.acquire();

        // _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_UI);
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
            //Log.d("XYZ","x:"+event.values[0]+",y:"+event.values[1]+",z:"+event.values[2]);
            // _y_acc.setText(z+"");

//            Log.i("DEBUG",x+","+z);

            //Log.i(TAG,"sending data form watch");
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
                       // toast(getApplicationContext(),"Connected to `"+node.getDisplayName()+"`!");
                    }
                });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    { toast(getApplicationContext(),"Connection failed! ("+connectionResult.toString()+")"); }

    private void sendMessage(String key)
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
                           /* else
                            {
                                Log.d("SENDMESSAGE","MESSAGE SENT - "+key);
                                Log.d("SENDMESSAGE","status "+sendMessageResult.getStatus().isSuccess());
                            }*/
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

            // Haptic feedback para a ocorrência de um evento
            vibrator.vibrate(250);

            switch(event)
            {
                // Mensagem com o consumo atual de cada pessoa
                // (formato: "Person consumption-Maria-22-Pedro-11-Joao-7")
                case "Person consumption":

                    int nrPessoas = (valores.length - 1 )/ 2;
                    for(int i = 0; i < nrPessoas; i++)
                    {
                        if(mediaConsumoPessoa != -1)
                        {
                            mediaConsumoPessoa += Float.parseFloat(valores[i*2+2]);
                            mediaConsumoPessoa /= 2;
                        }
                        else
                        {
                            mediaConsumoPessoa += Float.parseFloat(valores[i*2+2]);
                        }
                        piePessoasAcum.setValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                        Log.d("PERSONS","Consumption of "+valores[i*2+1]+": "+valores[i*2+2]);
                    }
                    linePessoasMedia.addPoint(mediaConsumoPessoa);
                    notify("Person consumption","Updated data!");
                    Log.d("PERSONS","Person consumption has been updated!");
                    break;

                // Mensagem com as percentagens de energias renováveis e não renováveis
                // (formato: "Energy-hidrica-32-termica-9-nao renovavel-70")
                case "Energy":
                    int tamanho = (valores.length - 1 )/ 2;
                    for(int i = 0; i < tamanho; i++)
                    {
                        pieEnergias.setValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                        Log.d("ENERGY","Energia "+valores[i*2+1]+": "+valores[i*2+2]);
                    }
                    notify("Energy","Updated data!");
                    Log.d("ENERGY","Energy data has been updated!");
                    break;

                // Mensagem com o consumo geral total
                // (formato: "Total overall power-900")
                case "Total overall power":
                    _consumo.setText(valores[1]);
                    notify("Overall power  consumption","Updated data!");
                    Log.d("PLUGS","Total overall consumption (current): "+valores[1]);
                    break;

                // Mensagem com o consumo atual de uma plug
                // (formato: "Plug consumption-Sala de estar-500")
                case "Plug consumption":
                    String plugName = valores[1];
                    float value = Float.parseFloat(valores[2]);
                    linePlugs.addPoint(plugName,value);
                    piePlugsAcum.setValue(plugName,value);
                    notify("Plug consumption","Updated data!");
                    Log.d("PLUGS","Consumption of plug"+plugName+".local: "+value);
                    break;

                // Mensagem a indicar o 'enable' de uma plug
                // (formato: "Plug start-Quarto de dormir")
                case "Plug start":
                    String plug = valores[1];
                    navigationDrawer.setCurrentItem(TabConfig.PLUGS.ordinal(),true);
                   // linePlugs.add(plug);
                    linePlugs.switchSeries(plug);
                    break;

                // Mensagem a indicar o 'enable' de um device
                // (formato: "Device start-Forno3000MX")
                case "Device start":
                    String device = valores[1];
                    navigationDrawer.setCurrentItem(TabConfig.DEVICES.ordinal(),true);
                //    lineDevices.add(device);
                    lineDevices.switchSeries(device);
                    break;

                // Mensagem a indicar o consumo de um device
                // (formato: "Device consumption-Forno3000MX-13")
                case "Device consumption":
                    String deviceName = valores[1];
                    float deviceConsumption = Float.parseFloat(valores[2]);
                    lineDevices.addPoint(deviceName,deviceConsumption);
                    notify("Device consumption","Updated data!");
                    break;

                case "START":
                    inStudy = true;
                    notify("Wattapp","A new study was started!");
                    break;

                case "STOP":
                    inStudy = false;
                    notify("Wattapp","Study ended!");
                    break;

                // Mensagem inválida
                default:
                    Log.d("ERROR","Error: evento `"+event+"` desconhecido");
                    notify("Wattapp","Invalid message received!");
                    break;
            }
        }
        catch(Exception e)
        {
            Log.i("Error",messageEvent.getPath());
            e.printStackTrace();
            notify("Wattapp","Invalid message received!");
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
                toast(this,"Scheduled! (follow the target to confirm)");
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
        linePessoasMedia = (DynamicLineChart) findViewById(R.id.linepessoasmedia);
        piePessoasAcum = (DynamicPieChart) findViewById(R.id.tab_power_pessoas_total);
        pieEnergias = (DynamicPieChart) findViewById(R.id.tab_energias);
        piePlugsAcum = (DynamicPieChart) findViewById(R.id.tab_power_plugs_total);
        linePlugs = (DynamicLineChart) findViewById(R.id.linechartplugs);
        lineDevices = (DynamicLineChart) findViewById(R.id.linechartdevices);
        fitToScreen(this,piePessoasAcum);
        fitToScreen(this,pieEnergias);
        fitToScreen(this,piePlugsAcum);

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
        piePessoasAcum.setOnLongClickListener((v)->
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

        linePessoasMedia.enableExtra();
        linePessoasMedia.addPoint("Manel","14:01",10);
        linePessoasMedia.addPoint("Afonso","14:01",20);
        linePessoasMedia.addPoint("Dionísio","14:01",30);
        mediaConsumoPessoa += 20;
        linePessoasMedia.addPoint(mediaConsumoPessoa);

        linePessoasMedia.addPoint("Manel","14:02",11);
        mediaConsumoPessoa += 11;
        mediaConsumoPessoa /= 2;
        linePessoasMedia.addPoint("Afonso","14:02",19);
        mediaConsumoPessoa += 19;
        mediaConsumoPessoa /= 2;
        linePessoasMedia.addPoint("Dionísio","14:02",20);
        mediaConsumoPessoa += 20;
        mediaConsumoPessoa /= 2;
        linePessoasMedia.addPoint(mediaConsumoPessoa);

        linePessoasMedia.addPoint("Manel","14:03",15);
        mediaConsumoPessoa += 15;
        mediaConsumoPessoa /= 3;
        linePessoasMedia.addPoint("Afonso","14:03",3);
        mediaConsumoPessoa += 3;
        mediaConsumoPessoa /= 2;
        linePessoasMedia.addPoint("Dionísio","14:03",12);
        mediaConsumoPessoa += 12;
        mediaConsumoPessoa /= 2;
        linePessoasMedia.addPoint(mediaConsumoPessoa);

        linePessoasMedia.setOnLongClickListener((v)->
        {
            navigationDrawer.setCurrentItem(TabConfig.PESSOAS.ordinal(),true);
            return true;
        });

        piePlugsAcum.incValue("Sala de estar",30);
        piePlugsAcum.incValue("Quarto de dormir",20);
        piePlugsAcum.incValue("Hall de entrada",20);
        piePlugsAcum.incValue("Sala de estar",20);
        piePlugsAcum.incValue("Escritório",20);
        piePlugsAcum.setOnLongClickListener((v)->
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
        //if(!paused) toast(getApplicationContext(),title + " - " + message);
        if(paused) UI.notify(this,MainActivity.class,title,message);
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