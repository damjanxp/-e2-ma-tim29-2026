package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.main.MainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Ekran za prijavu korisnika.
 * KT2: koristi {@link UserRepository} za Firebase autentifikaciju.
 * Podržava prijavu putem emaila ili korisničkog imena.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tlEmail;
    private TextInputLayout tlPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private TextView tvForgotPassword;
    private TextView tvRegister;
    private ProgressBar pbLoading;

    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userRepository = UserRepository.getInstance();

        initViews();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Ako je korisnik već prijavljen i email je verifikovan, preskoči login ekran
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null && current.isEmailVerified()) {
            goToMain();
        }
    }

    // -------------------------------------------------------------------------
    // Inicijalizacija
    // -------------------------------------------------------------------------

    private void initViews() {
        tlEmail        = findViewById(R.id.tlEmail);
        tlPassword     = findViewById(R.id.tlPassword);
        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        btnLogin       = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister     = findViewById(R.id.tvRegister);
        pbLoading      = findViewById(R.id.pbLoading);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> onLoginClicked());
        tvForgotPassword.setOnClickListener(v -> onForgotPasswordClicked());
        tvRegister.setOnClickListener(v -> onRegisterClicked());
    }

    // -------------------------------------------------------------------------
    // Akcije
    // -------------------------------------------------------------------------

    private void onLoginClicked() {
        tlEmail.setError(null);
        tlPassword.setError(null);

        String emailOrUsername = etEmail.getText() != null
                ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString() : "";

        if (!validateInputs(emailOrUsername, password)) {
            return;
        }

        setLoading(true);

        userRepository.login(emailOrUsername, password, new UserRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                goToMain();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void onForgotPasswordClicked() {
        startActivity(new Intent(this, ForgotPasswordActivity.class));
    }

    private void onRegisterClicked() {
        startActivity(new Intent(this, RegisterActivity.class));
    }

    // -------------------------------------------------------------------------
    // Validacija
    // -------------------------------------------------------------------------

    /**
     * Validira unesene podatke i postavlja greške na odgovarajuće TextInputLayout-ove.
     *
     * @return true ako su svi unosi ispravni, false u suprotnom
     */
    private boolean validateInputs(String emailOrUsername, String password) {
        boolean valid = true;

        if (emailOrUsername.isEmpty()) {
            tlEmail.setError(getString(R.string.error_field_required));
            valid = false;
        }

        if (password.isEmpty()) {
            tlPassword.setError(getString(R.string.error_field_required));
            valid = false;
        }

        return valid;
    }

    // -------------------------------------------------------------------------
    // Pomoćne metode
    // -------------------------------------------------------------------------

    /** Prikazuje ili skriva ProgressBar i onemogućava/omogućava dugme za prijavu. */
    private void setLoading(boolean isLoading) {
        pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
    }

    /** Prikazuje Snackbar sa porukom greške. */
    private void showError(String message) {
        Snackbar.make(btnLogin, message, Snackbar.LENGTH_LONG).show();
    }

    /** Prelazi na MainActivity i zatvara back stack. */
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
