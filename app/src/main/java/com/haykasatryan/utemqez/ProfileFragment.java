package com.haykasatryan.utemqez;

import static android.app.Activity.RESULT_OK;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileFragment extends Fragment implements ResponseCallback {

    private static final String TAG = "ProfileFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView userName;
    private ImageView profileImage;
    private Button btnAddNewRecipe;
    private ImageButton btnLogout;
    private Cloudinary cloudinary;
    private Dialog recipeDialog;
    private EditText recipeTitle, recipeTime;
    private ImageView recipeImagePreview;
    private TextView selectImageHint;
    private LinearLayout ingredientsContainer, instructionsContainer, categoriesContainer;
    private Button btnAddIngredient, btnAddInstruction, btnAddCategory, btnPostRecipe, btnGetNutritionFromAI;
    private EditText nutritionCalories, nutritionProtein, nutritionFat, nutritionCarbs;
    private String recipeImageUrl = "";
    private RecyclerView userRecipesRecyclerView;
    private RecipeAdapter userRecipesAdapter;
    private final List<Recipe> userRecipesList = new ArrayList<>();
    private ChatFutures chatModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "deor2c9as",
                "api_key", "882433745744291",
                "api_secret", "tPtmtB9HaZ7pTv6dn7lEJJssOh0"
        ));
        GeminiPro geminiPro = new GeminiPro();
        chatModel = geminiPro.getModel().startChat();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        userName = view.findViewById(R.id.profileName);
        profileImage = view.findViewById(R.id.profilePicture);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnAddNewRecipe = view.findViewById(R.id.btnAddNewRecipe);
        userRecipesRecyclerView = view.findViewById(R.id.userRecipesRecyclerView);
        Button btnAdminDashboard = view.findViewById(R.id.btnAdminDashboard);

        if (userName == null || profileImage == null || btnLogout == null || btnAddNewRecipe == null || userRecipesRecyclerView == null || btnAdminDashboard == null) {
            Log.e(TAG, "One or more views in fragment_profile not found");
            Toast.makeText(requireContext(), "Error loading profile layout", Toast.LENGTH_SHORT).show();
            return view;
        }

        userRecipesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        userRecipesAdapter = new RecipeAdapter(userRecipesList, R.layout.recipe_item_search);
        userRecipesRecyclerView.setAdapter(userRecipesAdapter);

        userRecipesAdapter.setOnDeleteClickListener(this::showDeleteConfirmationDialog);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Log.d(TAG, "Current user UID: " + user.getUid());
            userName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
            initializeUserDocument(user);
            loadProfilePicture(user.getEmail());
            fetchUserRecipes(user.getUid());
            debugAllRecipes();

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!isAdded()) return; // Check if fragment is attached
                        Boolean isAdmin = documentSnapshot.getBoolean("isAdmin");
                        btnAdminDashboard.setVisibility((isAdmin != null && isAdmin) ? View.VISIBLE : View.GONE);
                        Log.d(TAG, "Admin status checked: isAdmin=" + isAdmin);
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return; // Check if fragment is attached
                        Log.e(TAG, "Error checking admin status: " + e.getMessage(), e);
                    });
        } else {
            Log.w(TAG, "No user logged in");
            Navigation.findNavController(view).navigate(R.id.action_profileFragment_to_loginActivity);
        }

        profileImage.setOnClickListener(v -> pickImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
        btnAddNewRecipe.setOnClickListener(v -> showRecipeFormDialog());
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_loginActivity);
        });
        btnAdminDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminDashboardActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private void debugAllRecipes() {
        Log.d(TAG, "Debugging all recipes in Firestore");
        db.collection("recipes").get().addOnCompleteListener(task -> {
            if (!isAdded()) return; // Check if fragment is attached
            if (task.isSuccessful()) {
                Log.d(TAG, "Total recipes in collection: " + task.getResult().size());
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Log.d(TAG, "Recipe: " + doc.getId() + ", userId: " + doc.getString("userId") + ", title: " + doc.getString("title") + ", isApproved: " + doc.getBoolean("isApproved"));
                }
            } else {
                Log.e(TAG, "Error debugging all recipes: " + task.getException().getMessage(), task.getException());
            }
        });
    }

    private void initializeUserDocument(FirebaseUser user) {
        String userId = user.getUid();

        Map<String, Object> userData = new HashMap<>();
        if (user.getEmail() != null) {
            userData.put("email", user.getEmail());
        }
        if (user.getDisplayName() != null) {
            userData.put("displayName", user.getDisplayName());
        }

        db.collection("users").document(userId)
                .set(userData, SetOptions.mergeFields("email", "displayName"))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document initialized for: " + userId))
                .addOnFailureListener(e -> {
                    if (!isAdded()) return; // Check if fragment is attached
                    Log.e(TAG, "Failed to initialize user document: " + e.getMessage(), e);
                });
    }

    private void fetchUserRecipes(String userId) {
        Log.d(TAG, "Fetching recipes for userId: " + userId);
        db.collection("recipes")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment not attached, skipping fetchUserRecipes callback for userId: " + userId);
                        return; // Exit early if fragment is not attached
                    }
                    if (task.isSuccessful()) {
                        userRecipesList.clear();
                        int recipeCount = 0;
                        Log.d(TAG, "Query returned " + task.getResult().size() + " documents");
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Log.d(TAG, "Document ID: " + document.getId() + ", Data: " + document.getData());

                                if (document.getString("userId") == null || !document.getString("userId").equals(userId)) {
                                    Log.w(TAG, "Skipping document " + document.getId() + ": userId mismatch or null");
                                    continue;
                                }
                                Long id = document.getLong("id");
                                if (id == null) {
                                    Log.e(TAG, "Skipping document " + document.getId() + ": 'id' field missing or not a long");
                                    continue;
                                }

                                Recipe recipe = new Recipe();
                                recipe.setId(id.intValue());
                                recipe.setTitle(document.getString("title") != null ? document.getString("title") : "Untitled");
                                recipe.setReadyInMinutes(document.getLong("readyInMinutes") != null ? document.getLong("readyInMinutes").intValue() : 0);
                                recipe.setSourceUrl(document.getString("sourceUrl") != null ? document.getString("sourceUrl") : "");
                                recipe.setInstructions(document.getString("instructions") != null ? document.getString("instructions") : "");
                                recipe.setImageUrl(document.getString("imageUrl") != null ? document.getString("imageUrl") : "");
                                recipe.setCategory((List<String>) document.get("category") != null ? (List<String>) document.get("category") : new ArrayList<>());
                                recipe.setUserId(document.getString("userId") != null ? document.getString("userId") : "");
                                recipe.setApproved(document.getBoolean("isApproved") != null ? document.getBoolean("isApproved") : false);
                                recipe.setLikes(document.getLong("likes") != null ? document.getLong("likes").intValue() : 0);

                                List<Map<String, String>> rawIngredients = (List<Map<String, String>>) document.get("ingredients");
                                List<Ingredient> ingredients = new ArrayList<>();
                                if (rawIngredients != null) {
                                    for (Map<String, String> raw : rawIngredients) {
                                        Ingredient ingredient = new Ingredient();
                                        ingredient.setAmount(raw.get("amount") != null ? raw.get("amount") : "");
                                        ingredient.setName(raw.get("name") != null ? raw.get("name") : "");
                                        ingredients.add(ingredient);
                                    }
                                }
                                recipe.setIngredients(ingredients);

                                Map<String, String> rawNutrition = (Map<String, String>) document.get("nutrition");
                                Nutrition nutrition = new Nutrition();
                                if (rawNutrition != null) {
                                    nutrition.setCalories(rawNutrition.get("calories") != null ? rawNutrition.get("calories") : "");
                                    nutrition.setProtein(rawNutrition.get("protein") != null ? rawNutrition.get("protein") : "");
                                    nutrition.setFat(rawNutrition.get("fat") != null ? rawNutrition.get("fat") : "");
                                    nutrition.setCarbs(rawNutrition.get("carbs") != null ? rawNutrition.get("carbs") : "");
                                }
                                recipe.setNutrition(nutrition);

                                userRecipesList.add(recipe);
                                recipeCount++;
                                Log.d(TAG, "Added recipe: " + document.getId() + ", title: " + recipe.getTitle() + ", isApproved: " + recipe.isApproved());
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing recipe: " + document.getId() + ", error: " + e.getMessage(), e);
                            }
                        }
                        int finalRecipeCount = recipeCount;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                Log.w(TAG, "Fragment not attached, skipping UI update for user recipes");
                                return;
                            }
                            userRecipesAdapter.updateList(userRecipesList);
                            userRecipesRecyclerView.setVisibility(userRecipesList.isEmpty() ? View.GONE : View.VISIBLE);
                            TextView noRecipesText = requireView().findViewById(R.id.noRecipesText);
                            noRecipesText.setVisibility(userRecipesList.isEmpty() ? View.VISIBLE : View.GONE);
                            userRecipesRecyclerView.scheduleLayoutAnimation();
                            userRecipesRecyclerView.invalidate();
                            Log.d(TAG, "Fetched " + finalRecipeCount + " user recipes for userId: " + userId);
                            if (userRecipesList.isEmpty()) {
                                Log.w(TAG, "No recipes added to userRecipesList. Check Firestore data, security rules, or parsing logic.");
                            }
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                Log.w(TAG, "Fragment not attached, skipping error UI update for user recipes");
                                return;
                            }
                            TextView noRecipesText = requireView().findViewById(R.id.noRecipesText);
                            noRecipesText.setVisibility(View.VISIBLE);
                            userRecipesRecyclerView.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), "Failed to get recipes: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        });
                        Log.e(TAG, "Error fetching user recipes: " + task.getException().getMessage(), task.getException());
                    }
                });
    }

    private void showDeleteConfirmationDialog(Recipe recipe) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Delete Recipe")
                .setMessage("Are you sure you want to delete \"" + recipe.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialogInterface, which) -> deleteRecipe(recipe))
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((AlertDialog) dialogInterface).getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = ((AlertDialog) dialogInterface).getButton(AlertDialog.BUTTON_NEGATIVE);

            positiveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            negativeButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));

            positiveButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
            negativeButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
        });

        dialog.show();
    }

    private void deleteRecipe(Recipe recipe) {
        db.collection("recipes")
                .document(String.valueOf(recipe.getId()))
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment not attached, skipping UI update for recipe deletion: " + recipe.getId());
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Recipe deleted successfully", Toast.LENGTH_SHORT).show();
                        fetchUserRecipes(Objects.requireNonNull(mAuth.getCurrentUser()).getUid());
                    });
                    Log.d(TAG, "Recipe deleted: " + recipe.getId());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment not attached, skipping error UI update for recipe deletion: " + recipe.getId());
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to delete recipe: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    Log.e(TAG, "Error deleting recipe: " + e.getMessage(), e);
                });
    }

    private void showRecipeFormDialog() {
        recipeDialog = new Dialog(requireContext());
        try {
            recipeDialog.setContentView(R.layout.layout_recipe_form_popup);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout_recipe_form_popup: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error loading recipe form", Toast.LENGTH_SHORT).show();
            return;
        }

        recipeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams params = recipeDialog.getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int maxHeight = (int) (displayMetrics.heightPixels * 0.8);
        params.height = maxHeight;
        recipeDialog.getWindow().setAttributes(params);
        recipeDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        View contentView = recipeDialog.findViewById(R.id.recipeForm);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                contentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                LinearLayout recipeContainer = contentView.findViewById(R.id.recipeContainer);
                int dialogHeight = contentView.getHeight();
                int contentHeight = recipeContainer.getHeight();
                Log.d(TAG, "Dialog height: " + dialogHeight + "px, Max height: " + maxHeight + "px, Content height: " + contentHeight + "px");
                if (contentHeight > dialogHeight) {
                    Log.w(TAG, "Content height exceeds dialog height; ensure ScrollView is scrollable");
                }
            }
        });

        recipeTitle = recipeDialog.findViewById(R.id.recipeTitle);
        recipeTime = recipeDialog.findViewById(R.id.recipeTime);
        recipeImagePreview = recipeDialog.findViewById(R.id.recipeImagePreview);
        selectImageHint = recipeDialog.findViewById(R.id.selectImageHint);
        ingredientsContainer = recipeDialog.findViewById(R.id.ingredientsContainer);
        btnAddIngredient = recipeDialog.findViewById(R.id.btnAddIngredient);
        instructionsContainer = recipeDialog.findViewById(R.id.instructionsContainer);
        btnAddInstruction = recipeDialog.findViewById(R.id.btnAddInstruction);
        nutritionCalories = recipeDialog.findViewById(R.id.nutritionCalories);
        nutritionProtein = recipeDialog.findViewById(R.id.nutritionProtein);
        nutritionFat = recipeDialog.findViewById(R.id.nutritionFat);
        nutritionCarbs = recipeDialog.findViewById(R.id.nutritionCarbs);
        categoriesContainer = recipeDialog.findViewById(R.id.categoriesContainer);
        btnAddCategory = recipeDialog.findViewById(R.id.btnAddCategory);
        btnPostRecipe = recipeDialog.findViewById(R.id.btnPostRecipe);
        btnGetNutritionFromAI = recipeDialog.findViewById(R.id.btnGetNutritionFromAI);

        if (recipeTitle == null || recipeTime == null || recipeImagePreview == null || selectImageHint == null ||
                ingredientsContainer == null || btnAddIngredient == null ||
                instructionsContainer == null || btnAddInstruction == null ||
                nutritionCalories == null || nutritionProtein == null ||
                nutritionFat == null || nutritionCarbs == null ||
                categoriesContainer == null || btnAddCategory == null ||
                btnPostRecipe == null || btnGetNutritionFromAI == null) {
            Log.e(TAG, "One or more views in recipe form dialog not found");
            Toast.makeText(requireContext(), "Error initializing recipe form", Toast.LENGTH_SHORT).show();
            recipeDialog.dismiss();
            return;
        }

        recipeImagePreview.setOnClickListener(v -> {
            recipeImagePreview.setImageResource(R.drawable.placeholder_recipe);
            selectImageHint.setVisibility(View.VISIBLE);
            pickRecipeImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
        });
        btnAddIngredient.setOnClickListener(v -> addIngredientField());
        btnAddInstruction.setOnClickListener(v -> addInstructionField());
        btnAddCategory.setOnClickListener(v -> addCategoryField());
        btnPostRecipe.setOnClickListener(v -> {
            if (validateRecipeForm()) {
                postRecipe();
                recipeDialog.dismiss();
            }
        });
        btnGetNutritionFromAI.setOnClickListener(v -> {
            if (validateRecipeFormForAI()) {
                requestNutritionFromAI();
            } else {
                Toast toast = Toast.makeText(requireContext(), "Please fill all recipe information", Toast.LENGTH_SHORT);
                TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                if (toastTextView != null) {
                    toastTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
                }
                toast.show();
            }
        });

        addIngredientField();
        addInstructionField();
        addCategoryField();

        recipeDialog.setCanceledOnTouchOutside(true);
        recipeDialog.show();
        Log.d(TAG, "Recipe form dialog shown");
    }

    private boolean validateRecipeForm() {
        if (recipeTitle.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a recipe title", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!recipeTime.getText().toString().trim().isEmpty()) {
            try {
                Integer.parseInt(recipeTime.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Time must be a number", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private boolean validateRecipeFormForAI() {
        if (recipeTitle.getText().toString().trim().isEmpty()) {
            return false;
        }
        if (recipeTime.getText().toString().trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(recipeTime.getText().toString().trim());
        } catch (NumberFormatException e) {
            return false;
        }
        boolean hasIngredients = false;
        for (int i = 0; i < ingredientsContainer.getChildCount(); i++) {
            LinearLayout layout = (LinearLayout) ingredientsContainer.getChildAt(i);
            EditText name = (EditText) layout.getChildAt(0);
            EditText amount = (EditText) layout.getChildAt(1);
            if (!name.getText().toString().trim().isEmpty() && !amount.getText().toString().trim().isEmpty()) {
                hasIngredients = true;
                break;
            }
        }
        if (!hasIngredients) {
            return false;
        }
        boolean hasInstructions = false;
        for (int i = 0; i < instructionsContainer.getChildCount(); i++) {
            EditText instruction = (EditText) instructionsContainer.getChildAt(i);
            if (!instruction.getText().toString().trim().isEmpty()) {
                hasInstructions = true;
                break;
            }
        }
        if (!hasInstructions) {
            return false;
        }
        boolean hasCategories = false;
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            EditText category = (EditText) categoriesContainer.getChildAt(i);
            if (!category.getText().toString().trim().isEmpty()) {
                hasCategories = true;
                break;
            }
        }
        return hasCategories;
    }

    private void requestNutritionFromAI() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Calculate the nutritional values for the following recipe. Provide only numerical values (no units) for Calories, Protein, Fat, and Carbs. Format the response as: 'Calories: <number>, Protein: <number>, Fat: <number>, Carbs: <number>'.\n\n");
        prompt.append("Recipe Title: ").append(recipeTitle.getText().toString().trim()).append("\n");
        prompt.append("Preparation Time: ").append(recipeTime.getText().toString().trim()).append(" minutes\n");

        prompt.append("Ingredients:\n");
        for (int i = 0; i < ingredientsContainer.getChildCount(); i++) {
            LinearLayout layout = (LinearLayout) ingredientsContainer.getChildAt(i);
            EditText name = (EditText) layout.getChildAt(0);
            EditText amount = (EditText) layout.getChildAt(1);
            if (!name.getText().toString().trim().isEmpty() && !amount.getText().toString().trim().isEmpty()) {
                prompt.append("- ").append(amount.getText().toString().trim()).append(" ").append(name.getText().toString().trim()).append("\n");
            }
        }

        prompt.append("Instructions:\n");
        for (int i = 0; i < instructionsContainer.getChildCount(); i++) {
            EditText instruction = (EditText) instructionsContainer.getChildAt(i);
            String instructionText = instruction.getText().toString().trim();
            if (!instructionText.isEmpty()) {
                prompt.append((i + 1)).append(". ").append(instructionText).append("\n");
            }
        }

        prompt.append("Categories:\n");
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            EditText category = (EditText) categoriesContainer.getChildAt(i);
            if (!category.getText().toString().trim().isEmpty()) {
                prompt.append("- ").append(category.getText().toString().trim()).append("\n");
            }
        }

        Log.d(TAG, "Sending AI prompt: " + prompt.toString());
        GeminiPro.getResponse(chatModel, prompt.toString(), this);
    }

    @Override
    public void onResponse(String response) {
        if (!isAdded()) {
            Log.w(TAG, "Fragment not attached, skipping AI response handling");
            return;
        }
        requireActivity().runOnUiThread(() -> {
            Log.d(TAG, "AI response: " + response);
            Pattern pattern = Pattern.compile("Calories: (\\d+), Protein: (\\d+\\.?\\d*), Fat: (\\d+\\.?\\d*), Carbs: (\\d+\\.?\\d*)");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                nutritionCalories.setText(matcher.group(1));
                nutritionProtein.setText(matcher.group(2));
                nutritionFat.setText(matcher.group(3));
                nutritionCarbs.setText(matcher.group(4));
                Toast.makeText(requireContext(), "Nutrition values updated from AI", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Failed to parse AI nutrition values", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid AI response format: " + response);
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
        if (!isAdded()) {
            Log.w(TAG, "Fragment not attached, skipping AI error handling");
            return;
        }
        requireActivity().runOnUiThread(() -> {
            Log.e(TAG, "AI error: " + throwable.getMessage(), throwable);
            Toast.makeText(requireContext(), "Failed to get nutrition values: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void addIngredientField() {
        LinearLayout ingredientLayout = new LinearLayout(requireContext());
        ingredientLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()), 0, 0);
        ingredientLayout.setLayoutParams(layoutParams);

        EditText name = new EditText(requireContext());
        name.setHint("Ingredient Name");
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMargins(0, 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()), 0);
        name.setLayoutParams(nameParams);
        name.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.edit_text_background));
        name.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())
        );
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        EditText amount = new EditText(requireContext());
        amount.setHint("Amount");
        amount.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        amount.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.edit_text_background));
        amount.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())
        );
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        ingredientLayout.addView(name);
        ingredientLayout.addView(amount);
        ingredientsContainer.addView(ingredientLayout);
    }

    private void addInstructionField() {
        EditText instruction = new EditText(requireContext());
        instruction.setHint("Instruction Step");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()), 0, 0);
        instruction.setLayoutParams(params);
        instruction.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.edit_text_background));
        instruction.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())
        );
        instruction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        instructionsContainer.addView(instruction);
    }

    private void addCategoryField() {
        EditText category = new EditText(requireContext());
        category.setHint("Category");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()), 0, 0);
        category.setLayoutParams(params);
        category.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.edit_text_background));
        category.setPadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())
        );
        category.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        categoriesContainer.addView(category);
    }

    private void postRecipe() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Please log in to post a recipe", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No user logged in for posting recipe");
            return;
        }

        int recipeId = new Random().nextInt(900000) + 100000;

        List<Map<String, String>> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientsContainer.getChildCount(); i++) {
            LinearLayout layout = (LinearLayout) ingredientsContainer.getChildAt(i);
            EditText name = (EditText) layout.getChildAt(0);
            EditText amount = (EditText) layout.getChildAt(1);
            if (!amount.getText().toString().isEmpty() && !name.getText().toString().isEmpty()) {
                Map<String, String> ingredient = new HashMap<>();
                ingredient.put("amount", amount.getText().toString());
                ingredient.put("name", name.getText().toString());
                ingredients.add(ingredient);
            }
        }

        List<String> instructionList = new ArrayList<>();
        for (int i = 0; i < instructionsContainer.getChildCount(); i++) {
            EditText instruction = (EditText) instructionsContainer.getChildAt(i);
            String instructionText = instruction.getText().toString().trim();
            if (!instructionText.isEmpty()) {
                instructionList.add(instructionText);
            }
        }

        String instructions;
        if (instructionList.isEmpty()) {
            instructions = "No instructions provided";
        } else {
            StringBuilder instructionsBuilder = new StringBuilder();
            for (int i = 0; i < instructionList.size(); i++) {
                instructionsBuilder.append(i + 1).append(". ").append(instructionList.get(i));
                if (i < instructionList.size() - 1) {
                    instructionsBuilder.append("\n");
                }
            }
            instructions = instructionsBuilder.toString();
        }

        Map<String, String> nutrition = new HashMap<>();
        nutrition.put("calories", nutritionCalories.getText().toString().isEmpty() ? "0 Calories" : nutritionCalories.getText().toString() + " Calories");
        nutrition.put("protein", nutritionProtein.getText().toString().isEmpty() ? "0g Protein" : nutritionProtein.getText().toString() + "g Protein");
        nutrition.put("fat", nutritionFat.getText().toString().isEmpty() ? "0g Fat" : nutritionFat.getText().toString() + "g Fat");
        nutrition.put("carbs", nutritionCarbs.getText().toString().isEmpty() ? "0g Carbs" : nutritionCarbs.getText().toString() + "g Carbs");

        List<String> categories = new ArrayList<>();
        for (int i = 0; i < categoriesContainer.getChildCount(); i++) {
            EditText category = (EditText) categoriesContainer.getChildAt(i);
            if (!category.getText().toString().isEmpty()) {
                categories.add(category.getText().toString());
            }
        }

        Map<String, Object> recipe = new HashMap<>();
        recipe.put("id", recipeId);
        recipe.put("title", recipeTitle.getText().toString());
        recipe.put("readyInMinutes", recipeTime.getText().toString().isEmpty() ? 0 : Integer.parseInt(recipeTime.getText().toString()));
        recipe.put("sourceUrl", "");
        recipe.put("ingredients", ingredients);
        recipe.put("instructions", instructions);
        recipe.put("nutrition", nutrition);
        recipe.put("imageUrl", recipeImageUrl);
        recipe.put("category", categories);
        recipe.put("userId", user.getUid());
        recipe.put("isApproved", false);
        recipe.put("likes", 0);

        db.collection("recipes")
                .document(String.valueOf(recipeId))
                .set(recipe)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment not attached, skipping UI update for recipe post: " + recipeId);
                        return;
                    }
                    Toast.makeText(requireContext(), "Recipe posted successfully, pending approval", Toast.LENGTH_SHORT).show();
                    clearForm();
                    fetchUserRecipes(user.getUid());
                    Log.d(TAG, "Recipe posted: " + recipeId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment not attached, skipping error UI update for recipe post: " + recipeId);
                        return;
                    }
                    Log.e(TAG, "Error posting recipe: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Failed to post recipe", Toast.LENGTH_SHORT).show();
                });
    }

    private void clearForm() {
        recipeTitle.setText("");
        recipeTime.setText("");
        recipeImagePreview.setImageResource(R.drawable.placeholder_recipe);
        selectImageHint.setVisibility(View.VISIBLE);
        recipeImageUrl = "";
        ingredientsContainer.removeAllViews();
        instructionsContainer.removeAllViews();
        nutritionCalories.setText("");
        nutritionProtein.setText("");
        nutritionFat.setText("");
        nutritionCarbs.setText("");
        categoriesContainer.removeAllViews();
        addIngredientField();
        addInstructionField();
        addCategoryField();
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (!isAdded()) {
                    Log.w(TAG, "Fragment not attached, skipping profile image selection");
                    return;
                }
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    Log.d(TAG, "Profile image selected: " + imageUri);
                    try {
                        InputStream inputStream = requireActivity().getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap != null) {
                            profileImage.setImageBitmap(bitmap);
                            uploadImageToCloudinary(bitmap, true);
                        } else {
                            Log.e(TAG, "Failed to decode bitmap");
                        }
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "File not found", e);
                    }
                } else {
                    Log.d(TAG, "Profile image selection cancelled or failed");
                }
            }
    );

    private final ActivityResultLauncher<Intent> pickRecipeImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (!isAdded()) {
                    Log.w(TAG, "Fragment not attached, skipping recipe image selection");
                    return;
                }
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    Log.d(TAG, "Recipe image selected: " + imageUri);
                    try {
                        InputStream inputStream = requireActivity().getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap != null) {
                            Glide.with(requireContext())
                                    .load(bitmap)
                                    .apply(new RequestOptions()
                                            .transform(new RoundedCorners(22))
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .placeholder(R.drawable.placeholder_recipe)
                                            .error(R.drawable.placeholder_recipe))
                                    .into(recipeImagePreview);
                            selectImageHint.setVisibility(View.GONE);
                            uploadImageToCloudinary(bitmap, false);
                        } else {
                            Log.e(TAG, "Failed to decode bitmap");
                            recipeImagePreview.setImageResource(R.drawable.placeholder_recipe);
                            selectImageHint.setVisibility(View.VISIBLE);
                        }
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "File not found", e);
                        recipeImagePreview.setImageResource(R.drawable.placeholder_recipe);
                        selectImageHint.setVisibility(View.VISIBLE);
                    }
                } else {
                    Log.d(TAG, "Recipe image selection cancelled or failed");
                    recipeImagePreview.setImageResource(R.drawable.placeholder_recipe);
                    selectImageHint.setVisibility(View.VISIBLE);
                }
            }
    );

    private void uploadImageToCloudinary(Bitmap bitmap, boolean isProfilePicture) {
        new Thread(() -> {
            try {
                File file = convertBitmapToFile(bitmap);
                if (file == null) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Error preparing image for upload", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                Map uploadResult = cloudinary.uploader().upload(file, ObjectUtils.emptyMap());
                String imageUrl = (String) uploadResult.get("url");
                if (imageUrl != null) {
                    imageUrl = imageUrl.replace("http://", "https://");
                    if (isProfilePicture) {
                        saveProfilePictureToFirestore(imageUrl);
                        String finalImageUrl = imageUrl;
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Glide.with(requireContext())
                                    .load(finalImageUrl)
                                    .apply(new RequestOptions()
                                            .circleCrop()
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .placeholder(R.drawable.user)
                                            .error(R.drawable.user))
                                    .into(profileImage);
                        });
                    } else {
                        recipeImageUrl = imageUrl;
                        String finalImageUrl1 = imageUrl;
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Glide.with(requireContext())
                                    .load(finalImageUrl1)
                                    .apply(new RequestOptions()
                                            .transform(new RoundedCorners(22))
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .placeholder(R.drawable.placeholder_recipe)
                                            .error(R.drawable.placeholder_recipe))
                                    .into(recipeImagePreview);
                            selectImageHint.setVisibility(View.GONE);
                        });
                    }
                    Log.d(TAG, "Image uploaded: " + imageUrl);
                } else {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Image upload returned no URL", Toast.LENGTH_SHORT).show();
                    });
                    Log.e(TAG, "No URL returned from Cloudinary");
                }
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Failed to upload image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                Log.e(TAG, "Upload error", e);
            }
        }).start();
    }

    private File convertBitmapToFile(Bitmap bitmap) {
        File file = new File(requireContext().getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            Log.d(TAG, "File created at: " + file.getAbsolutePath() + ", size: " + file.length());
            return file;
        } catch (Exception e) {
            if (!isAdded()) return null;
            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Error converting image: " + e.getMessage(), Toast.LENGTH_LONG).show());
            Log.e(TAG, "Error converting image", e);
            return null;
        }
    }

    private void saveProfilePictureToFirestore(String imageUrl) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            if (!isAdded()) return;
            Log.w(TAG, "No user logged in for saving profile picture");
            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "No user logged in", Toast.LENGTH_SHORT).show());
            return;
        }

        String userId = user.getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("profilePicture", imageUrl);
        updates.put("email", user.getEmail());
        updates.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "User");

        db.collection("users").document(userId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Profile picture saved successfully", Toast.LENGTH_SHORT).show();
                        loadProfilePicture(user.getEmail());
                    });
                    Log.d(TAG, "Profile picture saved for user: " + userId + ", URL: " + imageUrl);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to save profile picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                    Log.e(TAG, "Failed to save profile picture for user: " + userId + ", error: " + e.getMessage(), e);
                });
    }

    private void loadProfilePicture(String email) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String imageUrl = queryDocumentSnapshots.getDocuments().get(0).getString("profilePicture");
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            imageUrl = imageUrl.replace("http://", "https://");
                            Glide.with(requireContext())
                                    .load(imageUrl)
                                    .apply(new RequestOptions()
                                            .circleCrop()
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .placeholder(R.drawable.user)
                                            .error(R.drawable.user))
                                    .into(profileImage);
                            Log.d(TAG, "Profile picture loaded: " + imageUrl);
                        } else {
                            profileImage.setImageResource(R.drawable.user);
                            Log.d(TAG, "No profile picture found for email: " + email);
                        }
                    } else {
                        profileImage.setImageResource(R.drawable.user);
                        Log.w(TAG, "No user document found for email: " + email);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error loading profile picture: " + e.getMessage(), e);
                    profileImage.setImageResource(R.drawable.user);
                });
    }
}