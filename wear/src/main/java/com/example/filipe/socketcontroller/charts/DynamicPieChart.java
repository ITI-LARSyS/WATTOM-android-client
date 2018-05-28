package com.example.filipe.socketcontroller.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.example.filipe.socketcontroller.R;

import org.eazegraph.lib.charts.PieChart;
import org.eazegraph.lib.models.PieModel;

import java.util.HashMap;

import static com.example.filipe.socketcontroller.util.UI.colors;

public class DynamicPieChart extends PieChart
{
    // Data
    private HashMap<String,PieModel> values;
    private int currentIndex;

    // Constants
    private static final int NONE = -1;

    public DynamicPieChart(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        loadAttributes(context,attrs);
        init();
    }

    public DynamicPieChart(Context context)
    {
        super(context);
        init();
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        adjustParams();
    }

    // Leitura dos atributos presentes no XML
    @SuppressLint("CustomViewStyleable")
    private void loadAttributes(Context context, AttributeSet attrs)
    {
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

    // Ajuste dos parâmetros do layout
    // (por causa de problemas com match_parent e wrap_content)
    private void adjustParams()
    {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        ViewGroup.LayoutParams params = getLayoutParams();
        int input_width = params.width;
        int input_height = params.height;
        if(input_width == LinearLayout.LayoutParams.MATCH_PARENT || input_width == LinearLayout.LayoutParams.WRAP_CONTENT)
        {
            params.width = metrics.widthPixels;
        }
        if(input_height == LinearLayout.LayoutParams.MATCH_PARENT || input_height == LinearLayout.LayoutParams.WRAP_CONTENT)
        {
            params.height = metrics.heightPixels;
        }
    }

    // Preparação geral do layout
    private void init()
    {
        values = new HashMap<>();
        currentIndex = NONE;
        this.setUsePieRotation(false);
        this.setOnClickListener((v)->switchSlice());
    }

    // Troca de slice
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

    // Adição de uma nova slice
    private void add(String key)
    {
        PieModel slice = new PieModel(key,0,Color.parseColor(colors[values.size() % colors.length]));
        values.put(key,slice);
        refresh();
    }

    // Refresh do layout
    private void refresh()
    {
        clearChart();
        if(currentIndex == NONE)
        {
            currentIndex = 0;
        }
        for(PieModel p : values.values())
        {
            addPieSlice(p);
        }
        setCurrentItem(currentIndex);
    }

    // Mudança de valor de uma slice
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
