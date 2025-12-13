package com.example.modestyrent_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "modestyrent_prefs";
    private static final String KEY_SEEN_ONBOARDING = "seen_onboarding";

    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private Button btnSkip, btnNext;

    // update drawable names if different
    private final int[] images = new int[]{
            R.drawable.onboarding_1,
            R.drawable.onboarding_2,
            R.drawable.onboarding_3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Check SharedPreferences whether onboarding was already shown
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean seen = prefs.getBoolean(KEY_SEEN_ONBOARDING, false);
        if (seen) {
            // if already seen -> go straight to home page
            startActivity(new Intent(MainActivity.this, activity_home.class));
            finish();
            return;
        }

        // 2) Not seen -> set content view for onboarding
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        dotsLayout = findViewById(R.id.dotsLayout);
        btnSkip = findViewById(R.id.btnSkip);
        btnNext = findViewById(R.id.btnNext);

        viewPager.setAdapter(new OnboardingAdapter(this, images));

        setupIndicators(images.length);
        setCurrentIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                setCurrentIndicator(position);
                btnNext.setText(position == images.length - 1 ? "GET STARTED" : "NEXT");
            }
        });

        btnSkip.setOnClickListener(v -> {
            markOnboardingSeen();
            startActivity(new Intent(MainActivity.this, activity_home.class));
            finish();
        });

        btnNext.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur + 1 < images.length) {
                viewPager.setCurrentItem(cur + 1, true);
            } else {
                markOnboardingSeen();
                startActivity(new Intent(MainActivity.this, activity_home.class));
                finish();
            }
        });
    }

    private void markOnboardingSeen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SEEN_ONBOARDING, true).apply();
    }

    private void setupIndicators(int count) {
        ImageView[] indicators = new ImageView[count];
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);

        for (int i = 0; i < count; i++) {
            indicators[i] = new ImageView(this);
            indicators[i].setImageDrawable(ContextCompat.getDrawable(this, R.drawable.indicator_inactive));
            indicators[i].setLayoutParams(params);
            dotsLayout.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int index) {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            ImageView imageView = (ImageView) dotsLayout.getChildAt(i);
            imageView.setImageDrawable(
                    ContextCompat.getDrawable(this,
                            i == index ? R.drawable.indicator_active : R.drawable.indicator_inactive
                    )
            );
        }
    }
}
