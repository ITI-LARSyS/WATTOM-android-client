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

    public void add(String index)
    {
        values.put(index,new ValueLineSeries());
        values.get(index).setColor(Color.parseColor(colors[values.size()-1 % colors.length]));
        addSeries(values.get(index));
    }
    public void remove(String plugName)
    {
        values.remove(plugName);
    }
    public void addPoint(String plugName,float point)
    {
        if(!contains(plugName))
        { add(plugName); }

        values.get(plugName).addPoint(new ValueLinePoint(
                getCurrentTime(),
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
    public boolean contains(String index)
    {
        return values.containsKey(index);
    }
}
