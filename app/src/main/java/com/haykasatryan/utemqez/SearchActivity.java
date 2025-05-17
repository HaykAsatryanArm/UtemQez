package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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
import com.google.firebase.auth.FirebaseUser;
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

        // Initialize views
        searchBar = findViewById(R.id.searchBar);
        searchButton = findViewById(R.id.searchButton);
        ImageButton profileButton = findViewById(R.id.nav_profile);
        searchRecipesRecyclerView = findViewById(R.id.searchRecipesRecyclerView);

        // Set up RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        searchRecipesRecyclerView.setLayoutManager(layoutManager);
        searchRecipesAdapter = new RecipeAdapter(searchRecipesList, R.layout.recipe_item_search);
        searchRecipesRecyclerView.setAdapter(searchRecipesAdapter);

        // Profile button click listener
        profileButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(SearchActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(SearchActivity.this, ProfileActivity.class));
            }
        });

        // Search button click listener
        searchButton.setOnClickListener(v -> {
            String query = searchBar.getText().toString().trim();
            if (!query.isEmpty()) {
                searchRecipes(query);
            } else {
                Toast.makeText(this, "Please enter a search query", Toast.LENGTH_SHORT).show();
            }
        });

        // Bottom navigation click listeners
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
            Toast.makeText(this, "Liked recipes not implemented", Toast.LENGTH_SHORT).show();
        });

        // Add scroll listener for searchRecipesRecyclerView
        searchRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                if (!isLoadingSearch && totalItemCount <= (lastVisibleItem + 3)) {
                    searchRecipes(searchBar.getText().toString().trim());
                }
            }
        });
    }

    private void searchRecipes(String query) {
        if (isLoadingSearch || query.isEmpty()) return;
        isLoadingSearch = true;
        searchRecipesAdapter.setLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query firestoreQuery = db.collection("recipes")
                .whereArrayContainsAny("category", java.util.Arrays.asList(query.split("\\s+")))
                .limit(PAGE_SIZE);

        if (lastSearchDoc != null) {
            firestoreQuery = firestoreQuery.startAfter(lastSearchDoc);
        }

        firestoreQuery.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (!searchRecipeIds.contains(recipe.getId())) {
                        newRecipes.add(recipe);
                        searchRecipeIds.add(recipe.getId());
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
                    Toast.makeText(this, "Failed to search recipes: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    searchRecipesAdapter.setLoading(false);
                });
            }
            isLoadingSearch = false;
        });
    }
}