package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

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
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private RecyclerView searchRecipesRecyclerView;
    private RecipeAdapter searchRecipesAdapter;
    private final List<Recipe> searchRecipesList = new ArrayList<>();
    private EditText searchBar;

    // Pagination variables
    private static final int PAGE_SIZE = 10;
    private DocumentSnapshot lastDoc = null;
    private boolean isLoading = false;
    private String currentQuery = "";

    // Debounce variables
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private static final long DEBOUNCE_DELAY = 300; // 300ms delay
    private final Runnable searchRunnable = () -> {
        lastDoc = null; // Reset pagination for new query
        searchRecipesList.clear();
        filterRecipes(currentQuery);
    };

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

        // Initialize search bar with debounce
        searchBar = findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, DEBOUNCE_DELAY);
            }

            @Override
            public void afterTextChanged(Editable s) {}
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
                    filterRecipes(currentQuery); // Load more recipes
                }
            }
        });

        // Fetch initial recipes
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
        if (isLoading) return;
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
                    if (query.isEmpty() || recipe.getTitle().toLowerCase().contains(query.toLowerCase())) {
                        if (!searchRecipesList.contains(recipe)) { // Avoid duplicates
                            filteredList.add(recipe);
                        }
                    }
                }
                searchRecipesList.addAll(filteredList);
                lastDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                runOnUiThread(() -> {
                    searchRecipesAdapter.updateList(searchRecipesList); // Use DiffUtil
                    searchRecipesAdapter.setLoading(false); // Hide loading indicator
                });
            }
            isLoading = false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable); // Clean up
    }
}