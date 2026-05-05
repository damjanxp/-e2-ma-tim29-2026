package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Ekran za prijavu korisnika.
 * KT1: samo GUI logika, bez Firebase autentifikacije.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tlEmail;
    private TextInputLayout tlPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private TextView tvForgotPassword;
    private TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();
    }

    private void initViews() {
        tlEmail = findViewById(R.id.tlEmail);
        tlPassword = findViewById(R.id.tlPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> onLoginClicked());
        tvForgotPassword.setOnClickListener(v -> onForgotPasswordClicked());
        tvRegister.setOnClickListener(v -> onRegisterClicked());
    }

    private void onLoginClicked() {
        // Resetuj prethodne greške
        tlEmail.setError(null);
        tlPassword.setError(null);

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (!validateInputs(email, password)) {
            return;
        }

        // KT1: mock login — Firebase autentifikacija se dodaje u KT2
        Toast.makeText(this, "Login uspesan (mock)", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Validira unesene podatke i postavlja greške na odgovarajuće TextInputLayout-ove.
     *
     * @return true ako su svi unosi ispravni, false u suprotnom
     */
    private boolean validateInputs(String email, String password) {
        boolean valid = true;

        if (email.isEmpty()) {
            tlEmail.setError(getString(R.string.error_field_required));
            valid = false;
        }

        if (password.isEmpty()) {
            tlPassword.setError(getString(R.string.error_field_required));
            valid = false;
        }

        return valid;
    }

    private void onForgotPasswordClicked() {
        startActivity(new Intent(this, ForgotPasswordActivity.class));
    }

    private void onRegisterClicked() {
        startActivity(new Intent(this, RegisterActivity.class));
    }
}


