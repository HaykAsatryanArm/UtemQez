package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView adminTitle;
    private LinearLayout btnManageUsers, btnModerateRecipes, btnViewAnalytics;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        adminTitle = findViewById(R.id.adminTitle);
        btnManageUsers = findViewById(R.id.btnManageUsers);
        btnModerateRecipes = findViewById(R.id.btnModerateRecipes);
        btnViewAnalytics = findViewById(R.id.btnViewAnalytics);
        btnBack = findViewById(R.id.btnBack);

        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Boolean isAdmin = documentSnapshot.getBoolean("isAdmin");
                            if (isAdmin != null && isAdmin) {
                                adminTitle.setText("Admin Dashboard");
                            } else {
                                Toast.makeText(this, "Access denied: Not an admin", Toast.LENGTH_LONG).show();
                                startActivity(new Intent(this, ProfileActivity.class));
                                finish();
                            }
                        } else {
                            Toast.makeText(this, "User data not found", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(this, ProfileActivity.class));
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        startActivity(new Intent(this, ProfileActivity.class));
                        finish();
                    });
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }

        btnManageUsers.setOnClickListener(v -> startActivity(new Intent(this, ManageUsersActivity.class)));
        btnModerateRecipes.setOnClickListener(v -> startActivity(new Intent(this, ModerateRecipesActivity.class)));
        btnViewAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }
}