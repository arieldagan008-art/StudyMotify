package com.example.myapplication2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class Signup extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.sign_up);

        etName     = findViewById(R.id.et_name);
        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnSignup  = findViewById(R.id.btn_signup);

        String prefill = getIntent().getStringExtra("prefill_email");
        if (prefill != null) etEmail.setText(prefill);

        btnSignup.setOnClickListener(v -> attemptSignup());
    }

    private void attemptSignup() {
        Toast.makeText(this, "Sign Up button clicked", Toast.LENGTH_SHORT).show();

        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            Toast.makeText(this, "Validation failed: Name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            Toast.makeText(this, "Validation failed: Email is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            Toast.makeText(this, "Validation failed: Password too short", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Connecting to Firebase...", Toast.LENGTH_SHORT).show();
        btnSignup.setEnabled(false);

        FirebaseHelper.getInstance().getAuth()
                .createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        btnSignup.setEnabled(true);
                        showError("Signup failed: no user returned. Please try again.");
                        return;
                    }
                    // Write profile in background — don't block navigation on it
                    saveUserToDatabase(user.getUid(), name, email);
                    Toast.makeText(this, "Account created! Welcome!", Toast.LENGTH_SHORT).show();
                    goToHome();
                })
                .addOnFailureListener(e -> {
                    btnSignup.setEnabled(true);
                    showError("Firebase error: " + e.getMessage());
                });
    }

    private void saveUserToDatabase(String uid, String name, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);

        FirebaseHelper.getInstance().getDatabase()
                .getReference("users")
                .child(uid)
                .setValue(userData);
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
