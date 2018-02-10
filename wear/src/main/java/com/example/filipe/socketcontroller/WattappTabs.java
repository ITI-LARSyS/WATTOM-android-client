package com.example.filipe.socketcontroller;

public enum WattappTabs
{
    STARTSTOP(R.string.TITLE_STARTSTOP, android.R.drawable.ic_media_play,R.layout.tab_start_stop),
    SCHEDULE(R.string.TITLE_SCHEDULE, android.R.drawable.ic_lock_idle_alarm,R.layout.tab_schedule),
    STATS1(R.string.TITLE_STATS1, android.R.drawable.stat_sys_data_bluetooth,R.layout.tab_stats1),
    STATS2(R.string.TITLE_STATS2, android.R.drawable.stat_sys_data_bluetooth,R.layout.tab_stats2),
    STATS3(R.string.TITLE_STATS3, android.R.drawable.stat_sys_data_bluetooth,R.layout.tab_stats3),
    LOG(R.string.TITLE_LOG, android.R.drawable.ic_menu_preferences,R.layout.tab_log);

    final int title;
    final int icon;
    final int layout;

    public static final WattappTabs DEFAULT = STARTSTOP;

    WattappTabs(final int title, final int icon, final int layout)
    {
        this.title = title;
        this.icon = icon;
        this.layout = layout;
    }
}
