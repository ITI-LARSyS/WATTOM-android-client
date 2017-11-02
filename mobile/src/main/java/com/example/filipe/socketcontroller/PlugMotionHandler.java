package com.example.filipe.socketcontroller;

import android.content.Context;
import android.content.Intent;
import android.util.Log;


import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

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
    private  String _server_url = "http://192.168.8.113:3000/plug/3";
   // private  String _server_url = "http://192.168.1.6:3000/plug/3";

    private static final String TARGET = "target";


    private String _message = "[{position:0, velocity:400, orientation:1}]";

    // stuff to simulate the plug position in da software
    private int _position;
    private int _velocity;
    private int _ajustedVelocity;
    private int _orientation;
    private boolean _isRunning = true;

    public float _currentLED = -1;
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

    HttpRequest _request;
    RequestQueue _queue;


    public PlugMotionHandler(Context application_context, int frequency, int target, String url, RequestQueue queue){
        this._appCtx = application_context;
        _dataPackage = new Intent();
        _ajustedVelocity = frequency;
        _led_target = target;
        _server_url = url;
        _queue = queue;
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

    public PlugMotionHandler(Context application_context,int frequency, int target, String url){
        this._appCtx = application_context;
        _dataPackage = new Intent();
        _ajustedVelocity = frequency;
        _led_target = target;
        _server_url = url;
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

    public double[] getPosition(){
        return new double[]{_x, _y};
    }

    public void stopSimulation(){
        _isRunning = false;
    }

    public float getCurrentLed(){
        return _currentLED;
    }

    public int get_ajustedVelocity() {
        return _ajustedVelocity;
    }

    public void set_target(int _target) {
        this._target = _target;
    }

    public void handlePlugMessage(JSONObject data){

        try {
            _currentLED = data.getInt("position");
            _velocity = data.getInt("velocity");
            _orientation = data.getInt("orientation");
            _orientation = _orientation == 1 ? 1 : -1;
            _period          = N_LEDS*_velocity;
            _resolution      = Math.round(_period/_ajustedVelocity);
            _currentLED = _currentLED - _orientation;

            _currentLED = _currentLED == 12 ? 0 : _currentLED;
            _currentLED = _currentLED == -1 ? 11 : _currentLED;
           // Log.i("ORIENTATION",": "+_orientation);
            //_ajustedVelocity = Math.round(_period/(float)_resolution);

        } catch(JSONException e){
            e.printStackTrace();
        }

       // Log.i(TAG,"adj_vel:"+_ajustedVelocity+" res: "+_resolution);
    }

    public void forceUpdate(){
        getPlugData();
    }

    private long getPlugData(){
        long current_time = System.currentTimeMillis();
        try {
            _request = new HttpRequest(_server_url, _appCtx,_queue);
            _request.start();
            //Log.i(TAG,"--- RUNNING COLOR REQUEST : target "+_led_target+" ---");
            _request.join();
            String data = _request.getData();
            JSONArray json_array = new JSONArray(data);
            JSONObject json_message = json_array.getJSONObject(_led_target);
            handlePlugMessage(json_message);
            return  (System.currentTimeMillis()-current_time);
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
            sleep(_led_target*10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.wtf(TAG,"RUNNING THREAD "+_led_target);
        int counter = 0;
        long milis2 = 0;
        long newVel = 0;
        long compensation = getPlugData();
        counter = Math.round((compensation*(_resolution/N_LEDS))/_velocity)*2;
        int limit = _resolution/N_LEDS;
        int total = 0;
        _raio =12/(2*Math.PI);

        int max = 400;  // alterei o total aqui

        Log.wtf(TAG,"Limit:"+limit+" Resolution:"+_resolution);

        while (_isRunning){
            if(total<_resolution*1.5){
                milis2 = System.currentTimeMillis();
                if(counter == limit) {
                    _currentLED = _currentLED + _orientation;
                    _currentLED = _currentLED == 12 ? 0 : _currentLED;
                    _currentLED = _currentLED == -1 ? 11 : _currentLED;
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
                }
                try {
                    newVel =_ajustedVelocity-(System.currentTimeMillis()-milis2);
                    total++;
                    newVel=newVel<0?0:newVel;
                   if(newVel>0)
                       sleep(newVel);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }catch (IllegalArgumentException e){
                    e.printStackTrace();
                }
            }else{
               // Log.i(TAG,"------readjusting-------");
                compensation = getPlugData();
                counter = Math.round((compensation*(_resolution/N_LEDS))/_velocity)*2;
                limit = _resolution/N_LEDS;
                total = 0;
            }
        }
    }
}
