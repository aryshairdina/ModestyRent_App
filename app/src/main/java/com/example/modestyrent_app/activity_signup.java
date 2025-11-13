package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class activity_signup extends AppCompatActivity {

    private static final String TAG = "activity_signup";

    private TextInputEditText fullNameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private MaterialButton signUpButton;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Views
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        signUpButton = findViewById(R.id.signUpButton);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating account...");
        progressDialog.setCancelable(false);

        signUpButton.setOnClickListener(v -> attemptSignup());

        // Optional: login redirect
        View loginRedirect = findViewById(R.id.loginRedirectText);
        if (loginRedirect != null) {
            loginRedirect.setOnClickListener(v -> {
                startActivity(new Intent(activity_signup.this, activity_signin.class));
                finish();
            });
        }
    }

    private void attemptSignup() {
        String fullName = fullNameEditText.getText() != null ? fullNameEditText.getText().toString().trim() : "";
        String email = emailEditText.getText() != null ? emailEditText.getText().toString().trim() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString() : "";
        String confirm = confirmPasswordEditText.getText() != null ? confirmPasswordEditText.getText().toString() : "";

        // Validation
        if (TextUtils.isEmpty(fullName)) {
            fullNameEditText.setError("Enter full name");
            fullNameEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        progressDialog.show();

        // Create user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, createTask -> {
                    if (createTask.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) {
                            progressDialog.dismiss();
                            Toast.makeText(activity_signup.this, "Registration failed: no user returned", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String uid = firebaseUser.getUid();
                        user userObj = new user(uid, fullName, email);

                        // Save user profile to Realtime Database
                        usersRef.child(uid).setValue(userObj)
                                .addOnCompleteListener(dbTask -> {
                                    progressDialog.dismiss();
                                    if (dbTask.isSuccessful()) {
                                        // Optionally send email verification
                                        sendEmailVerification(firebaseUser);

                                        Toast.makeText(activity_signup.this, "Account created.", Toast.LENGTH_LONG).show();
                                        // Redirect to sign-in or home as desired
                                        Intent i = new Intent(activity_signup.this, activity_signin.class);
                                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(i);
                                        finish();
                                    } else {
                                        // DB write failed -> delete auth user to avoid orphaned account
                                        Exception dbEx = dbTask.getException();
                                        Log.e(TAG, "Failed to save user in Realtime DB", dbEx);
                                        // delete auth user
                                        firebaseUser.delete().addOnCompleteListener(deleteTask -> {
                                            if (deleteTask.isSuccessful()) {
                                                Log.d(TAG, "Auth user deleted after DB write failure");
                                            } else {
                                                Log.w(TAG, "Failed to delete auth user after DB write failure", deleteTask.getException());
                                            }
                                        });

                                        String msg = dbEx != null ? dbEx.getMessage() : "Failed to save profile";
                                        Toast.makeText(activity_signup.this, "Failed to save profile: " + msg, Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        progressDialog.dismiss();
                        Exception ex = createTask.getException();
                        Log.e(TAG, "createUserWithEmailAndPassword failed", ex);

                        // Friendly error messages for common cases
                        if (ex instanceof FirebaseAuthUserCollisionException) {
                            // Email already in use
                            Toast.makeText(activity_signup.this, "This email is already registered. Try signing in or use password reset.", Toast.LENGTH_LONG).show();
                        } else if (ex != null) {
                            Toast.makeText(activity_signup.this, "Registration failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity_signup.this, "Registration failed (unknown error)", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void sendEmailVerification(FirebaseUser user) {
        if (user == null) return;

        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Verification email sent to " + user.getEmail());
                    } else {
                        Log.w(TAG, "Failed to send verification email", task.getException());
                    }
                });
    }
}
