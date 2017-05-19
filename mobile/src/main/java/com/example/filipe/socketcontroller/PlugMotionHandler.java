package com.example.filipe.socketcontroller;

import android.content.Context;
import android.content.Intent;
import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;


/**
 * Created by Filipe on 16/05/2017.
 */
public class PlugMotionHandler extends Thread{

    private Context _appCtx;

    private static final String TAG = "PlugMotionHandler";
    private static final int N_LEDS = 12;
    private String _message = "{position:0, velocity:1000, orientation:1}";
    private int _position;
    private int _velocity;
    private int _ajustedVelocity;
    private int _orientation;
    private boolean _isRunning = true;
    private float _currentLED = -1;
    private int _period = 50;
    private int _resolution = 600; // factor to multiply by the number of leds, to increase the resolution from the 12

    private Intent _dataPackage;

    public final static String DATA_KEY = "XYPOINTS";

    public PlugMotionHandler(Context application_context){
        this._appCtx = application_context;
        _dataPackage = new Intent();
        //parsing debug
        try {
            JSONObject json_message = new JSONObject(_message);

            _currentLED      = json_message.getInt("position");
            _velocity        = json_message.getInt("velocity");
            _orientation     = json_message.getInt("orientation");

            _period          = N_LEDS*_velocity;
            _ajustedVelocity = Math.round(_period/(float)_resolution);
            Log.i(TAG,"adj_v:"+_ajustedVelocity+" res: "+_resolution);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void stopSimulation(){
        _isRunning = false;
    }

    private JSONObject getPlugData(){
        try {
            HttpRequest novo = new HttpRequest("http://merda.com", _appCtx);
            novo.start();
            novo.join();
            JSONObject data = novo.getData();
            return data;
        }catch(InterruptedException e){
            e.printStackTrace();
            return null;
        }
    }
    @Override
    public void run() {
        Log.i(TAG,"Thread running "+"resolution "+_resolution);
        int counter =0;
        long milis = System.currentTimeMillis();
        getPlugData();
        while (_isRunning){
            if(counter == (_resolution/N_LEDS)) {
                _currentLED = _currentLED + _orientation;
                _currentLED = _currentLED == 12 ? 0 : _currentLED;
                _currentLED = _currentLED == -1 ? 11 : _currentLED;

                counter = 0;
                Log.wtf(TAG, "Current LED: " + _currentLED+" "+counter+" tms diff (has to be the same as velocity) "+(System.currentTimeMillis()-milis));
                milis = System.currentTimeMillis();

            }else{
                counter++;

                float temp_val = ((float)counter/((float)_resolution/(float)N_LEDS)+_currentLED);
                double raio =12/(2*Math.PI);
                double angle = (temp_val/raio);

                double x = raio*Math.sin(angle);
                double y = raio*Math.cos(angle);
                //Log.i("point",x+","+y);
                _dataPackage.putExtra("x",x);
                _dataPackage.putExtra("y",y);
                _dataPackage.setAction(DATA_KEY);
                _appCtx.sendBroadcast(_dataPackage);
               // Log.i(TAG, "angle: "+(angle*180/Math.PI)+" x: "+x+" | y: "+y);
            }
            try {
                sleep(_ajustedVelocity);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
