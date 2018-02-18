package com.example.filipe.socketcontroller.tabs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.wearable.view.drawer.WearableNavigationDrawer;

import com.example.filipe.socketcontroller.MainActivity;

public class TabAdapter extends WearableNavigationDrawer.WearableNavigationDrawerAdapter
{
    private final Context context;

    public TabAdapter(final Context context)
    { this.context = context; }

    @Override
    public String getItemText(int index)
    { return context.getString(WattappTabConfig.values()[index].title); }

    @Override
    public Drawable getItemDrawable(int index)
    { return context.getDrawable(WattappTabConfig.values()[index].icon); }

    @Override
    public void onItemSelected(int index)
    { ((MainActivity)context).drawTab(WattappTabConfig.values()[index]); }

    @Override
    public int getCount()
    { return WattappTabConfig.values().length; }
}
