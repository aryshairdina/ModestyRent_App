package com.example.modestyrent_app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Edit profile activity:
 * - Load fullname, email, phone, address from Realtime DB (/users/{uid})
 * - Show email as read-only (from FirebaseAuth + DB fallback)
 * - Allow adding phone & address if absent, and editing fullname/phone/address
 * - Save updates back to /users/{uid}
 */
public class activity_edit_profile extends AppCompatActivity {

    private TextInputEditText etFullName, etEmail, etPhone, etAddress;
    private MaterialButton btnSave;
    private ImageView backIcon;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If you used EdgeToEdge earlier, keep that call in a utility. Here assume layout already set.
        setContentView(R.layout.activity_edit_profile);

        // init UI
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        btnSave = findViewById(R.id.btnSave);
        backIcon = findViewById(R.id.backIcon);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // user not logged in — handle gracefully (close or redirect to login)
            Toast.makeText(this, "Please login first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentUid = user.getUid();
        // Reference to /users/{uid} in Realtime Database
        usersRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid);

        // Show email from FirebaseAuth if available (read-only)
        if (user.getEmail() != null) {
            etEmail.setText(user.getEmail());
        } else {
            etEmail.setText("");
        }
        // Ensure email field is non-editable (xml already set but double-check)
        etEmail.setEnabled(false);
        etEmail.setFocusable(false);

        // Load existing user data from DB
        loadUserProfileFromDb();

        // Save button click -> validate & write to DB (create or update)
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveProfileToDb();
            }
        });

        // back button
        backIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    /**
     * Read user data from /users/{uid} and populate the fields.
     * Expected DB structure (example):
     * users
     *  └─ uid123
     *      ├─ fullname: "Nur Arysha"
     *      ├─ email: "nur.arysha@example.com"
     *      ├─ phone: "0123456789"
     *      └─ address: "..."
     */
    private void loadUserProfileFromDb() {
        usersRef.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // fullname
                    if (snapshot.child("fullName").exists()) {
                        String fullname = snapshot.child("fullName").getValue(String.class);
                        if (fullname != null) etFullName.setText(fullname);
                    } // else leave blank so user can add

                    // email (prefer auth email but fallback to DB)
                    if (TextUtils.isEmpty(etEmail.getText())) {
                        if (snapshot.child("email").exists()) {
                            String email = snapshot.child("email").getValue(String.class);
                            if (email != null) etEmail.setText(email);
                        }
                    }

                    // phone
                    if (snapshot.child("phone").exists()) {
                        String phone = snapshot.child("phone").getValue(String.class);
                        if (phone != null) etPhone.setText(phone);
                    }

                    // address
                    if (snapshot.child("address").exists()) {
                        String address = snapshot.child("address").getValue(String.class);
                        if (address != null) etAddress.setText(address);
                    }

                } else {
                    // No node found for this user yet — user registered with auth only
                    // Prefill fullname from auth displayName if available
                    FirebaseUser authUser = mAuth.getCurrentUser();
                    if (authUser != null && !TextUtils.isEmpty(authUser.getDisplayName())) {
                        etFullName.setText(authUser.getDisplayName());
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // reading failed
                Toast.makeText(activity_edit_profile.this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Validate inputs and push the data to Realtime DB.
     * Will create phone/address fields if they were missing (same as update).
     */
    private void saveProfileToDb() {
        String fullname = etFullName.getText() == null ? "" : etFullName.getText().toString().trim();
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        String phone = etPhone.getText() == null ? "" : etPhone.getText().toString().trim();
        String address = etAddress.getText() == null ? "" : etAddress.getText().toString().trim();

        // Basic validation: fullname required
        if (TextUtils.isEmpty(fullname)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        // Optional: validate phone if present (simple length check)
        if (!TextUtils.isEmpty(phone)) {
            // basic numeric check and length (adjust to your rules)
            String digitsOnly = phone.replaceAll("\\s+", "");
            if (!digitsOnly.matches("[0-9+\\-()]*") || digitsOnly.length() < 7) {
                etPhone.setError("Enter a valid phone number");
                etPhone.requestFocus();
                return;
            }
        }

        // Build a map of fields to write (only these fields)
        // We update fullname, phone, address, and email (email saved for redundancy)
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("fullName", fullname);
        updates.put("phone", phone);
        updates.put("address", address);
        updates.put("email", email); // keep email in DB for easy searching and redundancy

        // Write to DB
        usersRef.updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(activity_edit_profile.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        // Optionally finish() or keep in activity
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(activity_edit_profile.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
