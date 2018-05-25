package com.example.filipe.socketcontroller.tabs;

import com.example.filipe.socketcontroller.R;

public enum TabConfig
{
    STARTSTOP(R.string.TITLE_STARTSTOP,
            android.R.drawable.ic_media_play,
            R.id.tab_start_stop),

    SCHEDULE(R.string.TITLE_SCHEDULE,
            android.R.drawable.ic_lock_idle_alarm,
            R.id.tab_schedule),

    PESSOAS(R.string.TITLE_STATS1,
            android.R.drawable.ic_menu_myplaces,
            R.id.tab_power_pessoas_total),

    PESSOAS2(R.string.TITLE_STATS7,
            android.R.drawable.ic_menu_myplaces,
            R.id.tab_power_pessoas_media),

    PLUGS(R.string.TITLE_STATS3,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_power_plugs_current),

    PLUGSTOTAL(R.string.TITLE_STATS5,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_power_plugs_total),

    DEVICES(R.string.TITLE_STATS6,
            android.R.drawable.ic_menu_camera,
            R.id.tab_power_devices),

    ENERGIAS(R.string.TITLE_STATS4,
            android.R.drawable.ic_lock_idle_low_battery,
            R.id.tab_energias);

    public final int title;
    public final int icon;
    public final int id;

    public static final TabConfig DEFAULT = STARTSTOP;

    TabConfig(final int title, final int icon, final int id)
    {
        this.title = title;
        this.icon = icon;
        this.id = id;
    }
}