package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
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

public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";
    private FirebaseAuth mAuth;
    private EditText searchBar;
    private ImageButton searchButton;
    private RecyclerView searchRecipesRecyclerView;
    private RecipeAdapter searchRecipesAdapter;
    private final List<Recipe> searchRecipesList = new ArrayList<>();
    private final Set<Integer> searchRecipeIds = new HashSet<>();
    private DocumentSnapshot lastSearchDoc = null;
    private boolean isLoadingSearch = false;
    private static final int PAGE_SIZE = 10;
    private long lastSearchClickTime = 0;
    private static final long DEBOUNCE_MS = 500;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchBar = view.findViewById(R.id.searchBar);
        searchButton = view.findViewById(R.id.searchButton);
        searchRecipesRecyclerView = view.findViewById(R.id.searchRecipesRecyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        searchRecipesRecyclerView.setLayoutManager(layoutManager);
        searchRecipesAdapter = new RecipeAdapter(searchRecipesList, R.layout.recipe_item_search);
        searchRecipesRecyclerView.setAdapter(searchRecipesAdapter);

        searchButton.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSearchClickTime < DEBOUNCE_MS) {
                Log.d(TAG, "Search button click debounced");
                return;
            }
            lastSearchClickTime = currentTime;
            String query = searchBar.getText().toString().trim();
            Log.d(TAG, "Search button clicked with query: " + query);
            searchRecipesList.clear();
            searchRecipeIds.clear();
            lastSearchDoc = null;
            searchRecipesAdapter.updateList(new ArrayList<>());
            searchRecipes(query);
        });

        searchRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (!isLoadingSearch && totalItemCount <= (lastVisibleItem + 5)) {
                    Log.d(TAG, "Loading more recipes, total items: " + totalItemCount + ", last visible: " + lastVisibleItem);
                    searchRecipes(searchBar.getText().toString().trim());
                }
            }
        });

        Log.d(TAG, "Initializing with empty search");
        searchRecipes("");

        return view;
    }

    private void searchRecipes(String query) {
        if (isLoadingSearch) {
            Log.d(TAG, "Search already in progress, skipping");
            return;
        }
        isLoadingSearch = true;
        searchRecipesAdapter.setLoading(true);
        Log.d(TAG, "Starting search for query: " + query);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query firestoreQuery = db.collection("recipes")
                .whereEqualTo("isApproved", true)
                .limit(PAGE_SIZE);

        if (lastSearchDoc != null) {
            firestoreQuery = firestoreQuery.startAfter(lastSearchDoc);
        }

        String lowerQuery = query != null ? query.trim().toLowerCase() : "";
        List<String> queryVariations = lowerQuery.isEmpty() ? new ArrayList<>() : generateQueryVariations(lowerQuery);

        firestoreQuery.get().addOnCompleteListener(task -> {
            if (!isAdded()) {
                Log.w(TAG, "Fragment detached, skipping UI update");
                isLoadingSearch = false;
                return;
            }
            isLoadingSearch = false;
            if (task.isSuccessful()) {
                List<Recipe> newRecipes = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult()) {
                    try {
                        Recipe recipe = document.toObject(Recipe.class);
                        if (recipe == null) {
                            Log.w(TAG, "Null recipe for document: " + document.getId());
                            continue;
                        }
                        recipe.setUserId(document.getId());
                        boolean matches = lowerQuery.isEmpty() || matchesQuery(recipe, queryVariations);
                        if (!searchRecipeIds.contains(recipe.getId()) && matches) {
                            newRecipes.add(recipe);
                            searchRecipeIds.add(recipe.getId());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing recipe: " + document.getId(), e);
                    }
                }
                Log.d(TAG, "Fetched " + newRecipes.size() + " recipes for query: " + query);
                lastSearchDoc = task.getResult().getDocuments().isEmpty() ? null :
                        task.getResult().getDocuments().get(task.getResult().size() - 1);

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment detached, skipping UI update");
                        return;
                    }
                    searchRecipesList.addAll(newRecipes);
                    searchRecipesAdapter.updateList(searchRecipesList);
                    searchRecipesAdapter.setLoading(false);
                    Log.d(TAG, "Adapter updated with " + searchRecipesList.size() + " recipes");
                    if (newRecipes.isEmpty() && searchRecipesList.isEmpty() && !lowerQuery.isEmpty()) {
                        Toast.makeText(requireContext(), "No recipes found", Toast.LENGTH_SHORT).show();
                    } else if (newRecipes.isEmpty() && searchRecipesList.isEmpty()) {
                        Log.w(TAG, "No recipes loaded for empty query");
                        Toast.makeText(requireContext(), "No recipes available", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment detached, skipping UI update");
                        return;
                    }
                    searchRecipesAdapter.setLoading(false);
                    Log.e(TAG, "Error fetching recipes: ", task.getException());
                    Toast.makeText(requireContext(), "Failed to load recipes", Toast.LENGTH_SHORT).show();
                });
            }
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