package com.example.filipe.socketcontroller.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.Calendar;
import java.util.HashMap;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends ValueLineChart
{
    private HashMap<String,ValueLineSeries> values;

    public DynamicLineChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        values = new HashMap<>();
    }
    public DynamicLineChart(Context context)
    {
        super(context);
        values = new HashMap<>();
    }

    private void add(String key)
    {
        values.put(key,new ValueLineSeries());
        values.get(key).setColor(Color.parseColor(colors[values.size() % colors.length]));
        addSeries(values.get(key));
    }
    public void remove(String key)
    {
        values.remove(key);
    }
    public void addPoint(String key,float point)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).addPoint(new ValueLinePoint(
                getCurrentTime(),
                point));
    }
    public void addPoint(String key,String legend,float point)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).addPoint(new ValueLinePoint(
                legend,
                point));
    }
    private String getCurrentTime()
    {
        Calendar now = Calendar.getInstance();
        String time = "";
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minutes = now.get(Calendar.MINUTE);

        if(hour < 10)
        { time += "0"; }
        time += hour + ":";
        if(minutes < 10)
        { time += "0"; }
        time += minutes;

        return time;
    }
    public boolean contains(String key)
    {
        return values.containsKey(key);
    }
}
