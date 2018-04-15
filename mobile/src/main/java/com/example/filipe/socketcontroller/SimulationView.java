package com.example.filipe.socketcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.filipe.socketcontroller.util.UI;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by Filipe on 01/08/2017.
 */
public class SimulationView extends SurfaceView implements SurfaceHolder.Callback
{

    int height;
    int width;

    //float x;
    //float y;

    //    float x2;
    //float y2;
    private static final String TAG = "Simulation View";
    private ArrayList<Pair<Float,Float>> coords;
    private ArrayList<String> colors;
    private Paint[] p;
    private int radious;

    public SimulationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        coords = new ArrayList<>();
        colors = new ArrayList<>();
        getHolder().addCallback(this);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void onDraw(Canvas c){
        if(c!=null){
            c.drawColor(Color.parseColor("#E2E0DB"));
            drawCircles(c);
        }
    }

    @SuppressLint("WrongCall")
    public void requestRender(){
        System.gc(); // ainda n‹o se se Ž necess‡rio mas pronto s— para ter certeza chamamos o GC agora.. aqui Ž um lugar seguro
        Canvas c = null;
        SurfaceHolder sh = getHolder();
        try {
            c = sh.lockCanvas(null);
            synchronized (sh) {
                onDraw(c);
            }
        } finally {
            // do this in a finally so that if an exception is thrown
            // during the above, we don't leave the Surface in an
            // inconsistent state
            if (c != null) {
                sh.unlockCanvasAndPost(c);
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        // TODO Auto-generated method stub
        //	requestRender();

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        Log.e("COMP","COMP WIDGET CREATED");
        height = getHeight();
        width  = getWidth();
        radious = (height/2);
       /* p = new Paint[UI.colors.length];
        for(int i = 0; i < UI.colors.length ; i++)
        {
            p[i] = new Paint();
            p[i].setColor(Color.parseColor(UI.colors[i % UI.colors.length]));
        }*/
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub

    }

    private void drawCircles(Canvas c){


        Log.i(TAG,"height "+height+" width:"+width+" , radious: "+radious);

        for(int i = 0; i < coords.size(); i++)
        {
            if(colors.size() == coords.size() && colors.get(i) != null)
            {
                Paint p = new Paint();
                p.setColor(Color.parseColor(colors.get(i)));

                Pair<Float,Float> pair = coords.get(i);
                c.drawCircle(((pair.first*(radious/2.3f)))+radious, (((pair.second*-1)*(radious/2.3f)))+radious, 20f, p);

                //c.drawCircle((x2+2)*120, ((y2*-1)+2)*120, 20f, p);
            }
        }


        //c.drawCircle((x2+2), ((y2*-1)+2), 20f, p);

    }

    public void setCoords(int i, float x, float y)
    {
        if(coords == null) coords = new ArrayList<>();
        if(i < coords.size()) coords.set(i,Pair.create(x,y));
        else coords.add(Pair.create(x,y));
        requestRender();
    }

    public void setColor(int i, String color)
    {
        if(colors == null) colors = new ArrayList<>();
        if(i < colors.size()) colors.set(i,color);
        else colors.add(color);
    }


}
