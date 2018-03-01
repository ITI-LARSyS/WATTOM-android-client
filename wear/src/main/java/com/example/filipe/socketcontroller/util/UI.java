package com.example.filipe.socketcontroller.util;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.filipe.socketcontroller.R;

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
                    "#00FF00",
                    "#00FFFF",
                    "#0080FF",
                    "#0000FF",
                    "#8000FF",
                    "#FF00FF",
                    "#705050",
                    "#FFFFFF"
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
    public static void fitToScreen(Activity a, View view, int padding)
    {
        DisplayMetrics metrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = metrics.widthPixels - padding;
        params.height = metrics.heightPixels - padding;
        view.setLayoutParams(params);
    }
    public static void toggleVisibility(View v)
    {
        if(isVisible(v)) hide(v);
        else unhide(v);
    }
    public static void updateTime(TextView text, int hour, int minutes)
    {
        String strHour = "";
        String strMinute = "";

        if(hour < 10) strHour += "0";
        strHour += hour;

        if(minutes < 10) strMinute += "0";
        strMinute += minutes;

        text.setText(strHour + ":" + strMinute);
    }
    public static void notify(Context c,Class destination,String title, String text)
    {
        Intent intent = new Intent(c, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(c,0,intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(c)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pending);

        NotificationManager notificationManager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, mBuilder.build());
    }

}
