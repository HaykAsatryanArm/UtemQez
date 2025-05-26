package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText searchBar;
    private ImageButton searchButton;
    private RecyclerView searchRecipesRecyclerView;
    private RecipeAdapter searchRecipesAdapter;
    private final List<Recipe> searchRecipesList = new ArrayList<>();
    private final Set<Long> searchRecipeIds = new HashSet<>();
    private DocumentSnapshot lastSearchDoc = null;
    private boolean isLoadingSearch = false;
    private static final int PAGE_SIZE = 10;
    private long lastSearchClickTime = 0;
    private static final long DEBOUNCE_MS = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        searchBar = findViewById(R.id.searchBar);
        searchButton = findViewById(R.id.searchButton);
        ImageButton profileButton = findViewById(R.id.nav_profile);
        searchRecipesRecyclerView = findViewById(R.id.searchRecipesRecyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        searchRecipesRecyclerView.setLayoutManager(layoutManager);
        searchRecipesAdapter = new RecipeAdapter(searchRecipesList, R.layout.recipe_item_search);
        searchRecipesRecyclerView.setAdapter(searchRecipesAdapter);

        profileButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(SearchActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(SearchActivity.this, ProfileActivity.class));
            }
        });

        searchButton.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSearchClickTime < DEBOUNCE_MS) {
                return;
            }
            lastSearchClickTime = currentTime;
            String query = searchBar.getText().toString().trim();
            searchRecipesList.clear();
            searchRecipeIds.clear();
            lastSearchDoc = null;
            searchRecipesAdapter.updateList(new ArrayList<>());
            searchRecipes(query);
        });

        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(SearchActivity.this, HomeActivity.class));
            finish();
        });
        findViewById(R.id.nav_search).setOnClickListener(v -> {});
        findViewById(R.id.nav_ai).setOnClickListener(v -> {
            startActivity(new Intent(SearchActivity.this, ChatActivity.class));
            finish();
        });
        findViewById(R.id.nav_liked).setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(SearchActivity.this, LoginActivity.class));
                Toast.makeText(this, "Please log in to view liked recipes", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(SearchActivity.this, LikedRecipesActivity.class));
            }
        });

        searchRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                if (!isLoadingSearch && totalItemCount <= (lastVisibleItem + 5)) {
                    searchRecipes(searchBar.getText().toString().trim());
                }
            }
        });

        searchRecipes("");
    }

    private void searchRecipes(String query) {
        if (isLoadingSearch) return;
        isLoadingSearch = true;
        searchRecipesAdapter.setLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query firestoreQuery = db.collection("recipes")
                .whereEqualTo("isApproved", true) // Only approved recipes
                .limit(PAGE_SIZE);

        if (lastSearchDoc != null) {
            firestoreQuery = firestoreQuery.startAfter(lastSearchDoc);
        }

        String lowerQuery = query != null ? query.trim().toLowerCase() : "";
        List<String> queryVariations = lowerQuery.isEmpty() ? new ArrayList<>() : generateQueryVariations(lowerQuery);

        firestoreQuery.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    try {
                        Recipe recipe = document.toObject(Recipe.class);
                        if (recipe == null) {
                            continue;
                        }
                        boolean matches = query.isEmpty() || matchesQuery(recipe, queryVariations);
                        if (!searchRecipeIds.contains((long) recipe.getId()) && matches) {
                            newRecipes.add(recipe);
                            searchRecipeIds.add((long) recipe.getId());
                        }
                    } catch (Exception e) {
                        Log.e("SearchActivity", "Error parsing recipe: " + document.getId(), e);
                    }
                }
                lastSearchDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                runOnUiThread(() -> {
                    List<Recipe> updatedList = new ArrayList<>(searchRecipesList);
                    updatedList.addAll(newRecipes);
                    searchRecipesList.clear();
                    searchRecipesList.addAll(updatedList);
                    searchRecipesAdapter.updateList(updatedList);
                    searchRecipesAdapter.setLoading(false);
                });
            } else {
                runOnUiThread(() -> {
                    searchRecipesAdapter.setLoading(false);
                });
            }
            isLoadingSearch = false;
        });
    }

    private List<String> generateQueryVariations(String query) {
        List<String> variations = new ArrayList<>();
        if (query == null || query.isEmpty()) return variations;
        variations.add(query);
        variations.add(query + "s");
        if (query.length() > 1) {
            variations.add(query.substring(0, 1).toUpperCase() + query.substring(1));
            variations.add(query.substring(0, 1).toUpperCase() + query.substring(1) + "s");
        }
        return variations;
    }

    private boolean matchesQuery(Recipe recipe, List<String> queryVariations) {
        if (recipe == null) {
            return false;
        }

        String title = recipe.getTitle() != null ? recipe.getTitle().toLowerCase() : "";
        if (!title.isEmpty() && queryVariations.stream().anyMatch(title::contains)) {
            return true;
        }

        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                String name = ingredient != null && ingredient.getName() != null ?
                        ingredient.getName().toLowerCase() : "";
                if (!name.isEmpty() && queryVariations.stream().anyMatch(name::contains)) {
                    return true;
                }
            }
        }

        String instructions = recipe.getInstructions() != null ?
                recipe.getInstructions().toLowerCase() : "";
        if (!instructions.isEmpty() && queryVariations.stream().anyMatch(instructions::contains)) {
            return true;
        }

        return false;
    }
}