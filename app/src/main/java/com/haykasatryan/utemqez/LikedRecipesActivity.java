package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class LikedRecipesActivity extends AppCompatActivity {

    private RecyclerView likedRecipesRecyclerView;
    private RecipeAdapter likedRecipesAdapter;
    private final List<Recipe> likedRecipesList = new ArrayList<>();
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_liked_recipes);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        likedRecipesRecyclerView = findViewById(R.id.likedRecipesRecyclerView);
        if (likedRecipesRecyclerView == null) {
            Log.e("LikedRecipesActivity", "RecyclerView not found in layout");
            finish();
            return;
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        likedRecipesRecyclerView.setLayoutManager(layoutManager);
        likedRecipesAdapter = new RecipeAdapter(likedRecipesList, R.layout.recipe_item_search);
        likedRecipesRecyclerView.setAdapter(likedRecipesAdapter);

        View profileButton = findViewById(R.id.nav_profile);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() == null) {
                    startActivity(new Intent(LikedRecipesActivity.this, LoginActivity.class));
                } else {
                    startActivity(new Intent(LikedRecipesActivity.this, ProfileActivity.class));
                }
            });
        } else {
            Log.w("LikedRecipesActivity", "Profile button not found in layout");
        }

        View closeButton = findViewById(R.id.nav_close);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                startActivity(new Intent(LikedRecipesActivity.this, HomeActivity.class));
                finish();
            });
        } else {
            Log.w("LikedRecipesActivity", "Close button not found in layout");
        }

        fetchLikedRecipes();
    }

    private void fetchLikedRecipes() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in to view liked recipes", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = user.getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<String> likedRecipeIds = (List<String>) documentSnapshot.get("likedRecipes");
                    Log.d("LikedRecipesActivity", "Liked Recipe IDs: " + likedRecipeIds);
                    if (likedRecipeIds != null && !likedRecipeIds.isEmpty()) {
                        db.collection("recipes").get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        likedRecipesList.clear();
                                        for (QueryDocumentSnapshot document : task.getResult()) {
                                            try {
                                                Recipe recipe = document.toObject(Recipe.class);
                                                recipe.setUserId(document.getId());
                                                String recipeId = String.valueOf(recipe.getId());
                                                Log.d("LikedRecipesActivity", "Recipe ID: " + recipeId + ", Title: " + recipe.getTitle());
                                                if (likedRecipeIds.contains(recipeId)) {
                                                    likedRecipesList.add(recipe);
                                                }
                                            } catch (Exception e) {
                                                Log.e("LikedRecipesActivity", "Error deserializing recipe: " + document.getId(), e);
                                            }
                                        }
                                        Log.d("LikedRecipesActivity", "Recipes added: " + likedRecipesList.size());
                                        likedRecipesAdapter.notifyDataSetChanged();
                                    } else {
                                        Log.e("LikedRecipesActivity", "Firestore error", task.getException());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("LikedRecipesActivity", "Firestore query failed", e);
                                    likedRecipesAdapter.notifyDataSetChanged();
                                });
                    } else {
                        likedRecipesAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LikedRecipesActivity", "Error fetching user data", e);
                    likedRecipesAdapter.notifyDataSetChanged();
                });
    }
}