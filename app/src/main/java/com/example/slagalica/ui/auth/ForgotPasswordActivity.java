package com.example.slagalica.ui.auth;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Ekran za promenu lozinke.
 * KT1: samo GUI logika i validacija, bez Firebase poziva.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputLayout tlCurrentPassword;
    private TextInputLayout tlNewPassword;
    private TextInputLayout tlConfirmNewPassword;

    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmNewPassword;

    private MaterialButton btnChangePassword;
    private TextView tvCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initViews();
        setupListeners();
    }

    private void initViews() {
        tlCurrentPassword = findViewById(R.id.tlCurrentPassword);
        tlNewPassword = findViewById(R.id.tlNewPassword);
        tlConfirmNewPassword = findViewById(R.id.tlConfirmNewPassword);

        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        tvCancel = findViewById(R.id.tvCancel);
    }

    private void setupListeners() {
        btnChangePassword.setOnClickListener(v -> onChangePasswordClicked());
        tvCancel.setOnClickListener(v -> finish());
    }

    private void onChangePasswordClicked() {
        clearErrors();

        String currentPassword = getText(etCurrentPassword);
        String newPassword = getText(etNewPassword);
        String confirmNewPassword = getText(etConfirmNewPassword);

        if (!validateInputs(currentPassword, newPassword, confirmNewPassword)) {
            return;
        }

        // KT1: mock promena lozinke — Firebase se dodaje u KT2
        Toast.makeText(this, getString(R.string.forgot_password_success_mock), Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Validira sva tri polja i postavlja greške na odgovarajuće TextInputLayout-ove.
     *
     * @return true ako su svi unosi ispravni, false ako postoji bar jedna greška
     */
    private boolean validateInputs(String currentPassword, String newPassword, String confirmNewPassword) {
        boolean valid = true;

        if (currentPassword.isEmpty()) {
            tlCurrentPassword.setError(getString(R.string.error_field_required));
            valid = false;
        }

        if (newPassword.isEmpty()) {
            tlNewPassword.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (newPassword.length() < 6) {
            tlNewPassword.setError(getString(R.string.error_password_too_short));
            valid = false;
        }

        if (confirmNewPassword.isEmpty()) {
            tlConfirmNewPassword.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (!confirmNewPassword.equals(newPassword)) {
            tlConfirmNewPassword.setError(getString(R.string.error_passwords_do_not_match));
            valid = false;
        }

        return valid;
    }

    private void clearErrors() {
        tlCurrentPassword.setError(null);
        tlNewPassword.setError(null);
        tlConfirmNewPassword.setError(null);
    }

    /**
     * Pomoćna metoda: čita i trim-uje tekst iz TextInputEditText.
     *
     * @param field polje iz koga se čita tekst
     * @return trimmovani tekst, ili prazan string ako je null
     */
    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
