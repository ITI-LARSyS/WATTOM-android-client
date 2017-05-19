package com.example.filipe.socketcontroller;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

/**
 * Created by Filipe on 18/05/2017.
 */
public class HttpRequest extends Thread {

    private static final String TAG = "HTTPRequest";
    private Context _appCtx;
    private String _url;
    private RequestQueue _queue;
    private JSONObject _data;

    public HttpRequest(String url, Context application_context){
        this._url = url;
        this._appCtx = application_context;
        _queue = Volley.newRequestQueue(_appCtx);

    }

    public JSONObject getData(){
        if(_data!=null)
            return _data;
        else{
            try {
                return new JSONObject("{message:error no data}");
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private String buildRequest(){

        return _url;
    }

    private void parseData(String data) throws JSONException {
        _data = new JSONObject(data);

    }

    @Override
    public void run(){
        String request = "";

        request = buildRequest();;

        RequestFuture<String> future = RequestFuture.newFuture();
        StringRequest stringRequest = new StringRequest(Request.Method.GET, request,future,future);
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                5000,
                3,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        _queue.add(stringRequest);

        try {
            Log.e(TAG, "-----   running request  ------");
            String response = future.get();
            parseData(response);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
}
