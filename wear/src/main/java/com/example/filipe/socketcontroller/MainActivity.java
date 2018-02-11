package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.PieModel;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity implements MessageApi.MessageListener, SensorEventListener , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
    private static final String TAG = "Main Activity Watch";

    /* ***** */
    /* ????? */
    /* ***** */
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private int Primeiroconsumo;
    private int consumo;
    private int primeiro;
    private int consumoTotal;
    private int count = 0;
    private boolean changedStart;
    private boolean changedEnd;
    private Timer timer;
    private TimerTask checkSecond;
    //Done by Pedro to implement the schedule service
    private Button _buttonStart;
    private Button _buttonEnd;
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
    //private float _orientationVals[]={0,0,0};
    private boolean _sensor_running = false;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    /* ****************** */
    /* BACK-END (CONEXÃO) */
    /* ****************** */
    private GoogleApiClient _client;
    private Node _phone; // the connected device to send the message to
    //private int _count=0;
    public static final String WEAR_ACC_SERVICE = "acc";

    /* **************** */
    /* NAVEGAÇÃO E AÇÃO */
    /* **************** */
    private WearableNavigationDrawer navDrawer;
    private WearableActionDrawer actDrawer;
    private Menu actMenu;
    private MenuInflater actMenuInflater;
    private static final int NONE = -1;
    private Tab[] tabs;

    /* ************** */
    /* START/STOP TAB */
    /* ************** */
    private MenuItem itemToggleSensor;
    private TextView textSensorState;
    private int _factor;
    private CheckBox _leftHanded;

    /* ************ */
    /* SCHEDULE TAB */
    /* ************ */
    private Button _buttonSchedule;
    private TextView _StartTime;
    private TextView _EndTime;
    private static final int SELECT_TIME_START = 0;
    private static final int SELECT_TIME_END = 1;
    private static final int TIME_CONFIRMED = 2;
    private int scheduleState = 0;
    private TimePicker InitialTime;
    private TimePicker EndTime;
    private LinearLayout chooseStartTime;
    private LinearLayout chooseEndTime;

    /* ********** */
    /* STATS1 TAB */
    /* ********** */
    private PieChart mPieChart;
    private String [] ChartColor = new String[4];
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

        ChartColor[0] = "#FE6DA8";
        ChartColor[1] = "#56B7F1";
        ChartColor[2] = "#CDA67F";
        ChartColor[3] = "#FED70E";

        seconds = 0;
        Primeiroconsumo=0;
        consumo = 0;
        primeiro = 0;
        consumoTotal = 0;

        _sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        _sensor = _sensorManager.getDefaultSensor( Sensor.TYPE_ORIENTATION);
        _last_push = System.currentTimeMillis();

        actDrawer = (WearableActionDrawer) findViewById(R.id.bottom_action_drawer);
        actMenu = actDrawer.getMenu();
        actMenuInflater = getMenuInflater();

        // Possibilita a navegação pelos tabs presentes no TabAdapter
        navDrawer = (WearableNavigationDrawer) findViewById(R.id.top_navigation_drawer);
        navDrawer.setAdapter(new TabAdapter(this));

        // Desenha o tab predefinido
        tabs = new Tab[WattappTabs.values().length];
        int initial = WattappTabs.DEFAULT.ordinal();
        tabs[initial] = new Tab(WattappTabs.DEFAULT);
        draw(tabs[initial]);
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
        Wearable.MessageApi.removeListener(_client, this);
        _client.disconnect();
    }

    @Override
    public void onResume()
    {
        super.onResume();

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
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

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

    /* *********************** */
    /* COMUNICAÇÃO WEAR-MOBILE */
    /* *********************** */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>()
                {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult nodes)
                    {
                        for (Node node : nodes.getNodes())
                        { _phone = node; }
                        Log.i(TAG,"watch connected");
                    }
                });
        toast("Connected successfully!");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    { toast("Connection failed! ("+connectionResult.toString()+")"); }

    private void sendMessage(String key)
    {
        if (_phone != null && _client!= null && _client.isConnected())
        {
            Wearable.MessageApi.sendMessage(
                    _client, _phone.getId(), WEAR_ACC_SERVICE + "" + key, null).setResultCallback(
                    new ResultCallback<MessageApi.SendMessageResult>()
                    {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult)
                        {
                            if (!sendMessageResult.getStatus().isSuccess())
                            {
                                Log.e(TAG, "Failed to send message with status code: "
                                        + sendMessageResult.getStatus().getStatusCode());
                            }
                            else
                            {
                                Log.d("SENDMESSAGE","MESSAGE SENT!");
                                //Log.d("SENDMESSAGE","status "+sendMessageResult.getStatus().isSuccess());
                            }
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
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        try
        {
            toast("Message received!");
            String [] valores = messageEvent.getPath().split("-");
            if(valores.length > 1)
            {
                mPieChart.clearChart();
                int tamanho = (valores.length - 1 )/ 2;
                for(int i = 0; i < tamanho; i++)
                {
                    mPieChart.addPieSlice(new PieModel(valores[i*2+1], Float.parseFloat(valores[i*2+2]), Color.parseColor(ChartColor[mPieChart.getChildCount()])));
                }
                mPieChart.startAnimation();
            }
            else
            {
                String power = messageEvent.getPath();
                _consumo.setText(power);
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
    {
        if(chooseStartTime.getVisibility() == LinearLayout.GONE)
        { chooseStartTime.setVisibility(LinearLayout.VISIBLE); }
        else
        { chooseStartTime.setVisibility(LinearLayout.GONE); }
    }

    public void showEndPicker(View v)
    {
        if(chooseEndTime.getVisibility() == LinearLayout.GONE)
        { chooseEndTime.setVisibility(LinearLayout.VISIBLE); }
        else
        { chooseEndTime.setVisibility(LinearLayout.GONE); }
    }

    public void handleSensorClick(MenuItem item)
    {
        if(!_sensor_running)
        {
            itemToggleSensor.setTitle(R.string.STOP_SENSOR);
            textSensorState.setText(R.string.SENSOR_ON);

            _factor = _leftHanded.isChecked()? -1 : 1;
            _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_FASTEST);
            _sensor_running = true;
            pushThread = new PushThread();
            pushThread.start();
        }
        else
        {
            //cpuWakeLock.release();
            itemToggleSensor.setTitle(R.string.START_SENSOR);
            textSensorState.setText(R.string.SENSOR_OFF);

            _sensorManager.unregisterListener(this);
            _sensor_running = false;

            try
            { pushThread.join(); }
            catch (InterruptedException e)
            { e.printStackTrace(); }
        }
    }

    public void handleQuitClick(MenuItem item)
    {
        cpuWakeLock.release();
        _sensorManager.unregisterListener(this);
        _sensor_running = false;
        this.finish();
    }

    public void handleScheduleButton(View v)
    {
        String time = _StartTime.getText().toString()+"/";
        time += _EndTime.getText().toString();
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

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* *** */
    /* I/O */
    /* *** */

    private void toast(String s)
    { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }


    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ************************** */
    /* NAVEGAÇÃO E AÇÕES DOS TABS */
    /* ************************** */

    /* Separador da aplicação (com respetivo layout) */
    private class Tab extends Fragment
    {
        private WattappTabs choice;

        public Tab(final WattappTabs choice)
        {
            this.choice = choice;
            final Bundle arguments = new Bundle();
            arguments.putSerializable("TAB",choice);
            this.setArguments(arguments);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState)
        {
            // Caso tenha um menu de ações
            if(choice.menu != NONE)
            {
                // São retiradas as ações presentes (de outras tabs)
                actMenu.clear();

                // O menu de ações é populado com as ações específicas do tab
                actMenuInflater.inflate(choice.menu,actMenu);
            }

            return inflater.inflate(choice.layout, container, false);
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState)
        {
        	/* São atualizados os IDs dos elementos de cada view */
            _x_acc          = (TextView) view.findViewById(R.id.x_text_field);
            _y_acc          = (TextView) view.findViewById(R.id.y_text_field);
            _z_acc          = (TextView) view.findViewById(R.id.z_text_field);
            _tms            = (TextView) view.findViewById(R.id.tms_text_field);
            _leftHanded     = (CheckBox) view.findViewById(R.id.checkLeftHanded);
            _buttonSchedule = (Button) view.findViewById(R.id.buttonSchedule);
            _buttonStart    = (Button) view.findViewById(R.id.buttonStart);
            _buttonEnd      = (Button) view.findViewById(R.id.buttonEnd);
            _StartTime      = (TextView) view.findViewById(R.id.HoraInicio);
            _EndTime        = (TextView) view.findViewById(R.id.HoraFim);
            _consumo        = (TextView) view.findViewById(R.id.ConsumoInsert);
            InitialTime     = (TimePicker) view.findViewById(R.id.InitialPicker);
            EndTime         = (TimePicker) view.findViewById(R.id.EndPicker);
            chooseStartTime = (LinearLayout) view.findViewById(R.id.PrimeiroTempo);
            chooseEndTime   = (LinearLayout) view.findViewById(R.id.UltimoTempo);
            mPieChart = (PieChart) view.findViewById(R.id.piechart);

            itemToggleSensor = actMenu.findItem(R.id.item_toggle_sensor);
            textSensorState = (TextView) view.findViewById(R.id.textSensorState);

            /* Respetivas configurações dos elementos de cada view */

            if(InitialTime != null)
            {
                InitialTime.setIs24HourView(true);
                InitialTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener()
                {
                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
                    {
                        String strHour = "";
                        String strMinute = "";

                        if(hourOfDay < 10) strHour += "0";
                        strHour += hourOfDay;

                        if(minute < 10) strMinute += "0";
                        strMinute += minute;

                        _StartTime.setText(strHour + ":" + strMinute);
                        changedStart = true;
                    }
                });
            }

            if(EndTime != null)
            {
                EndTime.setIs24HourView(true);
                EndTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener()
                {
                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
                    {
                        String strHour = "";
                        String strMinute = "";

                        if(hourOfDay < 10) strHour += "0";
                        strHour += hourOfDay;

                        if(minute < 10) strMinute += "0";
                        strMinute += minute;

                        _EndTime.setText(strHour + ":" + strMinute);
                        changedEnd = true;
                    }
                });
            }

            if(chooseStartTime != null)
            { chooseStartTime.setVisibility(LinearLayout.GONE); }

            if(chooseEndTime != null)
            { chooseEndTime.setVisibility(LinearLayout.GONE); }

            if(textSensorState != null)
            {
                if (_sensor_running)
                { textSensorState.setText(R.string.SENSOR_ON); }
            }

        }
    }

    /* Fornece acesso aos tabs do enum */
    private final class TabAdapter extends WearableNavigationDrawer.WearableNavigationDrawerAdapter
    {
        private final Context context;
        private WattappTabs currentTab = WattappTabs.DEFAULT;

        TabAdapter(final Context context)
        { this.context = context; }

        @Override
        public String getItemText(int index)
        { return context.getString(WattappTabs.values()[index].title); }

        @Override
        public Drawable getItemDrawable(int index)
        { return context.getDrawable(WattappTabs.values()[index].icon); }

        @Override
        public void onItemSelected(int index)
        {
        	// Busca o tab ao enum (correspondente ao índice)
            WattappTabs chosenTab = WattappTabs.values()[index];

            // Se for um tab diferente do atual
            if (chosenTab != currentTab)
            {
                // Caso não tenha estado anterior, é criada uma nova instância
                // (caso contrário, o estado anterior mantém-se)
                if(tabs[index] == null)
                { tabs[index] = new Tab(chosenTab); }

                draw(tabs[index]);

                currentTab = chosenTab;

                // Caso tenha menu de ações, é permitido que seja acedido
                if(chosenTab.menu != NONE)
                { actDrawer.unlockDrawer(); }

                // Caso contrário, não é permitido que seja acedido
                else
                { actDrawer.lockDrawerClosed(); }
            }
        }

        @Override
        public int getCount()
        { return WattappTabs.values().length; }
    }

    /* Desenha o novo tab */
    private void draw(Tab newTab)
    {
    	// Substitui o elemento 'fragment_container' pela view do tab
        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, newTab)
            .commit();
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

}
