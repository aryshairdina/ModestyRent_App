package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

public class activity_booking extends AppCompatActivity {

    private TextView tvBookingNumber, tvProductName, tvRentalPeriod,
            tvDeliveryOption, tvPaymentMethod, tvTotalAmount, tvBookingStatus;

    private MaterialButton btnViewBookings, btnBackToHome, btnDownloadReceipt;
    private ImageView backIcon;

    private DatabaseReference bookingsRef;
    private String bookingId;

    private final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }

        bookingId = getIntent().getStringExtra("bookingId");
        if (bookingId == null) {
            Toast.makeText(this, "Booking not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        loadBookingData();
    }

    private void initializeViews() {
        tvBookingNumber = findViewById(R.id.tvBookingNumber);
        tvProductName = findViewById(R.id.tvProductName);
        tvRentalPeriod = findViewById(R.id.tvRentalPeriod);
        tvDeliveryOption = findViewById(R.id.tvDeliveryOption);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvBookingStatus = findViewById(R.id.tvBookingStatus);

        btnViewBookings = findViewById(R.id.btnViewBookings);
        btnBackToHome = findViewById(R.id.btnBackToHome);
        btnDownloadReceipt = findViewById(R.id.btnDownloadReceipt);
        backIcon = findViewById(R.id.backIcon);
    }

    private void setupClickListeners() {

        if (backIcon != null) backIcon.setOnClickListener(v -> finish());

        btnViewBookings.setOnClickListener(v -> {
            startActivity(new Intent(this, activity_myrentals.class));
            finish();
        });

        btnBackToHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, activity_homepage.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnDownloadReceipt.setOnClickListener(v -> generatePdfReceipt());
    }

    private void loadBookingData() {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        bookingsRef = root.child("bookings");

        bookingsRef.child(bookingId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if (!snapshot.exists()) {
                            Toast.makeText(activity_booking.this,
                                    "Booking not found", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        String bookingNumber = snapshot.child("bookingNumber").getValue(String.class);
                        if (bookingNumber == null) {
                            bookingNumber = generateBookingNumber();
                            saveBookingNumber(bookingNumber);
                        }

                        tvBookingNumber.setText(bookingNumber);
                        tvProductName.setText(snapshot.child("productName").getValue(String.class));
                        tvDeliveryOption.setText(snapshot.child("deliveryOption").getValue(String.class));
                        tvPaymentMethod.setText(snapshot.child("paymentMethod").getValue(String.class));

                        Long start = snapshot.child("startDate").getValue(Long.class);
                        Long end = snapshot.child("endDate").getValue(Long.class);
                        if (start != null && end != null) {
                            tvRentalPeriod.setText(
                                    DATE_FORMAT.format(start) + " to " + DATE_FORMAT.format(end));
                        }

                        Double total = snapshot.child("totalAmount").getValue(Double.class);
                        tvTotalAmount.setText(
                                String.format(Locale.getDefault(),
                                        "RM %.2f", total != null ? total : 0));

                        tvBookingStatus.setText("Confirmed");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(activity_booking.this,
                                "Failed to load booking", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String generateBookingNumber() {
        String time = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        return "MR" + time + new Random().nextInt(900);
    }

    private void saveBookingNumber(String number) {
        bookingsRef.child(bookingId).child("bookingNumber").setValue(number);
    }

    // =========================
    // PDF RECEIPT
    // =========================

    private void generatePdfReceipt() {

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(2);

        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(595, 842, 1).create());

        Canvas canvas = page.getCanvas();

        int x = 40;
        int y = 50;

        // LOGO
        Bitmap logo = BitmapFactory.decodeResource(
                getResources(), R.drawable.receipt_logo);
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, 80, 80, false);
        canvas.drawBitmap(scaledLogo, x, y, paint);

        // TITLE
        paint.setFakeBoldText(true);
        paint.setTextSize(20);
        canvas.drawText("MODESTYRENT", x + 100, y + 30, paint);

        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        canvas.drawText("Booking Receipt", x + 100, y + 55, paint);

        y += 100;
        canvas.drawLine(x, y, 555, y, linePaint);
        y += 30;

        paint.setTextSize(12);

        drawRow(canvas, paint, "Booking Number", tvBookingNumber.getText().toString(), y); y += 25;
        drawRow(canvas, paint, "Product", tvProductName.getText().toString(), y); y += 25;
        drawRow(canvas, paint, "Rental Period", tvRentalPeriod.getText().toString(), y); y += 25;
        drawRow(canvas, paint, "Delivery Option", tvDeliveryOption.getText().toString(), y); y += 25;
        drawRow(canvas, paint, "Payment Method", tvPaymentMethod.getText().toString(), y);

        y += 30;
        canvas.drawLine(x, y, 555, y, linePaint);
        y += 30;

        paint.setFakeBoldText(true);
        paint.setTextSize(14);
        drawRow(canvas, paint, "Total Paid", tvTotalAmount.getText().toString(), y);

        y += 30;
        paint.setFakeBoldText(false);
        paint.setTextSize(12);
        drawRow(canvas, paint, "Status", tvBookingStatus.getText().toString(), y);

        // PAYMENT POLICY TEXT (NEW)
        y += 40;
        paint.setTextSize(11);
        canvas.drawText(
                "The payment will be held securely until the rental process is completed.",
                x, y, paint);
        y += 15;
        canvas.drawText(
                "Any refundable deposit will be returned after the item is successfully returned.",
                x, y, paint);

        // FOOTER
        y = 760;
        paint.setTextSize(10);
        canvas.drawText("This is a system-generated receipt.", x, y, paint);
        canvas.drawText("Thank you for using ModestyRent.", x, y + 15, paint);

        document.finishPage(page);

        try {
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS),
                    "ModestyRent_Receipt_" + tvBookingNumber.getText() + ".pdf");

            document.writeTo(new FileOutputStream(file));
            Toast.makeText(this,
                    "Receipt saved to Downloads", Toast.LENGTH_LONG).show();
            openPdf(file);

        } catch (IOException e) {
            Toast.makeText(this,
                    "Failed to generate receipt", Toast.LENGTH_SHORT).show();
        }

        document.close();
    }

    private void drawRow(Canvas canvas, Paint paint,
                         String label, String value, int y) {
        canvas.drawText(label, 40, y, paint);
        canvas.drawText(value, 330, y, paint);
    }

    private void openPdf(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setDataAndType(
                    FileProvider.getUriForFile(
                            this,
                            "com.example.modestyrent_app.provider",
                            file),
                    "application/pdf");
        } else {
            intent.setDataAndType(
                    android.net.Uri.fromFile(file),
                    "application/pdf");
        }
        startActivity(intent);
    }
}
