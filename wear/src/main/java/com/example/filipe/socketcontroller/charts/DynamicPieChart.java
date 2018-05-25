package com.example.filipe.socketcontroller.charts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;

import com.example.filipe.socketcontroller.R;

import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.PieModel;

import java.util.HashMap;
import java.util.Map;

import static com.example.filipe.socketcontroller.util.UI.colors;
import static com.example.filipe.socketcontroller.util.UI.fitToScreen;

public class DynamicPieChart extends PieChart
{
    private HashMap<String,PieModel> values;
    private int currentIndex;
    public DynamicPieChart(Context context)
    {
        super(context);
        init();
    }

    @SuppressLint("CustomViewStyleable")
    public DynamicPieChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.DynamicCharts, 0, 0);
        try
        {
            String unit = ta.getString(R.styleable.DynamicCharts_unitShown);
            this.setInnerValueUnit(unit);
        }
        finally
        {
            ta.recycle();
        }
    }

    private void init()
    {
        values = new HashMap<>();
        currentIndex = -1;
        this.setUsePieRotation(false);
        this.setOnClickListener((v)->switchSlice());
    }

    public void switchSlice()
    {
        if(values.size() > 1)
        {
            switchSlice((currentIndex + 1) % values.size());
        }
    }

    public void switchSlice(int index)
    {
        currentIndex = index;
        setCurrentItem(currentIndex);
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
        if(currentIndex == -1)
        {
            currentIndex = 0;
        }
        for(PieModel p : values.values())
        {
            addPieSlice(p);
        }
        setCurrentItem(currentIndex);
    }
    public void setValue(String key,float value)
    {
        if(!contains(key))
        { add(key); }

        values.get(key).setValue(value);
        refresh();
    }
    public void incValue(String key, float value)
    {
        if(!contains(key))
        { add(key); }

        float old = values.get(key).getValue();
        values.get(key).setValue(old + value);
        refresh();
    }
    public boolean contains(String key)
    {
        return values.containsKey(key);
    }
}
