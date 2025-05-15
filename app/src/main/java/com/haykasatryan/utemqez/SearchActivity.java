package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    private RecyclerView searchRecipesRecyclerView;
    private RecipeAdapter searchRecipesAdapter;
    private final List<Recipe> searchRecipesList = new ArrayList<>();
    private final Set<Long> searchRecipeIds = new HashSet<>();
    private EditText searchBar;
    private ImageButton searchButton;

    // Pagination variables
    private static final int PAGE_SIZE = 10;
    private DocumentSnapshot lastDoc = null;
    private boolean isLoading = false;
    private String currentQuery = "";
    private int noResultsCount = 0; // Track consecutive empty result pages
    private static final int MAX_NO_RESULTS_PAGES = 3; // Stop after 3 empty pages

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize RecyclerView
        searchRecipesRecyclerView = findViewById(R.id.searchRecipesRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        searchRecipesRecyclerView.setLayoutManager(layoutManager);
        searchRecipesAdapter = new RecipeAdapter(searchRecipesList, R.layout.recipe_item_search);
        searchRecipesRecyclerView.setAdapter(searchRecipesAdapter);

        // Initialize search bar and button
        searchBar = findViewById(R.id.searchBar);
        searchButton = findViewById(R.id.searchButton);
        searchButton.setOnClickListener(v -> {
            currentQuery = searchBar.getText().toString().trim().toLowerCase(Locale.US);
            Log.d(TAG, "Search initiated with query: " + currentQuery);
            lastDoc = null; // Reset pagination for new query
            searchRecipeIds.clear();
            searchRecipesList.clear();
            noResultsCount = 0; // Reset no results counter
            searchRecipesAdapter.updateList(new ArrayList<>()); // Clear RecyclerView
            filterRecipes(currentQuery);
        });

        // Add scroll listener
        searchRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                if (!isLoading && totalItemCount <= (lastVisibleItem + 3)) {
                    Log.d(TAG, "Loading more recipes for query: " + currentQuery);
                    filterRecipes(currentQuery); // Load more recipes
                }
            }
        });

        // Fetch initial recipes
        Log.d(TAG, "Fetching initial recipes");
        filterRecipes("");

        // Bottom navigation click listeners
        ImageButton navHome = findViewById(R.id.nav_home);
        ImageButton navSearch = findViewById(R.id.nav_search);
        ImageButton navAi = findViewById(R.id.nav_ai);
        ImageButton navLiked = findViewById(R.id.nav_liked);

        navHome.setOnClickListener(v -> {
            startActivity(new Intent(SearchActivity.this, HomeActivity.class));
            finish();
        });

        navSearch.setOnClickListener(v -> {});
        navAi.setOnClickListener(v -> {});
        navLiked.setOnClickListener(v -> {});

        // Profile button click listener
        ImageButton profileButton = findViewById(R.id.nav_profile);
        profileButton.setOnClickListener(v -> {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                startActivity(new Intent(SearchActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(SearchActivity.this, ProfileActivity.class));
            }
        });
    }

    private void filterRecipes(String query) {
        if (isLoading) {
            Log.d(TAG, "Skipping filterRecipes, already loading");
            return;
        }
        isLoading = true;
        searchRecipesAdapter.setLoading(true); // Show loading indicator

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query baseQuery = db.collection("recipes")
                .orderBy("title")
                .limit(PAGE_SIZE);

        if (lastDoc != null) {
            baseQuery = baseQuery.startAfter(lastDoc);
        }

        baseQuery.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Recipe> filteredList = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (recipe != null && !searchRecipeIds.contains(recipe.getId())) {
                        // Check if recipe matches query (title, ingredients, category, or instructions)
                        boolean matches = false;
                        String queryLower = query.toLowerCase(Locale.US);
                        Log.d(TAG, "Checking recipe ID: " + recipe.getId() + ", Title: " + (recipe.getTitle() != null ? recipe.getTitle() : "null"));

                        // Title match
                        if (recipe.getTitle() != null && recipe.getTitle().toLowerCase(Locale.US).contains(queryLower)) {
                            matches = true;
                            Log.d(TAG, "Match found in title: " + recipe.getTitle());
                        }

                        // Ingredient match
                        if (!matches && recipe.getIngredients() != null) {
                            for (Ingredient ingredient : recipe.getIngredients()) {
                                if (ingredient.getName() != null && ingredient.getName().toLowerCase(Locale.US).contains(queryLower)) {
                                    matches = true;
                                    Log.d(TAG, "Match found in ingredient: " + ingredient.getName());
                                    break;
                                }
                            }
                        }

                        // Category match
                        if (!matches && recipe.getCategory() != null) {
                            for (String category : recipe.getCategory()) {
                                if (category != null && category.toLowerCase(Locale.US).contains(queryLower)) {
                                    matches = true;
                                    Log.d(TAG, "Match found in category: " + category);
                                    break;
                                }
                            }
                        }

                        // Instructions match
                        if (!matches && recipe.getInstructions() != null && recipe.getInstructions().toLowerCase(Locale.US).contains(queryLower)) {
                            matches = true;
                            Log.d(TAG, "Match found in instructions: " + recipe.getInstructions().substring(0, Math.min(50, recipe.getInstructions().length())) + "...");
                        }

                        if (query.isEmpty() || matches) {
                            filteredList.add(recipe);
                            searchRecipeIds.add(recipe.getId());
                        }
                    }
                }
                Log.d(TAG, "Fetched " + filteredList.size() + " recipes for query: " + query);

                runOnUiThread(() -> {
                    List<Recipe> updatedList = new ArrayList<>(searchRecipesList);
                    updatedList.addAll(filteredList);
                    searchRecipesList.clear();
                    searchRecipesList.addAll(updatedList);
                    searchRecipesAdapter.updateList(updatedList); // Use DiffUtil
                    searchRecipesAdapter.setLoading(false); // Hide loading indicator
                    if (updatedList.isEmpty() && !query.isEmpty()) {
                        noResultsCount++;
                        if (noResultsCount < MAX_NO_RESULTS_PAGES) {
                            Log.d(TAG, "No results in this page, fetching next page");
                            isLoading = false; // Allow next fetch
                            filterRecipes(query); // Fetch next page
                        } else {
                            Toast.makeText(SearchActivity.this, "No recipes found", Toast.LENGTH_SHORT).show();
                            noResultsCount = 0; // Reset counter
                        }
                    } else {
                        noResultsCount = 0; // Reset counter on successful results
                    }
                });
            } else {
                Log.e(TAG, "Query failed", task.getException());
                runOnUiThread(() -> {
                    searchRecipesAdapter.setLoading(false);
                    Toast.makeText(SearchActivity.this, "Failed to load recipes", Toast.LENGTH_SHORT).show();
                });
            }
            if (!task.isSuccessful() || !task.getResult().isEmpty()) {
                lastDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);
            }
            isLoading = false;
        });
    }
}