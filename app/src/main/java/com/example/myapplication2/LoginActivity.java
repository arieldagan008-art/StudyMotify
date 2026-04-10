package com.example.myapplication2;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnContinue;
    private TextView tvForgotPassword, tvSignUp;

    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS = 5;
    private long lockedUntilMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.log_in2);

        etEmail         = findViewById(R.id.et_email);
        etPassword      = findViewById(R.id.et_password);
        btnContinue     = findViewById(R.id.btn_continue);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvSignUp        = findViewById(R.id.tv_sign_up);

        btnContinue.setOnClickListener(v -> attemptLogin());

        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class))
        );

        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, Signup.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (FirebaseHelper.getInstance().isLoggedIn()) {
            goToHome();
        }
    }

    private void attemptLogin() {
        long now = SystemClock.elapsedRealtime();
        if (now < lockedUntilMs) {
            long secs = (lockedUntilMs - now) / 1000;
            Toast.makeText(this, "Too many attempts. Try again in " + secs + "s", Toast.LENGTH_SHORT).show();
            return;
        }

        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        btnContinue.setEnabled(false);
        doFirebaseLogin(email, password);
    }

    private void doFirebaseLogin(String email, String password) {
        FirebaseHelper.getInstance().getAuth()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    failedAttempts = 0;
                    lockedUntilMs  = 0;
                    goToHome();
                })
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    failedAttempts++;
                    int remaining = MAX_ATTEMPTS - failedAttempts;

                    if (remaining <= 0) {
                        lockedUntilMs  = SystemClock.elapsedRealtime() + 30_000;
                        failedAttempts = 0;
                        Toast.makeText(this,
                                "Too many failed attempts. Locked for 30 seconds.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
