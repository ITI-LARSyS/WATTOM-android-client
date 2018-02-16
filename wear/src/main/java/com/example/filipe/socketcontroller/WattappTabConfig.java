package com.example.filipe.socketcontroller;

public enum WattappTabConfig
{
    STARTSTOP(R.string.TITLE_STARTSTOP,
            android.R.drawable.ic_media_play,
            R.id.tab_start_stop,
            R.menu.menu_start_stop),

    SCHEDULE(R.string.TITLE_SCHEDULE,
            android.R.drawable.ic_lock_idle_alarm,
            R.id.tab_schedule,
            -1),

    STATS1(R.string.TITLE_STATS1,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats_pessoas,
            -1),

    STATS2(R.string.TITLE_STATS2,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats2,
            -1),

    STATS3(R.string.TITLE_STATS3,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats3,
            -1),

    STATS4(R.string.TITLE_STATS4,
            android.R.drawable.ic_menu_sort_by_size,
            R.id.tab_stats_energias,
            -1),

    LOG(R.string.TITLE_LOG,
            android.R.drawable.ic_menu_info_details,
            R.id.tab_log,
            -1);

    final int title;
    final int icon;
    final int id;
    final int menu;

    public static final WattappTabConfig DEFAULT = STARTSTOP;

    WattappTabConfig(final int title, final int icon, final int id, final int menu)
    {
        this.title = title;
        this.icon = icon;
        this.id = id;
        this.menu = menu;
    }
}
