package com.example.filipe.socketcontroller.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.filipe.socketcontroller.R;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends LinearLayout
{
    private HashMap<String,ValueLineSeries> values;
    private ValueLineChart chart;
    private TextView indicator;
    private int currentIndex;

    @SuppressLint("CustomViewStyleable")
    public DynamicLineChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.DynamicCharts, 0, 0);
        try
        {
            String unit = ta.getString(R.styleable.DynamicCharts_unitShown);
            chart.setIndicatorTextUnit(unit);
        }
        finally
        {
            ta.recycle();
        }
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

        initChart(c);
        initIndicator(c);

        currentIndex = -1;

        addView(chart);
        addView(indicator);
    }

    private void initChart(Context c)
    {
        chart = new ValueLineChart(c);
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        chart.setLayoutParams(new LinearLayout.LayoutParams(metrics.widthPixels - 80,metrics.heightPixels - 80));
        chart.setShowStandardValues(true);
        chart.setShowDecimal(true);
        chart.setUseCubic(true);
        chart.setUseOverlapFill(true);
        chart.setUseDynamicScaling(false);
        chart.setIndicatorLineColor(Color.parseColor("#FFFFFF"));
        chart.setIndicatorTextColor(Color.parseColor("#FFFFFF"));
    }

    private void initIndicator(Context c)
    {
        indicator = new TextView(c);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        indicator.setOnClickListener((v)-> switchSeries());
        indicator.setText("-");
        indicator.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
    }

    public void refresh()
    {
        if (currentIndex == -1)
        {
            switchSeries(0);
        }
        else
        {
            invalidate();
        }
    }

    public void add(String key)
    {
        values.put(key, new ValueLineSeries());
        values.get(key).setColor(Color.parseColor(colors[(values.size() - 1) % colors.length]));
    }

    public void addPoint(String key,float point)
    {
        addPoint(key,getCurrentTime(),point);
    }
    public void addPoint(String key,String legend,float point)
    {
        if(!contains(key))
        { add(key); }

        ValueLinePoint value = new ValueLinePoint(legend,point);
        values.get(key).addPoint(value);

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
            switchSeries((currentIndex + 1) % values.size());
        }
    }
    public void switchSeries(int index)
    {
        if(index <= values.size())
        {
            currentIndex = index;
            String key = (String) values.keySet().toArray()[currentIndex];
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
        for(ValueLinePoint p : values.get(key).getSeries())
        {
            p.setIgnore(false);
        }
        chart.addSeries(values.get(key));
        indicator.setText(key);
    }

}
