package com.haykasatryan.utemqez;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    Button loginBtn, guestBtn;
    TextView toRegLink, reset, skip;
    EditText userEmail, userPassword;
    FirebaseAuth mAuth;
    private boolean isPasswordVisible = false;
    private static final String GUEST_EMAIL = "individualproject2025@gmail.com";
    private static final String GUEST_PASSWORD = "Samsung2025";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        userEmail = findViewById(R.id.email);
        userPassword = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);
        guestBtn = findViewById(R.id.guestBtn);
        toRegLink = findViewById(R.id.toReg);
        reset = findViewById(R.id.forgot);
        skip = findViewById(R.id.skip);

        mAuth = FirebaseAuth.getInstance();

        toRegLink.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        skip.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, HomeActivity.class)));

        reset.setOnClickListener(v -> {
            String email = userEmail.getText().toString().trim();
            if (email.isEmpty()) {
                userEmail.setError("Write your email");
                userEmail.requestFocus();
                return;
            }
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(getApplicationContext(), "Check Your Email", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LoginActivity.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        loginBtn.setOnClickListener(v -> {
            String email = userEmail.getText().toString().trim();
            String password = userPassword.getText().toString().trim();
            if (email.isEmpty()) {
                userEmail.setError("Email is empty");
                userEmail.requestFocus();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                userEmail.setError("Enter a valid email");
                userEmail.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                userPassword.setError("Password is empty");
                userPassword.requestFocus();
                return;
            }
            if (password.length() < 6) {
                userPassword.setError("Password must be at least 6 characters");
                userPassword.requestFocus();
                return;
            }
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                finish();
                            } else {
                                Toast.makeText(LoginActivity.this,
                                        "Please verify your email before logging in.",
                                        Toast.LENGTH_LONG).show();
                                FirebaseAuth.getInstance().signOut();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Please check your login credentials.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        guestBtn.setOnClickListener(v -> {
            mAuth.signInWithEmailAndPassword(GUEST_EMAIL, GUEST_PASSWORD)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                Log.d("LoginActivity", "Guest login successful");
                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                finish();
                            } else {
                                Log.w("LoginActivity", "Guest email not verified");
                                Toast.makeText(LoginActivity.this,
                                        "Guest account email not verified.",
                                        Toast.LENGTH_LONG).show();
                                FirebaseAuth.getInstance().signOut();
                            }
                        } else {
                            Log.e("LoginActivity", "Guest login failed: " + task.getException().getMessage());
                            Toast.makeText(LoginActivity.this,
                                    "Guest login failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        setupPasswordToggle(userPassword, () -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                userPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                userPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
            } else {
                userPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                userPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
            }
            userPassword.setSelection(userPassword.getText().length());
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPasswordToggle(EditText editText, Runnable onToggle) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editText.getRight() - editText.getCompoundDrawables()[2].getBounds().width())) {
                    onToggle.run();
                    return true;
                }
            }
            return false;
        });
    }
}