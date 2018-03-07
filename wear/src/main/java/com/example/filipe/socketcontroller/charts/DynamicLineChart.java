package com.example.filipe.socketcontroller.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends ValueLineChart
{
    private HashMap<String,ValueLineSeries> values;
    private String current = "";
    private TextView legend;
    private int currentIndex = 0;

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
    public void add(String key)
    {
        if(!contains(key))
        {
            values.put(key, new ValueLineSeries());
            values.get(key).setColor(Color.parseColor(colors[(values.size() - 1) % colors.length]));

            if (legend == null)
            {
                addSeries(values.get(key));
            }
            else
            {
                if (current.equals(""))
                {
                    addSeries(values.get(key));
                    current = key;
                    legend.setText(key);
                }
            }
        }
    }
    public void remove(String key)
    {
        values.remove(key);
    }
    public void addPoint(String key,float point)
    {
        add(key);
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
    public void switchSeries()
    {
        if(values.size() > 1)
        {
            currentIndex = (currentIndex + 1) % values.size();
            switchSeries(currentIndex);
        }
    }
    public void switchSeries(int index)
    {
        if(index <= values.size())
        {
            clearChart();
            addSeries((ValueLineSeries) values.values().toArray()[index]);
            if(legend != null)
            {
                legend.setText((String)values.keySet().toArray()[index]);
            }
        }
    }

    public void switchSeries(String key)
    {
        int index = new ArrayList<>(values.keySet()).indexOf(key);
        if(index != -1)
        {
            switchSeries(index);
        }
    }

}
