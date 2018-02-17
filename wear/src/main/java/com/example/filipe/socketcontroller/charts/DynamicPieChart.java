package com.example.filipe.socketcontroller.charts;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.PieModel;

import java.util.HashMap;
import java.util.Map;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicPieChart extends PieChart
{
    private HashMap<String,PieModel> values;
    public DynamicPieChart(Context context)
    {
        super(context);
        values = new HashMap<>();
    }
    public DynamicPieChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        values = new HashMap<>();
    }
    private void add(String key)
    {
        PieModel slice = new PieModel(key,0,Color.parseColor(colors[values.size() % colors.length]));
        values.put(key,slice);
        refresh();
    }
    private void refresh()
    {
        clearChart();
        for(Map.Entry<String,PieModel> p : values.entrySet())
        { addPieSlice(p.getValue()); }
    }
    public void remove(String plugName)
    {
        values.remove(plugName);
        refresh();
    }
    public void setValue(String key,float value)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).setValue(value);
    }
    public void incValue(String key, float value)
    {
        if(!contains(key))
        { add(key); }

        float old = values.get(key).getValue();
        values.get(key).setValue(old + value);
    }
    public boolean contains(String key)
    {
        return values.containsKey(key);
    }
}
