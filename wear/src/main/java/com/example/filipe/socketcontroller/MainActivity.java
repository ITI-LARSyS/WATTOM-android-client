package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends Activity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "Main Activity Watch";
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private Button _startSensorBtn;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    private GoogleApiClient _client;

    private boolean sensor_running = false;

    private Node _phone; // the connected device to send the message to
    //private int _count=0;

    public static final String WEAR_ACC_SERVICE = "WearAccService";

    private long _last_push;
    private int _sampling_diff = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                _x_acc          = (TextView) stub.findViewById(R.id.x_text_field);
                _y_acc          = (TextView) stub.findViewById(R.id.y_text_field);
                _z_acc          = (TextView) stub.findViewById(R.id.z_text_field);
                _tms            = (TextView) stub.findViewById(R.id.tms_text_field);
                _startSensorBtn = (Button) stub.findViewById(R.id.start_sensor_btn);

            }
        });

        _sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        _sensor = _sensorManager.getDefaultSensor( Sensor.TYPE_LINEAR_ACCELERATION);
        _last_push = System.currentTimeMillis();
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

        Log.i(TAG, "On resume called");
       // _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //Log.wtf(TAG,event.toString());

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        int val =4;

       // if(x>val||y>val||z>val) {

            _x_acc.setText(x+"");
            _y_acc.setText(y+"");
            _z_acc.setText(z+"");
            _tms.setText(event.timestamp+"");

          //  Log.i(TAG, "x:" + x + "  y:" + y + "  z:" + z + " Event: " + event.timestamp);
        //}
        if(System.currentTimeMillis()-_last_push>_sampling_diff){
            Log.i(TAG,"Sending data");
            _last_push = System.currentTimeMillis();
          //  float[] data = {x,y};
            sendMessage(y+"#"+z);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void handleSensorClick(View v){

        if(v.getId() == R.id.start_sensor_btn && !sensor_running) {
            _startSensorBtn.setText("Stop Sensor");
            _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_UI);
            Log.i(TAG, "Aqui starting sensor");
            sensor_running = true;
        }else{
            _startSensorBtn.setText("Start Sensor");
            _sensorManager.unregisterListener(this);
            sensor_running = false;

        }
    }

    public void handleQuitClick(View v){
        _sensorManager.unregisterListener(this);
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
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void sendMessage(String key){
        if (_phone != null && _client!= null && _client.isConnected()) {
            //   Log.d(TAG, "-- " + _client.isConnected());
            Wearable.MessageApi.sendMessage(
                    _client, _phone.getId(), WEAR_ACC_SERVICE + "--" + key, null).setResultCallback(

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

    }
}
