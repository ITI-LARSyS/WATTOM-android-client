package com.example.filipe.socketcontroller;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Filipe on 16/05/2017.
 */
public class PlugMotionHandler extends Thread {

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

    private int _resolution = 200; // value to multiply by the number of leds
    public PlugMotionHandler(){

        try {
            JSONObject json_message = new JSONObject(_message);
            Log.i(TAG,"Json message "+json_message);
            _currentLED = json_message.getInt("position");
            _velocity   = json_message.getInt("velocity");
            _orientation = json_message.getInt("orientation");

            _period     = N_LEDS*_velocity;
            _ajustedVelocity = Math.round((float)_velocity*((float)N_LEDS/(float)_resolution));

            Log.i(TAG, "Adjusted velocity:"+_ajustedVelocity);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void stopSimulation(){
        _isRunning = false;
    }

    @Override
    public void run() {
        Log.i(TAG,"Thread running "+"resolution "+_resolution);
        int counter =0;
        while (_isRunning){
              if(counter == _resolution/N_LEDS) {
                _currentLED = _currentLED + _orientation;
                _currentLED = _currentLED == 12 ? 0 : _currentLED;
                _currentLED = _currentLED == -1 ? 11 : _currentLED;
                Log.wtf(TAG, "Current LED: " + _currentLED+" "+counter);
                counter = 0;
            }else{
                counter++;
                 float temp_val =((float)counter/((float)_resolution/(float)N_LEDS)+_currentLED);
               //   Log.i(TAG, "Current LED: " + temp_val+" "+counter);
            }

            try {
                sleep(_ajustedVelocity);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
