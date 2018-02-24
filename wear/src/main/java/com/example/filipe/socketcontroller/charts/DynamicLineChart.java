package com.example.filipe.socketcontroller.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends ValueLineChart
{
    private HashMap<String,ValueLineSeries> values;
    private String current = "";
    private TextView legend;

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
    public String getCurrentKey()
    {
        return current;
    }

    public void setLegend(TextView legend)
    {
        this.legend = legend;
        legend.setOnClickListener((v)-> switchSeries());
    }
    private void add(String key)
    {
        values.put(key,new ValueLineSeries());
        values.get(key).setColor(Color.parseColor(colors[(values.size()-1) % colors.length]));

        if(legend == null)
        {
            addSeries(values.get(key));
        }
        else
        {
            if(current.equals(""))
            {
                addSeries(values.get(key));
                current = key;
                legend.setText(key);
            }
        }
    }
    public void remove(String key)
    {
        values.remove(key);
    }
    public void addPoint(String key,float point)
    {
        if(!contains(key))
        { add(key); }

        addPoint(key,getCurrentTime(),point);
    }
    public void addPoint(String key,String legend,float point)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).addPoint(new ValueLinePoint(legend,point));
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
    private void switchSeries()
    {
        if(values.size() > 0)
        {
            clearChart();
            List<Object> allKeys = Arrays.asList(values.keySet().toArray());
            int newIndex = (allKeys.indexOf(current) + 1) % values.size();
            current = (String) allKeys.get(newIndex);
            addSeries((ValueLineSeries) values.values().toArray()[newIndex]);
            if(legend != null)
            {
                legend.setText(current);
            }
        }
    }

}
