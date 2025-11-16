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

        long selectedDate = getIntent().getLongExtra("selectedDateMillis", -1);
        int days = getIntent().getIntExtra("days", 1);
        String productName = getIntent().getStringExtra("productName");
        double unitPrice = getIntent().getDoubleExtra("unitPrice", 0.0);

        String dateStr = selectedDate > 0 ? new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedDate) : "—";
        double total = unitPrice * days;

        String summary = "Product: " + (productName != null ? productName : "—") + "\n"
                + "Date: " + dateStr + "\n"
                + "Days: " + days + "\n"
                + String.format(Locale.US, "Total: RM %.2f", total);

        tvCheckoutSummary.setText(summary);
    }
}
