package com.example.filipe.socketcontroller.util;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

public abstract class UI
{
    public static void unhide(View view)
    { view.setVisibility(RelativeLayout.VISIBLE); }

    public static void hide(View view)
    { view.setVisibility(RelativeLayout.GONE); }

    public static boolean isVisible(View view)
    { return view.getVisibility() == RelativeLayout.VISIBLE; }

    public static void toast(Context c, String s)
    { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    public static final String[] colors =
            {
                    "#FF0000",
                    "#FF8000",
                    "#FFFF00",
                    "#80FF00",
                    "#00FF00",
                    "#00FF80",
                    "#00FFFF",
                    "#0080FF",
                    "#0000FF",
                    "#7F00FF",
                    "#FF00FF",
                    "#FF007F",
                    "#808080"
            };
    public static void fitToScreen(Activity a, View view)
    {
        DisplayMetrics metrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = metrics.widthPixels;
        params.height = metrics.heightPixels;
        view.setLayoutParams(params);
    }

}
