package com.example.modestyrent_app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class activity_checkout extends AppCompatActivity {

    private TextView tvCheckoutSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        tvCheckoutSummary = findViewById(R.id.tvCheckoutSummary);

        // in activity_checkout.java onCreate()
        long startDate = getIntent().getLongExtra("startDateMillis", -1);
        long endDate = getIntent().getLongExtra("endDateMillis", -1);
        int days = getIntent().getIntExtra("days", 1);
        String productName = getIntent().getStringExtra("productName");
        double unitPrice = getIntent().getDoubleExtra("unitPrice", 0.0);

        String startStr = startDate > 0 ? new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(startDate) : "—";
        String endStr = endDate > 0 ? new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(endDate) : "—";
        double total = unitPrice * days;

        String summary = "Product: " + (productName != null ? productName : "—") + "\n"
                + "Start: " + startStr + "\n"
                + "End: " + endStr + "\n"
                + "Days: " + days + "\n"
                + String.format(Locale.US, "Total: RM %.2f", total);

        tvCheckoutSummary.setText(summary);

    }
}
