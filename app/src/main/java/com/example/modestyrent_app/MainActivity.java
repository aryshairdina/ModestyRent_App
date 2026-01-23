package com.example.modestyrent_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "modestyrent_prefs";
    private static final String KEY_SEEN_ONBOARDING = "seen_onboarding";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler().postDelayed(() -> {

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean seenOnboarding = prefs.getBoolean(KEY_SEEN_ONBOARDING, false);

            if (seenOnboarding) {
                startActivity(new Intent(MainActivity.this, activity_home.class));
            } else {
                startActivity(new Intent(MainActivity.this, activity_onboarding.class));
            }

            finish();

        }, 2500); // 2 seconds logo display
    }
}
