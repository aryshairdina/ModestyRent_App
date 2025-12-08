package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
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
        final EditText input = new EditText(this);
        input.setHint("Enter your email address");
        input.setText(emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "");
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We'll send a password reset link to your email.")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String mail = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(mail) || !Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
                        showErrorPanel("Enter a valid email address to reset password.");
                        return;
                    }
                    sendPasswordResetEmail(mail);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        b.show();
    }

    private void sendPasswordResetEmail(String email) {
        progressDialog.show();
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            progressDialog.dismiss();
            if (task.isSuccessful()) {
                showErrorPanel("Password reset email sent. Check your inbox.");
            } else {
                String raw = task.getException() != null ? task.getException().getMessage() : null;
                String friendly = raw != null ? raw : "Failed to send reset email. Try again later.";
                showErrorPanel(friendly);
            }
        });
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
