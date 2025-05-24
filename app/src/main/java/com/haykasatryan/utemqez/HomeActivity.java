package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextView welcomeText;
    private RecyclerView recipeRecyclerView, allRecipesRecyclerView;
    private RecipeAdapter categoryRecipeAdapter, allRecipesAdapter;
    private final List<Recipe> categoryRecipeList = new ArrayList<>();
    private final List<Recipe> allRecipesList = new ArrayList<>();
    private final Set<Integer> categoryRecipeIds = new HashSet<>();
    private final Set<Integer> allRecipeIds = new HashSet<>();
    private Button buttonBreakfast, buttonSalads, buttonDinner, buttonSnacks;
    private Button activeButton;
    private static final int PAGE_SIZE = 10;
    private DocumentSnapshot lastCategoryDoc = null;
    private DocumentSnapshot lastAllRecipesDoc = null;
    private boolean isLoadingCategory = false;
    private boolean isLoadingAllRecipes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            DocumentReference userRef = db.collection("users").document(user.getUid());
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (!documentSnapshot.exists()) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("likedRecipes", new ArrayList<String>());
                    userRef.set(userData)
                            .addOnSuccessListener(aVoid -> Log.d("Firestore", "User document created"))
                            .addOnFailureListener(e -> Log.w("Firestore", "Error creating user document", e));
                } else if (documentSnapshot.get("likedRecipes") == null) {
                    userRef.update("likedRecipes", new ArrayList<String>())
                            .addOnSuccessListener(aVoid -> Log.d("Firestore", "likedRecipes initialized"))
                            .addOnFailureListener(e -> Log.w("Firestore", "Error initializing likedRecipes", e));
                }
            });
        }

        welcomeText = findViewById(R.id.header_title);
        ImageButton profileButton = findViewById(R.id.nav_profile);

        if (user != null) {
            String userName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            welcomeText.setText("Welcome, " + userName + "!");
            welcomeText.setVisibility(View.VISIBLE);
        } else {
            welcomeText.setVisibility(View.GONE);
        }

        profileButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
            }
        });

        recipeRecyclerView = findViewById(R.id.recipeRecyclerView);
        LinearLayoutManager categoryLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recipeRecyclerView.setLayoutManager(categoryLayoutManager);
        categoryRecipeAdapter = new RecipeAdapter(categoryRecipeList, R.layout.recipe_item_main);
        recipeRecyclerView.setAdapter(categoryRecipeAdapter);

        allRecipesRecyclerView = findViewById(R.id.allRecipesRecyclerView);
        LinearLayoutManager allRecipesLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        allRecipesRecyclerView.setLayoutManager(allRecipesLayoutManager);
        allRecipesAdapter = new RecipeAdapter(allRecipesList, R.layout.recipe_item_search);
        allRecipesRecyclerView.setAdapter(allRecipesAdapter);

        buttonBreakfast = findViewById(R.id.buttonBreakfast);
        buttonSalads = findViewById(R.id.buttonSalads);
        buttonDinner = findViewById(R.id.buttonDinner);
        buttonSnacks = findViewById(R.id.buttonSnacks);

        activeButton = buttonBreakfast;
        buttonBreakfast.setSelected(true);
        buttonBreakfast.setTextColor(getResources().getColor(R.color.white));
        buttonSalads.setTextColor(getResources().getColor(R.color.blackot));
        buttonDinner.setTextColor(getResources().getColor(R.color.blackot));
        buttonSnacks.setTextColor(getResources().getColor(R.color.blackot));

        setCategoryButtonListeners();
        fetchRecipesByCategory("Breakfast");
        fetchAllRecipes();

        allRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (!isLoadingAllRecipes && totalItemCount <= (lastVisibleItem + 5)) {
                    fetchAllRecipes();
                }
            }
        });

        findViewById(R.id.nav_home).setOnClickListener(v -> {});
        findViewById(R.id.nav_search).setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, SearchActivity.class)));
        findViewById(R.id.nav_ai).setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, ChatActivity.class)));
        findViewById(R.id.nav_liked).setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                Toast.makeText(this, "Please log in to view liked recipes", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(HomeActivity.this, LikedRecipesActivity.class));
            }
        });
    }

    private void setCategoryButtonListeners() {
        buttonBreakfast.setOnClickListener(v -> handleCategorySelection("Breakfast", buttonBreakfast));
        buttonSalads.setOnClickListener(v -> handleCategorySelection("Salads", buttonSalads));
        buttonDinner.setOnClickListener(v -> handleCategorySelection("Dinner", buttonDinner));
        buttonSnacks.setOnClickListener(v -> handleCategorySelection("Snacks", buttonSnacks));
    }

    private void handleCategorySelection(String category, Button selectedButton) {
        if (activeButton != selectedButton) {
            if (activeButton != null) {
                activeButton.setSelected(false);
                activeButton.setTextColor(getResources().getColor(R.color.blackot));
            }
            selectedButton.setSelected(true);
            selectedButton.setTextColor(getResources().getColor(R.color.white));
            activeButton = selectedButton;
            lastCategoryDoc = null;
            categoryRecipeIds.clear();
            categoryRecipeList.clear();
            categoryRecipeAdapter.updateList(new ArrayList<>());
            fetchRecipesByCategory(category);
        }
    }

    private void fetchRecipesByCategory(String selectedCategory) {
        if (isLoadingCategory) return;
        isLoadingCategory = true;
        categoryRecipeAdapter.setLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query = db.collection("recipes")
                .whereArrayContains("category", selectedCategory)
                .whereEqualTo("isApproved", true) // Only approved recipes
                .limit(PAGE_SIZE);

        if (lastCategoryDoc != null) {
            query = query.startAfter(lastCategoryDoc);
        }

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (recipe != null) {
                        recipe.setUserId(document.getId());
                        if (!categoryRecipeIds.contains(recipe.getId())) {
                            newRecipes.add(recipe);
                            categoryRecipeIds.add(recipe.getId());
                        }
                    }
                }
                lastCategoryDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                runOnUiThread(() -> {
                    List<Recipe> updatedList = new ArrayList<>(categoryRecipeList);
                    updatedList.addAll(newRecipes);
                    categoryRecipeList.clear();
                    categoryRecipeList.addAll(updatedList);
                    categoryRecipeAdapter.updateList(updatedList);
                    categoryRecipeAdapter.setLoading(false);
                    if (newRecipes.isEmpty()) {
                        Toast.makeText(HomeActivity.this, "No more " + selectedCategory + " recipes", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    categoryRecipeAdapter.setLoading(false);
                    Toast.makeText(HomeActivity.this, "Error loading " + selectedCategory + " recipes", Toast.LENGTH_SHORT).show();
                });
            }
            isLoadingCategory = false;
        });
    }

    private void fetchAllRecipes() {
        if (isLoadingAllRecipes) return;
        isLoadingAllRecipes = true;
        allRecipesAdapter.setLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query = db.collection("recipes")
                .whereEqualTo("isApproved", true) // Only approved recipes
                .limit(PAGE_SIZE);
        if (lastAllRecipesDoc != null) {
            query = query.startAfter(lastAllRecipesDoc);
        }

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (recipe != null) {
                        recipe.setUserId(document.getId());
                        if (!allRecipeIds.contains(recipe.getId())) {
                            newRecipes.add(recipe);
                            allRecipeIds.add(recipe.getId());
                        }
                    }
                }
                lastAllRecipesDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                runOnUiThread(() -> {
                    List<Recipe> updatedList = new ArrayList<>(allRecipesList);
                    updatedList.addAll(newRecipes);
                    allRecipesList.clear();
                    allRecipesList.addAll(updatedList);
                    allRecipesAdapter.updateList(updatedList);
                    allRecipesAdapter.setLoading(false);
                    if (newRecipes.isEmpty()) {
                        Toast.makeText(HomeActivity.this, "No more recipes to load", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    allRecipesAdapter.setLoading(false);
                    Toast.makeText(HomeActivity.this, "Error loading recipes", Toast.LENGTH_SHORT).show();
                });
            }
            isLoadingAllRecipes = false;
        });
    }
}