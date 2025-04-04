package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button btnLogin, btnRegister;
    private TextView welcomeText;
    private static final String TAG = "HomeActivity";

    private RecyclerView recipeRecyclerView, allRecipesRecyclerView;
    private RecipeAdapter categoryRecipeAdapter, allRecipesAdapter;
    private final List<Recipe> categoryRecipeList = new ArrayList<>();
    private final List<Recipe> allRecipesList = new ArrayList<>();

    private Button buttonBreakfast, buttonSalads, buttonDinner, buttonSnacks;
    private Button activeButton; // Track the currently selected button

    @SuppressLint("SetTextI18n")
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
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        welcomeText = findViewById(R.id.header_title);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            welcomeText.setText("Welcome, " + userName + "!");
            btnLogin.setVisibility(View.GONE);
            btnRegister.setVisibility(View.GONE);
        } else {
            welcomeText.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            btnRegister.setVisibility(View.VISIBLE);
        }

        btnLogin.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, RegisterActivity.class)));
        welcomeText.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, ProfileActivity.class)));

        // Initialize category RecyclerView
        recipeRecyclerView = findViewById(R.id.recipeRecyclerView);
        recipeRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Initialize all recipes RecyclerView
        allRecipesRecyclerView = findViewById(R.id.allRecipesRecyclerView);
        allRecipesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        // Category buttons
        buttonBreakfast = findViewById(R.id.buttonBreakfast);
        buttonSalads = findViewById(R.id.buttonSalads);
        buttonDinner = findViewById(R.id.buttonDinner);
        buttonSnacks = findViewById(R.id.buttonSnacks);

        // Set initial active button
        activeButton = buttonBreakfast;
        buttonBreakfast.setSelected(true);
        buttonBreakfast.setTextColor(getResources().getColor(R.color.white));
        buttonSalads.setTextColor(getResources().getColor(R.color.blackot));
        buttonDinner.setTextColor(getResources().getColor(R.color.blackot));
        buttonSnacks.setTextColor(getResources().getColor(R.color.blackot));

        setCategoryButtonListeners();
        fetchRecipesByCategory("Breakfast");
        fetchAllRecipes();
    }

    private void setCategoryButtonListeners() {
        buttonBreakfast.setOnClickListener(v -> handleCategorySelection("Breakfast", buttonBreakfast));
        buttonSalads.setOnClickListener(v -> handleCategorySelection("Salads", buttonSalads));
        buttonDinner.setOnClickListener(v -> handleCategorySelection("Dinner", buttonDinner));
        buttonSnacks.setOnClickListener(v -> handleCategorySelection("Snacks", buttonSnacks));
    }

    private void handleCategorySelection(String category, Button selectedButton) {
        if (activeButton != selectedButton) {
            // Deselect the previous button
            if (activeButton != null) {
                activeButton.setSelected(false);
                activeButton.setTextColor(getResources().getColor(R.color.blackot));
            }
            // Select the new button
            selectedButton.setSelected(true);
            selectedButton.setTextColor(getResources().getColor(R.color.white));
            activeButton = selectedButton;
            fetchRecipesByCategory(category);
        }
    }

    // In HomeActivity.java
    private void fetchRecipesByCategory(String selectedCategory) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("recipes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        categoryRecipeList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Recipe recipe = document.toObject(Recipe.class);
                            if (recipe.getCategory() != null && recipe.getCategory().contains(selectedCategory)) {
                                categoryRecipeList.add(recipe);
                            }
                        }
                        runOnUiThread(() -> {
                            if (categoryRecipeAdapter == null) {
                                categoryRecipeAdapter = new RecipeAdapter(categoryRecipeList, R.layout.recipe_item_main);
                                recipeRecyclerView.setAdapter(categoryRecipeAdapter);
                            } else {
                                categoryRecipeAdapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        Log.e(TAG, "Error fetching category recipes", task.getException());
                    }
                });
    }

    private void fetchAllRecipes() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("recipes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        allRecipesList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Recipe recipe = document.toObject(Recipe.class);
                            allRecipesList.add(recipe);
                        }
                        runOnUiThread(() -> {
                            if (allRecipesAdapter == null) {
                                allRecipesAdapter = new RecipeAdapter(allRecipesList, R.layout.recipe_item_search);
                                allRecipesRecyclerView.setAdapter(allRecipesAdapter);
                            } else {
                                allRecipesAdapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        Log.e(TAG, "Error fetching all recipes", task.getException());
                    }
                });
    }
}