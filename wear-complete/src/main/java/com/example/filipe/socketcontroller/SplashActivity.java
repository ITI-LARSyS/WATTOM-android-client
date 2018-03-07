package com.example.filipe.socketcontroller;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

public class SplashActivity extends WearableActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
