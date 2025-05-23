package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
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
        setContentView(R.layout.activity_liked_recipes);

        mAuth = FirebaseAuth.getInstance();
        likedRecipesRecyclerView = findViewById(R.id.likedRecipesRecyclerView);
        if (likedRecipesRecyclerView == null) {
            Toast.makeText(this, "RecyclerView not found in layout", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        likedRecipesRecyclerView.setLayoutManager(layoutManager);
        likedRecipesAdapter = new RecipeAdapter(likedRecipesList, R.layout.recipe_item_search);
        likedRecipesRecyclerView.setAdapter(likedRecipesAdapter);

        fetchLikedRecipes();
    }

    private void fetchLikedRecipes() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<String> likedRecipeIds = (List<String>) documentSnapshot.get("likedRecipes");
                    if (likedRecipeIds != null && !likedRecipeIds.isEmpty()) {
                        db.collection("recipes").get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        likedRecipesList.clear();
                                        for (QueryDocumentSnapshot document : task.getResult()) {
                                            Recipe recipe = document.toObject(Recipe.class);
                                            recipe.setUserId(document.getId());
                                            if (likedRecipeIds.contains(String.valueOf(recipe.getId()))) {
                                                likedRecipesList.add(recipe);
                                            }
                                        }
                                        likedRecipesAdapter.notifyDataSetChanged(); // Safer than updateList
                                        if (likedRecipesList.isEmpty()) {
                                            Toast.makeText(this, "No liked recipes found", Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        Toast.makeText(this, "Error loading liked recipes", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(this, "No liked recipes found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching user data", Toast.LENGTH_SHORT).show();
                });
    }
}