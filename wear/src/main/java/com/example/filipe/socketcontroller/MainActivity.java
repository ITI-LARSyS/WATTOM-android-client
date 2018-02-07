package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.chooser.ChooserTarget;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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

import java.util.TimerTask;
import java.util.Timer;


public class MainActivity extends Activity implements MessageApi.MessageListener, SensorEventListener , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "Main Activity Watch";
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private CheckBox _leftHanded;
    private Button _startSensorBtn;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    private Timer timer;
    private TimerTask checkSecond;


    //Done by Pedro to implement the schedule service
    private Button _buttonStart;
    private Button _buttonEnd;
    private Button _buttonSchedule;
    private TextView _StartTime;
    private TextView _EndTime;
    private TextView _consumo;
    private int seconds;


    private int Primeiroconsumo;
    private int consumo;
    private int primeiro;
    private int consumoTotal;
    private int count = 0;
    private TimePicker InitialTime;
    private TimePicker EndTime;
    private LinearLayout chooseStartTime;
    private LinearLayout chooseEndTime;
    private boolean changedStart;
    private boolean changedEnd;
    private String [] ChartColor = new String[4];

    private GoogleApiClient _client;

    private boolean _sensor_running = false;

    private Node _phone; // the connected device to send the message to
    //private int _count=0;

    public static final String WEAR_ACC_SERVICE = "acc";

    private long _last_push;
    private long _sampling_diff = 40;        // alterei o sampling rate aqui
    //private float _orientationVals[]={0,0,0};
    float[] _rotationMatrix = new float[16];
    float x;
    float z;
    private int _factor;
    private int vez = 0;

    PowerManager.WakeLock cpuWakeLock;
    private PieChart mPieChart;

    private PushThread pushThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ChartColor[0] = "#FE6DA8";
        ChartColor[1] = "#56B7F1";
        ChartColor[2] = "#CDA67F";
        ChartColor[3] = "#FED70E";

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                _x_acc          = (TextView) stub.findViewById(R.id.x_text_field);
                _y_acc          = (TextView) stub.findViewById(R.id.y_text_field);
                _z_acc          = (TextView) stub.findViewById(R.id.z_text_field);
                _tms            = (TextView) stub.findViewById(R.id.tms_text_field);
                _startSensorBtn = (Button) stub.findViewById(R.id.start_sensor_btn);
                _leftHanded     = (CheckBox) stub.findViewById(R.id.left_handed);
                _buttonSchedule = (Button) stub.findViewById(R.id.buttonSchedule);
                _buttonStart    = (Button) stub.findViewById(R.id.buttonStart);
                _buttonEnd      = (Button) stub.findViewById(R.id.buttonEnd);
                _StartTime      = (TextView) stub.findViewById(R.id.HoraInicio);
                _EndTime        = (TextView) stub.findViewById(R.id.HoraFim);
                _consumo        = (TextView) stub.findViewById(R.id.ConsumoInsert);

                InitialTime         = (TimePicker) stub.findViewById(R.id.InitialPicker);
                InitialTime.setIs24HourView(true);
                InitialTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                        _StartTime.setText(hourOfDay+":"+minute);
                        changedStart = true;

                    }
                });
                EndTime         = (TimePicker) stub.findViewById(R.id.EndPicker);
                EndTime.setIs24HourView(true);
                EndTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {

                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                        _EndTime.setText(hourOfDay+":"+minute);
                        changedEnd = true;
                    }
                });

                chooseStartTime = (LinearLayout) stub.findViewById(R.id.PrimeiroTempo);
                chooseEndTime   = (LinearLayout) stub.findViewById(R.id.UltimoTempo);
                chooseStartTime.setVisibility(LinearLayout.GONE);
                chooseEndTime.setVisibility(LinearLayout.GONE);

                mPieChart = (PieChart) stub.findViewById(R.id.piechart);
            }
        });
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
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        _client.disconnect();
    }

    @Override
    public void onResume() {
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
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
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

    public void ShowStartPicker(View v){
        if(chooseStartTime.getVisibility() == LinearLayout.GONE){
            chooseStartTime.setVisibility(LinearLayout.VISIBLE);
        }else{
            chooseStartTime.setVisibility(LinearLayout.GONE);
        }
    }

    public void ShowEndPicker(View v){
        if(chooseEndTime.getVisibility() == LinearLayout.GONE){
            chooseEndTime.setVisibility(LinearLayout.VISIBLE);
        }else{
            chooseEndTime.setVisibility(LinearLayout.GONE);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void handleSensorClick(View v){

        if(v.getId() == R.id.start_sensor_btn && !_sensor_running) 
        {
            _factor = _leftHanded.isChecked()?-1:1;
            _startSensorBtn.setText("Stop Sensor");
            _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_FASTEST);
            Log.i(TAG, "Aqui starting sensor");
            _sensor_running = true;
            pushThread = new PushThread();
            pushThread.start();
        }
        else
        {
            //cpuWakeLock.release();
            _startSensorBtn.setText("Start Sensor");
            _sensorManager.unregisterListener(this);
            _sensor_running = false;
            try
            {
                pushThread.join();
            }
            catch (InterruptedException e)
            { e.printStackTrace(); }
        }
    }

    public void handleQuitClick(View v){
        cpuWakeLock.release();
        _sensorManager.unregisterListener(this);
        _sensor_running = false;
        this.finish();

    }

    public void handleSendReadingsClick(View v){
        Log.i(TAG, "Sending message");
        sendMessage("not a message");
    }
    //
    // WEAR COMMUNICATION STUFF
    //
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                        for (Node node : nodes.getNodes()) {
                            _phone = node;
                        }
                        Log.i(TAG,"watch connected");
                    }
                });
            toast("Connected successfully!");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) 
    {
        toast("Connection failed! ("+connectionResult.toString()+")");
    }

    private void sendMessage(String key){
        if (_phone != null && _client!= null && _client.isConnected()) {
            //   Log.d(TAG, "-- " + _client.isConnected());
            Wearable.MessageApi.sendMessage(
                    _client, _phone.getId(), WEAR_ACC_SERVICE + "" + key, null).setResultCallback(

                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {

                            if (!sendMessageResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to send message with status code: "
                                        + sendMessageResult.getStatus().getStatusCode());
                            }else{
                              //  Log.i(TAG,"status "+sendMessageResult.getStatus().isSuccess());
                            }
                        }
                    }
            );
        }
        else
        {
            Log.d("SENDMESSAGE","Failed to send a message!");
        }

    }

    public void Schedule(View v){
        if(v.getId() ==  R.id.buttonSchedule){
            String StartTime = _StartTime.getText().toString()+"/";
            StartTime += _EndTime.getText().toString();
            //Log.i("HOUR",StartTime);
            if(vez == 0){
                _buttonSchedule.setText("Confirm Start");
                vez++;
            }else if(vez == 1){
                _buttonSchedule.setText("Confirm End");
                vez++;
            }else{
                _buttonSchedule.setText("Set Schedule");
                vez = 0;
            }
            sendMessage(StartTime);
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        try{
            toast("Message received!");
            String [] valores = messageEvent.getPath().split("-");
            if(valores.length > 1){
                mPieChart.clearChart();
                int tamanho = (valores.length - 1 )/ 2;
                for(int i = 0; i < tamanho; i++){
                    mPieChart.addPieSlice(new PieModel(valores[i*2+1], Float.parseFloat(valores[i*2+2]), Color.parseColor(ChartColor[mPieChart.getChildCount()])));
                }
                mPieChart.startAnimation();
            }else{
                String power = messageEvent.getPath();
                _consumo.setText(power);
            }
        }catch(Exception e){
            Log.i("Error",messageEvent.getPath());
            e.printStackTrace();
        }

    }

    private class PushThread extends Thread{

        public void run(){

            while(_sensor_running){

                sendMessage(x+"#"+z);
                //Log.i("DEBUG",x+"#"+z);

                try {
                    Thread.sleep(_sampling_diff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

}
