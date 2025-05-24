package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;

public class AnalyticsActivity extends AppCompatActivity {

    private TextView userCount, recipeCount;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        db = FirebaseFirestore.getInstance();
        userCount = findViewById(R.id.userCount);
        recipeCount = findViewById(R.id.recipeCount);

        fetchAnalytics();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void fetchAnalytics() {
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userCount.setText("Total Users: " + queryDocumentSnapshots.size());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading user count", Toast.LENGTH_SHORT).show());

        db.collection("recipes").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recipeCount.setText("Total Recipes: " + queryDocumentSnapshots.size());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading recipe count", Toast.LENGTH_SHORT).show());
    }
}