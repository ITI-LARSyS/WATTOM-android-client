package com.example.filipe.socketcontroller.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.filipe.socketcontroller.R;

import org.eazegraph.lib.charts.ValueLineChart;
import org.eazegraph.lib.models.ValueLinePoint;
import org.eazegraph.lib.models.ValueLineSeries;

import java.util.Calendar;
import java.util.HashMap;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicLineChart extends LinearLayout
{
    // Size attributes
    private int width = -1;
    private int height = -1;

    // Data
    private HashMap<String,ValueLineSeries> values;
    private ValueLineSeries averageValues;

    // Layout elements
    private ValueLineChart chart;
    private TextView indicator;
    private String unit;

    // Misc attributes
    private int currentIndex;
    private boolean hasAverageLegend;

    // Constants
    private static final double PADDING_GRAPH_LEFT_RIGHT = 0.9;
    private static final double GRAPH_SIZE_RATIO = 0.7;
    private static final double INDICATOR_SIZE_RATIO = 0.2;
    private static final double EXTRA_LEGEND_SIZE_RATIO = 0.1;
    private static final int NONE = -1;

    public DynamicLineChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        loadAttributes(context,attrs);
        init(context);
    }

    public DynamicLineChart(Context context)
    {
        super(context);
        init(context);
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        adjustParams();
        initChart();
        initIndicator();
        initLegend(getContext());
    }

    // Leitura dos atributos presentes no XML
    @SuppressLint("CustomViewStyleable")
    private void loadAttributes(Context context, AttributeSet attrs)
    {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.DynamicCharts, 0, 0);
        try
        {
            unit = ta.getString(R.styleable.DynamicCharts_unitShown);
            hasAverageLegend = ta.getBoolean(R.styleable.DynamicCharts_showAverage,false);
        }
        finally
        {
            ta.recycle();
        }
    }

    // Ajuste dos parâmetros do layout
    // (por causa de problemas com match_parent e wrap_content)
    private void adjustParams()
    {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        ViewGroup.LayoutParams params = getLayoutParams();
        int input_width = params.width;
        int input_height = params.height;
        if(input_width == LayoutParams.MATCH_PARENT || input_width == LayoutParams.WRAP_CONTENT)
        {
            width = metrics.widthPixels - getPaddingLeft() - getPaddingRight();
        }
        else
        {
            width = input_width;
        }
        if(input_height == LayoutParams.MATCH_PARENT || input_height == LayoutParams.WRAP_CONTENT)
        {
            height = metrics.heightPixels - getPaddingTop() - getPaddingBottom();
        }
        else
        {
            height = input_height;
        }
    }

    // Preparação geral do layout
    private void init(Context c)
    {
        averageValues = new ValueLineSeries();
        averageValues.setColor(Color.parseColor(colors[colors.length-1]));

        chart = new ValueLineChart(c);
        indicator = new TextView(c);

        this.setOrientation(LinearLayout.VERTICAL);
        this.setGravity(Gravity.CENTER);

        values = new HashMap<>();
        currentIndex = NONE;
    }

    // Preparação do chart
    private void initChart()
    {
        chart.setLayoutParams(new LayoutParams(
                (int)(width * PADDING_GRAPH_LEFT_RIGHT),
                (int)(height  * GRAPH_SIZE_RATIO)));
        chart.setShowStandardValues(true);
        chart.setShowDecimal(true);
        chart.setUseCubic(false);
        chart.setUseOverlapFill(false);
        chart.setUseDynamicScaling(false);
        chart.setLegendHeight(30);
        chart.setIndicatorLineColor(Color.parseColor("#FFFFFF"));
        chart.setIndicatorTextColor(Color.parseColor("#FFFFFF"));
        addView(chart);
    }

    // Preparação do indicador
    private void initIndicator()
    {
        if(hasAverageLegend)
        {
            indicator.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    (int)(height * INDICATOR_SIZE_RATIO)));
        }
        else
        {
            indicator.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    (int)(height * (INDICATOR_SIZE_RATIO+EXTRA_LEGEND_SIZE_RATIO))));
        }
        indicator.setGravity(Gravity.CENTER);
        chart.setIndicatorTextUnit(unit);
        indicator.setOnClickListener((v)-> switchSeries());
        if(values.size() == 0) indicator.setText("-");
        addView(indicator);
    }

    // Preparação da legenda da média
    private void initLegend(Context c)
    {
        if(hasAverageLegend)
        {
            addView(new AverageLegend(c));
        }
    }

    // Refresh no layout
    public void refresh()
    {
        if (currentIndex == NONE)
        {
            switchSeries(0);
        }
        else
        {
            switchSeries(currentIndex);
        }
    }

    // Adição de uma nova série de valores
    public void add(String key)
    {
        values.put(key, new ValueLineSeries());
        values.get(key).setColor(Color.parseColor(colors[(values.size() - 1) % colors.length]));
    }

    // Adição de um ponto
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
    public void addPoint(float point)
    {
        ValueLinePoint value = new ValueLinePoint(getCurrentTime(),point);
        averageValues.addPoint(value);
    }

    // Obtenção do tempo atual
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

    // Troca de série de valores
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

        if(hasAverageLegend)
        {
            for(ValueLinePoint p : averageValues.getSeries())
            {
                p.setIgnore(false);
            }
            chart.addSeries(averageValues);
        }

        indicator.setText(key);
    }

    // Classe para a legenda da média
    private class AverageLegend extends android.support.v7.widget.AppCompatTextView
    {
        private static final String LEGEND = "Average value";
        public AverageLegend(Context c)
        {
            super(c);
            init();
        }
        private void init()
        {
            this.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    (int)(height * EXTRA_LEGEND_SIZE_RATIO)));
            this.setText(LEGEND);
            this.setGravity(Gravity.CENTER);
            this.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.editbox_dropdown_light_frame,0,0,0);
            this.setCompoundDrawablePadding(5);
        }
    }

}
