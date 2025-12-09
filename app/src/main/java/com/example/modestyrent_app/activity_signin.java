package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class activity_signin extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private MaterialButton signInButton;
    private TextView signUpRedirectText;
    private TextView tvForgotPassword;

    private View errorPanel;
    private ImageView errorIcon;
    private TextView errorMessage;
    private ImageButton errorCloseBtn;

    private FirebaseAuth mAuth;
    private ProgressDialog progressDialog;

    // auto-hide delay for error panel (ms)
    private static final long ERROR_AUTO_HIDE_MS = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signInButton = findViewById(R.id.signInButton);
        signUpRedirectText = findViewById(R.id.signUpRedirectText);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // error panel views
        errorPanel = findViewById(R.id.errorPanel);
        errorIcon = findViewById(R.id.errorIcon);
        errorMessage = findViewById(R.id.errorMessage);
        errorCloseBtn = findViewById(R.id.errorCloseBtn);

        mAuth = FirebaseAuth.getInstance();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Signing in...");
        progressDialog.setCancelable(false);

        signUpRedirectText.setOnClickListener(v -> {
            Intent intent = new Intent(activity_signin.this, activity_signup.class);
            startActivity(intent);
        });

        signInButton.setOnClickListener(v -> attemptLogin());

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        errorCloseBtn.setOnClickListener(v -> hideErrorPanel());
    }

    private void attemptLogin() {
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString() : "";

        // basic validation
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordEditText.setError("Enter valid password (>=6 chars)");
            passwordEditText.requestFocus();
            return;
        }

        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // login success -> open home
                            Intent intent = new Intent(activity_signin.this, activity_homepage.class);
                            // clear backstack
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            showErrorPanel("Sign-in failed. Please try again.");
                        }
                    } else {
                        // Show friendly message inside error panel (not toast)
                        String raw = task.getException() != null ? task.getException().getMessage() : null;
                        // Map some common firebase messages to friendlier text
                        String friendly;
                        if (raw != null && (raw.toLowerCase().contains("password") || raw.toLowerCase().contains("invalid"))) {
                            friendly = "Email or password is incorrect. Please try again.";
                        } else if (raw != null && raw.toLowerCase().contains("no user record")) {
                            friendly = "Account not found. Please sign up first.";
                        } else {
                            friendly = raw != null ? raw : "Authentication failed. Please try again.";
                        }
                        showErrorPanel(friendly);
                    }
                });
    }

    private void showForgotPasswordDialog() {
        // Inflate custom layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);

        TextInputLayout emailInputLayout = dialogView.findViewById(R.id.emailInputLayout);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextView tvError = dialogView.findViewById(R.id.tvError);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvDialogSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);

        // Pre-fill with email from sign-in form if available
        String currentEmail = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(currentEmail)) {
            etEmail.setText(currentEmail);
        }

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this, R.style.FashionDialogTheme)
                .setView(dialogView)
                .setCancelable(true);

        AlertDialog dialog = dialogBuilder.create();

        // Get buttons from custom layout
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSendLink = dialogView.findViewById(R.id.btnSendLink);

        btnCancel.setOnClickListener(view -> dialog.dismiss());

        btnSendLink.setOnClickListener(view -> {
            String email = etEmail.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                tvError.setText("Please enter your email address");
                tvError.setVisibility(View.VISIBLE);
                emailInputLayout.setError("Email is required");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvError.setText("Please enter a valid email address");
                tvError.setVisibility(View.VISIBLE);
                emailInputLayout.setError("Invalid email format");
                return;
            }

            // Clear errors
            tvError.setVisibility(View.GONE);
            emailInputLayout.setError(null);

            // If validation passes, send reset email
            sendPasswordResetEmail(email, dialog);
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email, AlertDialog dialog) {
        ProgressDialog resetProgressDialog = new ProgressDialog(this);
        resetProgressDialog.setMessage("Sending password reset link...");
        resetProgressDialog.setCancelable(false);
        resetProgressDialog.show();

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    resetProgressDialog.dismiss();

                    if (task.isSuccessful()) {
                        dialog.dismiss();
                        // Show success dialog
                        showSuccessDialog(email);
                    } else {
                        // Handle errors
                        String errorMessage = "Failed to send reset email";
                        if (task.getException() != null) {
                            String exceptionMessage = task.getException().getMessage();
                            if (exceptionMessage.contains("user-not-found")) {
                                errorMessage = "No account found with this email address";
                            } else if (exceptionMessage.contains("invalid-email")) {
                                errorMessage = "Invalid email address";
                            } else if (exceptionMessage.contains("too-many-requests")) {
                                errorMessage = "Too many attempts. Please try again later";
                            } else if (exceptionMessage.contains("network")) {
                                errorMessage = "Network error. Please check your connection";
                            }
                        }
                        showErrorPanel(errorMessage);
                    }
                });
    }

    private void showSuccessDialog(String email) {
        View successView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_success, null);

        TextView tvSuccessTitle = successView.findViewById(R.id.tvSuccessTitle);
        TextView tvSuccessMessage = successView.findViewById(R.id.tvSuccessMessage);
        TextView tvEmailAddress = successView.findViewById(R.id.tvEmailAddress);
        TextView tvSpamNote = successView.findViewById(R.id.tvSpamNote);
        Button btnGotIt = successView.findViewById(R.id.btnGotIt);

        String formattedEmail = email.substring(0, Math.min(3, email.length())) + "***" +
                email.substring(email.indexOf("@"));
        tvEmailAddress.setText(email);

        AlertDialog successDialog = new MaterialAlertDialogBuilder(this, R.style.FashionDialogTheme)
                .setView(successView)
                .setCancelable(false)
                .create();

        btnGotIt.setOnClickListener(view -> successDialog.dismiss());

        successDialog.show();
    }

    private void showErrorPanel(String message) {
        if (errorMessage != null) errorMessage.setText(message);
        if (errorPanel != null) errorPanel.setVisibility(View.VISIBLE);

        // auto-hide after a few seconds
        errorPanel.removeCallbacks(hideRunnable);
        errorPanel.postDelayed(hideRunnable, ERROR_AUTO_HIDE_MS);
    }

    private void hideErrorPanel() {
        if (errorPanel != null) {
            errorPanel.removeCallbacks(hideRunnable);
            errorPanel.setVisibility(View.GONE);
        }
    }

    private final Runnable hideRunnable = this::hideErrorPanel;
}