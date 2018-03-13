package com.example.filipe.socketcontroller.charts;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
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

public class DynamicLineChart extends LinearLayout
{
    private HashMap<String,ValueLineSeries> values;
    private ValueLineChart chart;
    private TextView indicator;
   // private View rootView;

    public DynamicLineChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    public DynamicLineChart(Context context)
    {
        super(context);
        init(context);
    }

    private void init(Context c)
    {
        this.setOrientation(LinearLayout.VERTICAL);

        values = new HashMap<>();

        chart = new ValueLineChart(c);
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        chart.setLayoutParams(new LinearLayout.LayoutParams(metrics.widthPixels - 80,metrics.heightPixels - 80));
        chart.setUseCubic(true);
        chart.setUseOverlapFill(false);
        chart.setIndicatorLineColor(Color.parseColor("#FFFFFF"));
        chart.setIndicatorTextColor(Color.parseColor("#FFFFFF"));

        indicator = new TextView(c);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        indicator.setOnClickListener((v)-> switchSeries());
        indicator.bringToFront();
        indicator.setText("-");
        indicator.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);

        addView(chart);
        addView(indicator);
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
        chart.clearChart();
        if(!values.containsKey(key))
        {
            add(key);
        }
        chart.addSeries(values.get(key));
        indicator.setText(key);
    }

}
