package com.haykasatryan.utemqez;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
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
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private FirebaseAuth mAuth;
    private TextView welcomeText;
    private RecyclerView recipeRecyclerView, allRecipesRecyclerView;
    private RecipeAdapter categoryRecipeAdapter, allRecipesAdapter;
    private final List<Recipe> categoryRecipeList = new ArrayList<>();
    private final List<Recipe> allRecipesList = new ArrayList<>();
    private Button buttonBreakfast, buttonSalads, buttonDinner, buttonSnacks;
    private Button activeButton;
    private static final int PAGE_SIZE = 10;
    private DocumentSnapshot lastCategoryDoc = null;
    private DocumentSnapshot lastAllRecipesDoc = null;
    private boolean isLoadingCategory = false;
    private boolean isLoadingAllRecipes = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastScrollTime = 0;
    private static final long DEBOUNCE_INTERVAL_MS = 500;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        initializeUserData(user, db);

        welcomeText = view.findViewById(R.id.header_title);
        ImageButton profileButton = view.findViewById(R.id.nav_profile);

        if (user != null) {
            String userName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            welcomeText.setText("Welcome, " + userName + "!");
            welcomeText.setVisibility(View.VISIBLE);
        } else {
            welcomeText.setVisibility(View.GONE);
        }

        profileButton.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            if (mAuth.getCurrentUser() == null) {
                navController.navigate(R.id.action_homeFragment_to_loginActivity);
            } else {
                navController.navigate(R.id.action_homeFragment_to_profileFragment);
            }
        });

        recipeRecyclerView = view.findViewById(R.id.recipeRecyclerView);
        LinearLayoutManager categoryLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        recipeRecyclerView.setLayoutManager(categoryLayoutManager);
        categoryRecipeAdapter = new RecipeAdapter(categoryRecipeList, R.layout.recipe_item_main);
        recipeRecyclerView.setAdapter(categoryRecipeAdapter);

        allRecipesRecyclerView = view.findViewById(R.id.allRecipesRecyclerView);
        LinearLayoutManager allRecipesLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        allRecipesRecyclerView.setLayoutManager(allRecipesLayoutManager);
        allRecipesAdapter = new RecipeAdapter(allRecipesList, R.layout.recipe_item_search);
        allRecipesRecyclerView.setAdapter(allRecipesAdapter);

        buttonBreakfast = view.findViewById(R.id.buttonBreakfast);
        buttonSalads = view.findViewById(R.id.buttonSalads);
        buttonDinner = view.findViewById(R.id.buttonDinner);
        buttonSnacks = view.findViewById(R.id.buttonSnacks);

        activeButton = buttonBreakfast;
        updateCategoryButtonState(buttonBreakfast);
        setCategoryButtonListeners();
        fetchRecipesByCategory("Breakfast");
        fetchAllRecipes();

        allRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastScrollTime < DEBOUNCE_INTERVAL_MS) return;
                lastScrollTime = currentTime;

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (!isLoadingAllRecipes && totalItemCount <= (lastVisibleItem + 5)) {
                    fetchAllRecipes();
                }
            }
        });

        return view;
    }

    private void initializeUserData(FirebaseUser user, FirebaseFirestore db) {
        if (user == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isUserInitialized = prefs.getBoolean("user_initialized_" + user.getUid(), false);
        if (!isUserInitialized) {
            DocumentReference userRef = db.collection("users").document(user.getUid());
            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (!isAdded()) return;
                if (!documentSnapshot.exists()) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("likedRecipes", new ArrayList<String>());
                    userRef.set(userData)
                            .addOnSuccessListener(aVoid -> {
                                prefs.edit().putBoolean("user_initialized_" + user.getUid(), true).apply();
                                Log.d("Firestore", "User document created");
                            })
                            .addOnFailureListener(e -> Log.w("Firestore", "Error creating user document", e));
                } else if (documentSnapshot.get("likedRecipes") == null) {
                    userRef.update("likedRecipes", new ArrayList<String>())
                            .addOnSuccessListener(aVoid -> {
                                prefs.edit().putBoolean("user_initialized_" + user.getUid(), true).apply();
                                Log.d("Firestore", "likedRecipes initialized");
                            })
                            .addOnFailureListener(e -> Log.w("Firestore", "Error initializing likedRecipes", e));
                } else {
                    prefs.edit().putBoolean("user_initialized_" + user.getUid(), true).apply();
                }
            });
        }
    }

    private void setCategoryButtonListeners() {
        buttonBreakfast.setOnClickListener(v -> handleCategorySelection("Breakfast", buttonBreakfast));
        buttonSalads.setOnClickListener(v -> handleCategorySelection("Salads", buttonSalads));
        buttonDinner.setOnClickListener(v -> handleCategorySelection("Dinner", buttonDinner));
        buttonSnacks.setOnClickListener(v -> handleCategorySelection("Snacks", buttonSnacks));
    }

    private void updateCategoryButtonState(Button selectedButton) {
        if (activeButton != null) {
            activeButton.setSelected(false);
            activeButton.setTextColor(getResources().getColor(R.color.blackot));
        }
        selectedButton.setSelected(true);
        selectedButton.setTextColor(getResources().getColor(R.color.white));
        activeButton = selectedButton;
    }

    private void handleCategorySelection(String category, Button selectedButton) {
        if (activeButton != selectedButton) {
            updateCategoryButtonState(selectedButton);
            lastCategoryDoc = null;
            categoryRecipeList.clear();
            mainHandler.post(() -> {
                if (!isAdded()) return;
                categoryRecipeAdapter.updateList(new ArrayList<>());
            });
            fetchRecipesByCategory(category);
        }
    }

    private void fetchRecipesByCategory(String selectedCategory) {
        if (isLoadingCategory) return;
        isLoadingCategory = true;
        mainHandler.post(() -> {
            if (!isAdded()) return;
            categoryRecipeAdapter.setLoading(true);
        });

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query = db.collection("recipes")
                .whereArrayContains("category", selectedCategory)
                .whereEqualTo("isApproved", true)
                .limit(PAGE_SIZE);

        if (lastCategoryDoc != null) {
            query = query.startAfter(lastCategoryDoc);
        }

        query.get().addOnCompleteListener(task -> {
            if (!isAdded()) {
                isLoadingCategory = false;
                return;
            }
            isLoadingCategory = false;
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (recipe != null) {
                        recipe.setUserId(document.getId());
                        newRecipes.add(recipe);
                    }
                }
                lastCategoryDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    categoryRecipeList.addAll(newRecipes);
                    categoryRecipeAdapter.updateList(categoryRecipeList);
                    categoryRecipeAdapter.setLoading(false);
                });
            } else {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Error loading recipes", Toast.LENGTH_SHORT).show();
                    categoryRecipeAdapter.setLoading(false);
                });
            }
        });
    }

    private void fetchAllRecipes() {
        if (isLoadingAllRecipes) return;
        isLoadingAllRecipes = true;
        mainHandler.post(() -> {
            if (!isAdded()) return;
            allRecipesAdapter.setLoading(true);
        });

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query = db.collection("recipes")
                .whereEqualTo("isApproved", true)
                .limit(PAGE_SIZE);
        if (lastAllRecipesDoc != null) {
            query = query.startAfter(lastAllRecipesDoc);
        }

        query.get().addOnCompleteListener(task -> {
            if (!isAdded()) {
                isLoadingAllRecipes = false;
                return;
            }
            isLoadingAllRecipes = false;
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    Recipe recipe = document.toObject(Recipe.class);
                    if (recipe != null) {
                        recipe.setUserId(document.getId());
                        newRecipes.add(recipe);
                    }
                }
                Log.d("HomeFragment", "Fetched " + newRecipes.size() + " recipes for Popular section");
                lastAllRecipesDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    allRecipesList.addAll(newRecipes);
                    allRecipesAdapter.updateList(allRecipesList);
                    allRecipesAdapter.setLoading(false);
                    if (newRecipes.isEmpty() && allRecipesList.isEmpty()) {
                        Log.w("HomeFragment", "No recipes loaded for Popular section");
                        Toast.makeText(requireContext(), "No popular recipes available", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Log.e("HomeFragment", "Error fetching popular recipes: " + task.getException().getMessage());
                    Toast.makeText(requireContext(), "Error loading popular recipes", Toast.LENGTH_SHORT).show();
                    allRecipesAdapter.setLoading(false);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        categoryRecipeList.clear();
        allRecipesList.clear();
        mainHandler.removeCallbacksAndMessages(null);
    }
}