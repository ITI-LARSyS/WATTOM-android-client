package com.example.filipe.socketcontroller.util;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class Alarm
{
    private Calendar calendar;
    private TimerTask task;
    public Alarm(int hour, int minute, Runnable action)
    {
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY,hour);
        calendar.set(Calendar.MINUTE,minute);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        task = new TimerTask() {
            @Override
            public void run() {
                action.run();
            }
        };
    }
    public Alarm(int minute, Runnable action)
    {
        calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE,minute);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        task = new TimerTask() {
            @Override
            public void run() {
                action.run();
            }
        };
    }
    public void activate()
    {
        new Timer().schedule(task,calendar.getTimeInMillis() - System.currentTimeMillis());
    }
}
