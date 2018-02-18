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
            R.id.tab_stats_pessoas),

    PLUGS(R.string.TITLE_STATS3,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats3),

    PLUGSTOTAL(R.string.TITLE_STATS5,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats_pie_plugs),

    ENERGIAS(R.string.TITLE_STATS4,
            android.R.drawable.ic_lock_idle_low_battery,
            R.id.tab_stats_energias),

    LOG(R.string.TITLE_LOG,
            android.R.drawable.ic_menu_info_details,
            R.id.tab_log),

    STATS2(R.string.TITLE_STATS2,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats2);

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