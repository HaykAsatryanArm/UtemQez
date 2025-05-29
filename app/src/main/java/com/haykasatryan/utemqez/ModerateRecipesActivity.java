package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class ModerateRecipesActivity extends AppCompatActivity {

    private RecyclerView recipesRecyclerView;
    private RecipeAdapter recipeAdapter;
    private final List<Recipe> recipeList = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_moderate_recipes);
            Log.d("ModerateRecipes", "Activity created, layout set");
        } catch (Exception e) {
            Log.e("ModerateRecipes", "Error setting content view", e);
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        recipesRecyclerView = findViewById(R.id.recipesRecyclerView);
        recipesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        try {
            recipeAdapter = new RecipeAdapter(recipeList, R.layout.recipe_item_moderate);
            recipeAdapter.setOnDeleteClickListener(this::deleteRecipe);
            recipeAdapter.setOnApproveClickListener(this::approveRecipe);
            recipesRecyclerView.setAdapter(recipeAdapter);
            Log.d("ModerateRecipes", "Adapter and listeners set");
        } catch (Exception e) {
            Log.e("ModerateRecipes", "Error initializing adapter", e);
            finish();
            return;
        }

        fetchUnapprovedRecipes();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void fetchUnapprovedRecipes() {
        Log.d("ModerateRecipes", "Fetching unapproved recipes");
        db.collection("recipes")
                .whereEqualTo("isApproved", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recipeList.clear();
                    Log.d("ModerateRecipes", "Found " + queryDocumentSnapshots.size() + " unapproved recipes");
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Recipe recipe = document.toObject(Recipe.class);
                            recipe.setUserId(document.getId());
                            Object idField = document.get("id");
                            if (idField instanceof Number) {
                                recipe.setId(((Number) idField).intValue());
                            } else if (idField instanceof String) {
                                try {
                                    recipe.setId(Integer.parseInt((String) idField));
                                } catch (NumberFormatException e) {
                                    Log.w("ModerateRecipes", "Invalid id format for recipe: " + document.getId() + ", using default ID");
                                    recipe.setId(0);
                                }
                            } else {
                                Log.w("ModerateRecipes", "No id field for recipe: " + document.getId() + ", using default ID");
                                recipe.setId(0);
                            }
                            if (recipe.getTitle() == null) recipe.setTitle("Untitled");
                            if (recipe.getImageUrl() == null) recipe.setImageUrl("");
                            if (recipe.getIngredients() == null) recipe.setIngredients(new ArrayList<>());
                            recipeList.add(recipe);
                            Log.d("ModerateRecipes", "Added recipe: " + recipe.getTitle() + ", ID: " + recipe.getId() + ", DocID: " + document.getId());
                        } catch (Exception e) {
                            Log.e("ModerateRecipes", "Error parsing recipe: " + document.getId() + ", Data: " + document.getData(), e);
                        }
                    }
                    try {
                        recipeAdapter.updateList(recipeList);
                        Log.d("ModerateRecipes", "Adapter updated with " + recipeList.size() + " recipes");
                    } catch (Exception e) {
                        Log.e("ModerateRecipes", "Error updating adapter", e);
                    }
                    findViewById(R.id.noRecipesText).setVisibility(recipeList.isEmpty() ? View.VISIBLE : View.GONE);
                    if (recipeList.isEmpty()) {
                        Log.w("ModerateRecipes", "No unapproved recipes added to list");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ModerateRecipes", "Error loading recipes", e);
                });
    }

    private void approveRecipe(Recipe recipe) {
        Log.d("ModerateRecipes", "Approving recipe: " + recipe.getUserId());
        db.collection("recipes")
                .document(recipe.getUserId())
                .update("isApproved", true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recipe approved", Toast.LENGTH_SHORT).show();
                    fetchUnapprovedRecipes();
                })
                .addOnFailureListener(e -> {
                    Log.e("ModerateRecipes", "Error approving recipe: " + recipe.getUserId(), e);
                });
    }

    private void deleteRecipe(Recipe recipe) {
        Log.d("ModerateRecipes", "Deleting recipe: " + recipe.getUserId());
        db.collection("recipes")
                .document(recipe.getUserId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recipe deleted", Toast.LENGTH_SHORT).show();
                    fetchUnapprovedRecipes();
                })
                .addOnFailureListener(e -> {
                    Log.e("ModerateRecipes", "Error deleting recipe: " + recipe.getUserId(), e);
                });
    }
}