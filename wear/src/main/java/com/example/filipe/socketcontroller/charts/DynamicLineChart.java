package com.example.filipe.socketcontroller.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends ValueLineChart
{
    private HashMap<String,ValueLineSeries> values;
    private TextView indicator;

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

    public void setIndicator(TextView indicator)
    {
        this.indicator = indicator;
        indicator.setOnClickListener((v)-> switchSeries());
    }

    public int getCurrentIndex()
    {
        return new ArrayList<>(values.keySet()).indexOf(indicator.getText()+"");
    }

    public void refresh()
    {
        switchSeries(indicator.getText()+"");
    }

    public void add(String key)
    {
        values.put(key, new ValueLineSeries());
        values.get(key).setColor(Color.parseColor(colors[(values.size() - 1) % colors.length]));
        if (indicator.getText().equals("-"))
        {
            switchSeries(key);
        }
    }
    public void remove(String key)
    {
        values.remove(key);
    }
    public void addPoint(String key,float point)
    {
        addPoint(key,getCurrentTime(),point);
    }
    public void addPoint(String key,String legend,float point)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).addPoint(new ValueLinePoint(legend,point));
        refresh();
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
            int index = getCurrentIndex();
            index = (index + 1) % values.size();
            switchSeries(index);
        }
    }
    public void switchSeries(int index)
    {
        if(index <= values.size())
        {
            String key = (String) values.keySet().toArray()[index];
            switchSeries(key);
        }
    }

    public void switchSeries(String key)
    {
        clearChart();
        if(!values.containsKey(key))
        {
            add(key);
        }
        addSeries(values.get(key));
        indicator.setText(key);
    }

}
