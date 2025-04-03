package com.haykasatryan.utemqez;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    Button registerBtn;
    TextView toLogLink;
    EditText userName, userEmail, userPassword, userPasswordRe;
    FirebaseAuth mAuth;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userName = findViewById(R.id.name);
        userEmail = findViewById(R.id.email);
        userPassword = findViewById(R.id.password);
        userPasswordRe = findViewById(R.id.passwordRe);
        registerBtn = findViewById(R.id.registerBtn);
        toLogLink = findViewById(R.id.toLog);

        registerBtn.setBackgroundResource(R.drawable.button_background);

        mAuth = FirebaseAuth.getInstance();

        toLogLink.setOnClickListener(v -> startActivity(new Intent(RegisterActivity.this, LoginActivity.class)));

        registerBtn.setOnClickListener(view -> registerUser());

        setupPasswordToggle(userPassword, () -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                userPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                userPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
            } else {
                userPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                userPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
            }
            userPassword.setSelection(userPassword.getText().length()); // Keep cursor at end
        });

        // Set up password visibility toggle for userPasswordRe
        setupPasswordToggle(userPasswordRe, () -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            if (isConfirmPasswordVisible) {
                userPasswordRe.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                userPasswordRe.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
            } else {
                userPasswordRe.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                userPasswordRe.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
            }
            userPasswordRe.setSelection(userPasswordRe.getText().length()); // Keep cursor at end
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

    private void registerUser() {

        Log.d(TAG, "Clicked");

        String name = userName.getText().toString().trim();
        String email = userEmail.getText().toString().trim();
        String password = userPassword.getText().toString().trim();
        String passwordRe = userPasswordRe.getText().toString().trim();

        if (email.isEmpty()) {
            userEmail.setError("Email is required");
            userEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            userEmail.setError("Enter a valid email");
            userEmail.requestFocus();
            return;
        }
        if (password.isEmpty() || password.length() < 8) {
            userPassword.setError("Password must be at least 8 characters");
            userPassword.requestFocus();
            return;
        }
        if (!password.equals(passwordRe)) {
            userPasswordRe.setError("Passwords do not match");
            userPasswordRe.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    Log.d(TAG, "Registered");
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            updateUserProfile(user, name, email);
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this, "Registration failed! " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUserProfile(FirebaseUser user, String name, String email) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        saveUserToFirestore(user, name, email);
                    } else {
                        Toast.makeText(RegisterActivity.this, "Failed to update profile: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String name, String email) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("profilePicture", "");
        userData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User successfully added to Firestore");
                    sendVerificationEmail(user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding user to Firestore", e);
                    Toast.makeText(RegisterActivity.this, "Failed to store user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

    }
    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Verification email sent.");
                        Toast.makeText(RegisterActivity.this,
                                "Verification email sent. Check your inbox.",
                                Toast.LENGTH_LONG).show();
                        FirebaseAuth.getInstance().signOut();
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();
                    } else {
                        Log.e(TAG, "Failed to send verification email", task.getException());
                        Toast.makeText(RegisterActivity.this,
                                "Failed to send verification email: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

}
