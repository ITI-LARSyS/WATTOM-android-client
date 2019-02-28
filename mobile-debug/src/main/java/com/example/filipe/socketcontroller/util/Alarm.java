package com.example.filipe.socketcontroller.util;

import android.util.Log;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Alarm
{
    private Calendar calendar;
    private TimerTask task;
    private boolean repeating;
    private long interval;
    private int plug;
    private boolean active = true;
    public Alarm(int hour, int minute, Runnable action, boolean repeatingint, int plug)
    {
        this.plug=plug;
        this.repeating = repeating;
        if(!repeating)
        {
            Log.i("Alarm","Creating alarm with hour");
            calendar = Calendar.getInstance();
            if(hour < calendar.get(Calendar.HOUR_OF_DAY)){
                calendar.set(Calendar.DAY_OF_YEAR,calendar.get(Calendar.DAY_OF_YEAR)+1);
                Log.i("Alarm", "previous time, adding one day");
            }
            calendar.set(Calendar.HOUR_OF_DAY,hour);
            calendar.set(Calendar.MINUTE,minute);
            calendar.set(Calendar.SECOND,0);
            calendar.set(Calendar.MILLISECOND,0);
            task = new TimerTask()
            {
                @Override
                public void run()
                {
                    action.run();
                }
            };
        }
        else
        {
            interval = TimeUnit.HOURS.toMillis(hour) + TimeUnit.MINUTES.toMillis(minute);
        }
    }
    public Alarm(int minute, Runnable action, boolean repeating)
    {
        if(!repeating)
        {
            calendar = Calendar.getInstance();
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            task = new TimerTask() {
                @Override
                public void run() {
                    action.run();
                }
            };
        }
        else
        {
            interval = TimeUnit.MINUTES.toMillis(minute);
        }
    }
    public int getPlug(){
        return this.plug;
    }
    public void setActive(boolean active){
        this.active = false;
    }
    public boolean isActive(){
        return this.active;
    }
    public void activate()
    {
        Log.i("Alarm ",calendar.getTimeInMillis() +" - "+ System.currentTimeMillis());
        if(!repeating) new Timer().schedule(task,calendar.getTimeInMillis() - System.currentTimeMillis());
        else new Timer().schedule(task,0,interval);
    }
}
