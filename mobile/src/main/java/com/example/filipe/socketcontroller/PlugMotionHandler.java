package com.example.filipe.socketcontroller;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Created by Filipe on 16/05/2017.
 */
public class PlugMotionHandler extends Thread{

    private Context _appCtx;

    private static final String TAG = "PlugMotionHandler";
    private static final int N_LEDS = 12;
    private static final String SERVER_URL = "http://192.168.8.113:3000/plug/2";
    private static final String TARGET = "target";


    private String _message = "[{position:0, velocity:400, orientation:1}]";

    // stuff to simulate the plug position in da software
    private int _position;
    private int _velocity;
    private int _ajustedVelocity;
    private int _orientation;
    private boolean _isRunning = true;
    private float _currentLED = -1;
    private int _period = 50;
    private int _resolution; // factor to multiply by the number of leds, to increase the resolution from the 12

    // used to broadcast the plug point
    private Intent _dataPackage;
    public final static String DATA_KEY = "XYPOINTS";

    //temp vars for the deploy
    private float _temp_val;// = ((float)counter/((float)_resolution/(float)N_LEDS)+_currentLED);
    private double _raio;// =12/(2*Math.PI);
    private double _angle;// = (temp_val/raio);

    //current position of the led
    private double _x;// = raio*Math.sin(angle);
    private double _y;// = raio*Math.cos(angle);

    // used to check the number of targets
    private int _led_target;
    private int _target;

    public PlugMotionHandler(Context application_context,int frequency, int target){
        this._appCtx = application_context;
        _dataPackage = new Intent();
        _ajustedVelocity = frequency;
        _led_target = target;
        //parsing debug
        try {
            JSONArray json_array = new JSONArray(_message);
            JSONObject json_message = json_array.getJSONObject(0);
            _currentLED      = json_message.getInt("position");
            _velocity        = json_message.getInt("velocity");
            _orientation     = json_message.getInt("orientation");
            _orientation     = _orientation==1?1:-1;
            _period          = N_LEDS*_velocity;
            //_ajustedVelocity = Math.round(_period/(float)_resolution);
            _resolution      = Math.round(_period/_ajustedVelocity);
           // Log.i(TAG,"adj_v:"+_ajustedVelocity+" res: "+_resolution);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void stopSimulation(){
        _isRunning = false;
    }

    public int get_ajustedVelocity() {
        return _ajustedVelocity;
    }

    public void set_target(int _target) {
        this._target = _target;
    }
    private void handlePlugMessage(JSONObject data){

        try {
            _currentLED = data.getInt("position");
            _velocity = data.getInt("velocity");
            _orientation = data.getInt("orientation");
            _orientation = _orientation == 1 ? 1 : -1;
            _period          = N_LEDS*_velocity;
            _resolution      = Math.round(_period/_ajustedVelocity);
            Log.i("ORIENTATION",": "+_orientation);
            //_ajustedVelocity = Math.round(_period/(float)_resolution);

        } catch(JSONException e){
            e.printStackTrace();
        }

       // Log.i(TAG,"adj_vel:"+_ajustedVelocity+" res: "+_resolution);
    }

    private long getPlugData(){
        long current_time = System.currentTimeMillis();
        try {
            HttpRequest novo = new HttpRequest(SERVER_URL, _appCtx);
            novo.start();
            novo.join();
            String data = novo.getData();
            JSONArray json_array = new JSONArray(data);
            JSONObject json_message = json_array.getJSONObject(_led_target);
            handlePlugMessage(json_message);

            long diff = System.currentTimeMillis()-current_time;
            // Log.i(TAG, "request duration:"+diff);
            return diff;
        }catch(InterruptedException e){
            e.printStackTrace();
            return 0;
        }catch (JSONException e) {
            e.printStackTrace();
            return 0;
        }
    }
    @Override
    public void run() {

        // HACK COMENTA MELHOR DEPOIS
        try {
            sleep(_led_target*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.i(TAG,"Thread running "+"resolution "+_resolution);
        int counter = 0;
        long milis2 = 0;
        long newVel = 0;
        long compensation = getPlugData();
        counter = Math.round((compensation*(_resolution/N_LEDS))/_velocity)*2;
        int limit = _resolution/N_LEDS;
        //Log.wtf(TAG, "compensation returned counter as: "+counter+ " ,lmit: "+limit);
        int total = 0;
        //Log.i(TAG,"total "+total+" "+_isRunning);
        _raio =12/(2*Math.PI);

        while (_isRunning){

            if(total<500){

                milis2 = System.currentTimeMillis();

                if(counter == limit) {
                    _currentLED = _currentLED + _orientation;
                    _currentLED = _currentLED == 12 ? 0 : _currentLED;
                    _currentLED = _currentLED == -1 ? 11 : _currentLED;
                   // Log.e(TAG, "Current LED: " + _currentLED+ " target "+_led_target);//+" "+counter+" tms diff (has to be the same as velocity) "+(System.currentTimeMillis()-milis));

                    counter = 1;
                }else{
                    counter++;

                    if(_orientation==1)
                        _temp_val = ((float)counter/((float)_resolution/(float)N_LEDS)+_currentLED);
                    else
                        _temp_val = (_currentLED-(float)counter/((float)_resolution/(float)N_LEDS));

                    _angle = (_temp_val/_raio);

                    _x = _raio*Math.sin(_angle);
                    _y = _raio*Math.cos(_angle);
                  //  Log.wtf(TAG, "teste-"+_led_target+","+_x+","+_y);
                    _dataPackage.putExtra("x",_x);
                    _dataPackage.putExtra("y",_y);
                    _dataPackage.putExtra(TARGET,_led_target);
                    _dataPackage.setAction(DATA_KEY+_led_target);
                    _appCtx.sendBroadcast(_dataPackage);
                }

                try {

                    newVel =(_ajustedVelocity-(System.currentTimeMillis()-milis2));
                    total++;
                    sleep(newVel);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }catch (IllegalArgumentException e){
                    e.printStackTrace();
                }
            }else{
//                Log.i(TAG,"------readjusting-------");
                compensation = getPlugData();
                counter = Math.round((compensation*(_resolution/N_LEDS))/_velocity)*2;
                limit = _resolution/N_LEDS;
                total = 0;
            }
        }

    }
}
