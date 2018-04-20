package com.example.filipe.socketcontroller.util;

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
    public Alarm(int hour, int minute, Runnable action, boolean repeating)
    {
        this.repeating = repeating;
        if(!repeating)
        {
            calendar = Calendar.getInstance();
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
    public void activate()
    {
        if(!repeating) new Timer().schedule(task,calendar.getTimeInMillis() - System.currentTimeMillis());
        else new Timer().schedule(task,0,interval);
    }
}
