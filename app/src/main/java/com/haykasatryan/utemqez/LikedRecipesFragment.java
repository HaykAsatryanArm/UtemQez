package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class LikedRecipesFragment extends Fragment {

    private static final String TAG = "LikedRecipesFragment";
    private RecyclerView likedRecipesRecyclerView;
    private RecipeAdapter likedRecipesAdapter;
    private final List<Recipe> likedRecipesList = new ArrayList<>();
    private FirebaseAuth mAuth;
    private NavController navController;
    private DocumentSnapshot lastLikedDoc = null;
    private boolean isLoadingLiked = false;
    private boolean allRecipesLoaded = false;
    private static final int PAGE_SIZE = 10;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_liked_recipes, container, false);

        navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        if (navController == null) {
            Log.e(TAG, "NavController not found");
            return view;
        }

        likedRecipesRecyclerView = view.findViewById(R.id.likedRecipesRecyclerView);
        if (likedRecipesRecyclerView == null) {
            Log.e(TAG, "RecyclerView not found in layout");
            navController.navigate(R.id.action_likedRecipesFragment_to_homeFragment);
            return view;
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        likedRecipesRecyclerView.setLayoutManager(layoutManager);
        likedRecipesAdapter = new RecipeAdapter(likedRecipesList, R.layout.recipe_item_search);
        likedRecipesRecyclerView.setAdapter(likedRecipesAdapter);

        likedRecipesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (allRecipesLoaded || isLoadingLiked) {
                    Log.d(TAG, "All recipes loaded or fetch in progress, skipping scroll fetch");
                    return;
                }
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (totalItemCount <= (lastVisibleItem + 5)) {
                    Log.d(TAG, "Loading more liked recipes, total items: " + totalItemCount + ", last visible: " + lastVisibleItem);
                    fetchLikedRecipes();
                }
            }
        });

        fetchLikedRecipes();

        return view;
    }

    private void fetchLikedRecipes() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Please log in to view liked recipes", Toast.LENGTH_SHORT).show();
            if (navController != null) {
                navController.navigate(R.id.action_likedRecipesFragment_to_loginActivity);
            } else {
                Log.e(TAG, "NavController is null, cannot navigate");
            }
            return;
        }

        if (isLoadingLiked || allRecipesLoaded) {
            Log.d(TAG, "Fetch already in progress or all recipes loaded, skipping");
            return;
        }
        isLoadingLiked = true;
        likedRecipesAdapter.setLoading(true);
        Log.d(TAG, "Starting fetch for liked recipes for user: " + user.getUid());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = user.getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment is detached, aborting");
                        isLoadingLiked = false;
                        return;
                    }
                    List<String> likedRecipeIds = (List<String>) documentSnapshot.get("likedRecipes");
                    Log.d(TAG, "Liked Recipe IDs for user " + userId + ": " + likedRecipeIds);
                    if (likedRecipeIds == null || likedRecipeIds.isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                Log.w(TAG, "Fragment is detached, aborting");
                                return;
                            }
                            isLoadingLiked = false;
                            allRecipesLoaded = true;
                            likedRecipesAdapter.updateList(likedRecipesList);
                            likedRecipesAdapter.setLoading(false);
                            Toast.makeText(requireContext(), "No liked recipes found", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "No liked recipes for user");
                        });
                        return;
                    }

                    List<Integer> likedRecipeIdsInt = new ArrayList<>();
                    for (String id : likedRecipeIds) {
                        try {
                            likedRecipeIdsInt.add(Integer.parseInt(id));
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid recipe ID format: " + id, e);
                        }
                    }
                    Log.d(TAG, "Converted Liked Recipe IDs to Integers: " + likedRecipeIdsInt);

                    if (likedRecipeIdsInt.isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                Log.w(TAG, "Fragment is detached, aborting");
                                return;
                            }
                            isLoadingLiked = false;
                            allRecipesLoaded = true;
                            likedRecipesAdapter.updateList(likedRecipesList);
                            likedRecipesAdapter.setLoading(false);
                            Toast.makeText(requireContext(), "No valid liked recipes found", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "No valid integer recipe IDs");
                        });
                        return;
                    }

                    // Query only liked recipes with pagination
                    Query query = db.collection("recipes")
                            .whereIn("id", likedRecipeIdsInt)
                            .whereEqualTo("isApproved", true)
                            .limit(PAGE_SIZE);
                    if (lastLikedDoc != null) {
                        query = query.startAfter(lastLikedDoc);
                    }

                    query.get().addOnCompleteListener(task -> {
                        if (!isAdded()) {
                            Log.w(TAG, "Fragment is detached, aborting");
                            isLoadingLiked = false;
                            return;
                        }
                        isLoadingLiked = false;
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
                                    newRecipes.add(recipe);
                                    Log.d(TAG, "Added liked recipe: " + recipe.getTitle() + ", ID: " + recipe.getId());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error deserializing recipe: " + document.getId(), e);
                                }
                            }
                            Log.d(TAG, "Fetched " + newRecipes.size() + " liked recipes");
                            lastLikedDoc = task.getResult().getDocuments().isEmpty() ? null :
                                    task.getResult().getDocuments().get(task.getResult().size() - 1);

                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) {
                                    Log.w(TAG, "Fragment is detached, aborting");
                                    return;
                                }
                                if (!newRecipes.isEmpty()) {
                                    likedRecipesList.addAll(newRecipes);
                                }
                                if (newRecipes.size() < PAGE_SIZE) {
                                    allRecipesLoaded = true;
                                    Log.d(TAG, "All liked recipes loaded, stopping pagination");
                                }
                                likedRecipesAdapter.updateList(likedRecipesList);
                                likedRecipesAdapter.setLoading(false);
                                if (likedRecipesList.isEmpty()) {
                                    Log.w(TAG, "No liked recipes loaded for user " + userId);
                                    Toast.makeText(requireContext(), "No liked recipes available", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) {
                                    Log.w(TAG, "Fragment is detached, aborting");
                                    return;
                                }
                                Log.e(TAG, "Firestore query failed", task.getException());
                                likedRecipesAdapter.updateList(likedRecipesList);
                                likedRecipesAdapter.setLoading(false);
                                Toast.makeText(requireContext(), "Failed to load liked recipes", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment is detached, aborting");
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        Log.e(TAG, "Error fetching user data for user " + userId, e);
                        likedRecipesAdapter.updateList(likedRecipesList);
                        likedRecipesAdapter.setLoading(false);
                        Toast.makeText(requireContext(), "Failed to load liked recipes", Toast.LENGTH_SHORT).show();
                    });
                });
    }
}