package com.example.slagalica.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

import java.util.regex.Pattern;

/**
 * Ekran za registraciju novog korisnika.
 * KT2: koristi {@link UserRepository} za Firebase registraciju sa email verifikacijom.
 */
public class RegisterActivity extends AppCompatActivity {

    /** Regex za validaciju email adrese. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Regex za korisničko ime: slova, cifre i donja crta, min 3 karaktera. */
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,}$");

    private TextInputLayout tlEmail;
    private TextInputLayout tlUsername;
    private TextInputLayout tlPassword;
    private TextInputLayout tlConfirmPassword;
    private TextInputLayout tlRegion;

    private TextInputEditText etEmail;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private AutoCompleteTextView actvRegion;

    private MaterialButton btnRegister;
    private TextView tvLogin;
    private ProgressBar pbLoading;

    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userRepository = UserRepository.getInstance();

        initViews();
        setupRegionDropdown();
        setupListeners();
    }

    // -------------------------------------------------------------------------
    // Inicijalizacija
    // -------------------------------------------------------------------------

    private void initViews() {
        tlEmail           = findViewById(R.id.tlEmail);
        tlUsername        = findViewById(R.id.tlUsername);
        tlPassword        = findViewById(R.id.tlPassword);
        tlConfirmPassword = findViewById(R.id.tlConfirmPassword);
        tlRegion          = findViewById(R.id.tlRegion);

        etEmail           = findViewById(R.id.etEmail);
        etUsername        = findViewById(R.id.etUsername);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        actvRegion        = findViewById(R.id.actvRegion);

        btnRegister = findViewById(R.id.btnRegister);
        tvLogin     = findViewById(R.id.tvLogin);
        pbLoading   = findViewById(R.id.pbLoading);
    }

    private void setupRegionDropdown() {
        String[] regions = getResources().getStringArray(R.array.regions_array);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                regions
        );
        actvRegion.setAdapter(adapter);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> onRegisterClicked());
        tvLogin.setOnClickListener(v -> finish());
    }

    // -------------------------------------------------------------------------
    // Registracija
    // -------------------------------------------------------------------------

    private void onRegisterClicked() {
        clearErrors();

        String email           = getText(etEmail);
        String username        = getText(etUsername);
        String password        = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);
        String region          = actvRegion.getText() != null
                ? actvRegion.getText().toString().trim() : "";

        if (!validateInputs(email, username, password, confirmPassword, region)) {
            return;
        }

        setLoading(true);

        userRepository.register(email, username, region, password,
                new UserRepository.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        setLoading(false);
                        showVerificationDialog();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showError(message);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Dijalozi i feedback
    // -------------------------------------------------------------------------

    /**
     * Prikazuje AlertDialog koji obaveštava korisnika da proveri mejl.
     * Klik na OK zatvara RegisterActivity i vraća na LoginActivity.
     */
    private void showVerificationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.register_dialog_verify_title))
                .setMessage(getString(R.string.register_dialog_verify_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.register_dialog_verify_ok),
                        (dialog, which) -> finish())
                .show();
    }

    /** Prikazuje Snackbar sa porukom greške. */
    private void showError(String message) {
        Snackbar.make(btnRegister, message, Snackbar.LENGTH_LONG).show();
    }

    /** Prikazuje ili skriva ProgressBar i onemogućava/omogućava dugme. */
    private void setLoading(boolean isLoading) {
        pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
    }

    // -------------------------------------------------------------------------
    // Validacija
    // -------------------------------------------------------------------------

    /**
     * Validira sva polja forme i postavlja odgovarajuće greške na TextInputLayout-ove.
     * Proverava sva polja odjednom — korisnik vidi sve greške istovremeno.
     *
     * @return true ako su svi unosi ispravni, false ako postoji bar jedna greška
     */
    private boolean validateInputs(String email, String username,
                                   String password, String confirmPassword,
                                   String region) {
        boolean valid = true;

        if (email.isEmpty()) {
            tlEmail.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            tlEmail.setError(getString(R.string.error_invalid_email));
            valid = false;
        }

        if (username.isEmpty()) {
            tlUsername.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (username.length() < 3) {
            tlUsername.setError(getString(R.string.error_username_too_short));
            valid = false;
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            tlUsername.setError(getString(R.string.error_username_invalid_chars));
            valid = false;
        }

        if (password.isEmpty()) {
            tlPassword.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (password.length() < 6) {
            tlPassword.setError(getString(R.string.error_password_too_short));
            valid = false;
        }

        if (confirmPassword.isEmpty()) {
            tlConfirmPassword.setError(getString(R.string.error_field_required));
            valid = false;
        } else if (!confirmPassword.equals(password)) {
            tlConfirmPassword.setError(getString(R.string.error_passwords_do_not_match));
            valid = false;
        }

        if (region.isEmpty()) {
            tlRegion.setError(getString(R.string.error_region_not_selected));
            valid = false;
        }

        return valid;
    }

    /** Resetuje sve greške na svim TextInputLayout-ovima pre nove validacije. */
    private void clearErrors() {
        tlEmail.setError(null);
        tlUsername.setError(null);
        tlPassword.setError(null);
        tlConfirmPassword.setError(null);
        tlRegion.setError(null);
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
