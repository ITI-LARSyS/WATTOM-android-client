package com.example.filipe.socketcontroller;

import android.graphics.Color;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.Calendar;
import java.util.HashMap;

import static com.example.filipe.socketcontroller.UI.colors;

public class PlugLineChartValues extends HashMap<String,ValueLineSeries>
{
    private ValueLineChart chart;
    public PlugLineChartValues(ValueLineChart chart)
    {
        this.chart = chart;
        chart.startAnimation();
    }
    public void addPlug(String plugName)
    {
        put(plugName,new ValueLineSeries());
        get(plugName).setColor(Color.parseColor(colors[this.size()-1 % colors.length]));
        chart.addSeries(get(plugName));
    }
    public void removePlug(String plugName)
    {
        remove(plugName);
    }
    public void addPoint(String plugName,float point)
    {
        if(!containsPlug(plugName))
        { addPlug(plugName); }

        get(plugName).addPoint(new ValueLinePoint(
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
    public boolean containsPlug(String plugName)
    {
        return containsKey(plugName);
    }

}
