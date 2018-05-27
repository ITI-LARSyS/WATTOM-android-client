package com.example.filipe.socketcontroller.tabs;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.wearable.view.drawer.WearableNavigationDrawer;

import com.example.filipe.socketcontroller.MainActivity;

import static com.example.filipe.socketcontroller.util.UI.hide;
import static com.example.filipe.socketcontroller.util.UI.unhide;

public class TabAdapter extends WearableNavigationDrawer.WearableNavigationDrawerAdapter
{
    private final Context context;

    public TabAdapter(final Context context)
    {
        this.context = context;
        drawTab(TabConfig.DEFAULT);
    }

    @Override
    public String getItemText(int index)
    { return context.getString(TabConfig.values()[index].title); }

    @Override
    public Drawable getItemDrawable(int index)
    { return context.getDrawable(TabConfig.values()[index].icon); }

    @Override
    public void onItemSelected(int index)
    { drawTab(TabConfig.values()[index]); }

    @Override
    public int getCount()
    { return TabConfig.values().length; }

    // Desenho de um separador
    private void drawTab(TabConfig openTab)
    {
        for(TabConfig tab : TabConfig.values())
        {
            if(tab == openTab) unhideTab(tab);
            else hideTab(tab);
        }
    }
    private void hideTab(TabConfig tab)
    {
        hide(((Activity)context).findViewById(tab.id));
    }
    private void unhideTab(TabConfig tab)
    {
        unhide(((Activity)context).findViewById(tab.id));
    }
}
