package com.haykasatryan.utemqez;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
import java.util.Random;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView userName, userEmail;
    private ImageView profileImage;
    private Button btnLogout, btnAddNewRecipe;
    private Cloudinary cloudinary;
    private Dialog recipeDialog;
    private Button btnSelectImage, btnAddIngredient, btnAddInstruction, btnAddCategory, btnPostRecipe;
    private EditText recipeTitle, recipeTime;
    private ImageView imageStatusIcon; // Changed from TextView to ImageView
    private LinearLayout ingredientsContainer, instructionsContainer, categoriesContainer;
    private EditText nutritionCalories, nutritionProtein, nutritionFat, nutritionCarbs;
    private String recipeImageUrl = "";

    private RecyclerView userRecipesRecyclerView;
    private RecipeAdapter userRecipesAdapter;
    private final List<Recipe> userRecipesList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        userName = findViewById(R.id.profileName);
        userEmail = findViewById(R.id.profileEmail);
        profileImage = findViewById(R.id.profilePicture);
        btnLogout = findViewById(R.id.btnLogout);
        btnAddNewRecipe = findViewById(R.id.btnAddNewRecipe);
        userRecipesRecyclerView = findViewById(R.id.userRecipesRecyclerView);
        Button btnAdminDashboard = findViewById(R.id.btnAdminDashboard);

        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "deor2c9as",
                "api_key", "882433745744291",
                "api_secret", "tPtmtB9HaZ7pTv6dn7lEJJssOh0"
        ));

        userRecipesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        userRecipesAdapter = new RecipeAdapter(userRecipesList, R.layout.recipe_item_search);
        userRecipesRecyclerView.setAdapter(userRecipesAdapter);

        userRecipesAdapter.setOnDeleteClickListener(this::showDeleteConfirmationDialog);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
            userEmail.setText(user.getEmail());
            initializeUserDocument(user);
            loadProfilePicture(user.getEmail());
            fetchUserRecipes(user.getUid());

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Boolean isAdmin = documentSnapshot.getBoolean("isAdmin");
                        btnAdminDashboard.setVisibility((isAdmin != null && isAdmin) ? View.VISIBLE : View.GONE);
                        Log.d(TAG, "Admin status checked: isAdmin=" + isAdmin);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error checking admin status: " + e.getMessage(), e));
        } else {
            Log.w(TAG, "No user logged in");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }

        profileImage.setOnClickListener(v -> pickImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
        btnAddNewRecipe.setOnClickListener(v -> showRecipeFormDialog());
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        });
        btnAdminDashboard.setOnClickListener(v -> startActivity(new Intent(this, AdminDashboardActivity.class)));
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
                .addOnFailureListener(e -> Log.e(TAG, "Failed to initialize user document: " + e.getMessage(), e));
    }

    private void fetchUserRecipes(String userId) {
        db.collection("recipes")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        userRecipesList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Recipe recipe = new Recipe();
                                recipe.setId(document.getLong("id").intValue());
                                recipe.setTitle(document.getString("title"));
                                recipe.setReadyInMinutes(document.getLong("readyInMinutes").intValue());
                                recipe.setSourceUrl(document.getString("sourceUrl"));
                                recipe.setInstructions(document.getString("instructions"));
                                recipe.setImageUrl(document.getString("imageUrl"));
                                recipe.setCategory((List<String>) document.get("category"));
                                recipe.setUserId(document.getString("userId"));

                                List<Map<String, String>> rawIngredients = (List<Map<String, String>>) document.get("ingredients");
                                List<Ingredient> ingredients = new ArrayList<>();
                                if (rawIngredients != null) {
                                    for (Map<String, String> raw : rawIngredients) {
                                        Ingredient ingredient = new Ingredient();
                                        ingredient.setAmount(raw.get("amount"));
                                        ingredient.setName(raw.get("name"));
                                        ingredients.add(ingredient);
                                    }
                                }
                                recipe.setIngredients(ingredients);

                                Map<String, String> rawNutrition = (Map<String, String>) document.get("nutrition");
                                Nutrition nutrition = new Nutrition();
                                if (rawNutrition != null) {
                                    nutrition.setCalories(rawNutrition.get("calories"));
                                    nutrition.setProtein(rawNutrition.get("protein"));
                                    nutrition.setFat(rawNutrition.get("fat"));
                                    nutrition.setCarbs(rawNutrition.get("carbs"));
                                }
                                recipe.setNutrition(nutrition);

                                userRecipesList.add(recipe);
                            } catch (Exception e) {
                                runOnUiThread(() -> Toast.makeText(this, "Error loading recipe: " + document.getId(), Toast.LENGTH_SHORT).show());
                                Log.e(TAG, "Error parsing recipe: " + document.getId(), e);
                            }
                        }
                        runOnUiThread(() -> {
                            userRecipesAdapter.notifyDataSetChanged();
                            TextView noRecipesText = findViewById(R.id.noRecipesText);
                            noRecipesText.setVisibility(userRecipesList.isEmpty() ? View.VISIBLE : View.GONE);
                            userRecipesRecyclerView.setVisibility(userRecipesList.isEmpty() ? View.GONE : View.VISIBLE);
                            Log.d(TAG, "Fetched " + userRecipesList.size() + " user recipes");
                        });
                    } else {
                        runOnUiThread(() -> {
                            TextView noRecipesText = findViewById(R.id.noRecipesText);
                            noRecipesText.setVisibility(View.VISIBLE);
                            userRecipesRecyclerView.setVisibility(View.GONE);
                        });
                        Log.e(TAG, "Error fetching user recipes", task.getException());
                    }
                });
    }

    private void showDeleteConfirmationDialog(Recipe recipe) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete Recipe")
                .setMessage("Are you sure you want to delete \"" + recipe.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialogInterface, which) -> deleteRecipe(recipe))
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((AlertDialog) dialogInterface).getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = ((AlertDialog) dialogInterface).getButton(AlertDialog.BUTTON_NEGATIVE);

            // Set text colors
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.black));
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.black));

            // Optionally set background colors
            positiveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
            negativeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
        });

        dialog.show();
    }

    private void deleteRecipe(Recipe recipe) {
        db.collection("recipes")
                .document(String.valueOf(recipe.getId()))
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recipe deleted successfully", Toast.LENGTH_SHORT).show();
                    fetchUserRecipes(mAuth.getCurrentUser().getUid());
                    Log.d(TAG, "Recipe deleted: " + recipe.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting recipe: " + e.getMessage(), e);
                });
    }

    private void showRecipeFormDialog() {
        recipeDialog = new Dialog(this);
        recipeDialog.setContentView(R.layout.layout_recipe_form_popup);
        recipeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        recipeDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        recipeTitle = recipeDialog.findViewById(R.id.recipeTitle);
        recipeTime = recipeDialog.findViewById(R.id.recipeTime);
        btnSelectImage = recipeDialog.findViewById(R.id.btnSelectImage);
        imageStatusIcon = recipeDialog.findViewById(R.id.imageStatusIcon); // Initialize ImageView
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

        TextView btnClosePopup = recipeDialog.findViewById(R.id.btnClosePopup);
        btnClosePopup.setOnClickListener(v -> recipeDialog.dismiss());

        btnSelectImage.setOnClickListener(v -> {
            imageStatusIcon.setImageResource(android.R.drawable.stat_sys_download); // Show download icon
            imageStatusIcon.setVisibility(View.VISIBLE);
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

        addIngredientField();
        addInstructionField();
        addCategoryField();

        recipeDialog.setCanceledOnTouchOutside(true);
        recipeDialog.show();
        Log.d(TAG, "Recipe form dialog shown");
    }

    private boolean validateRecipeForm() {
        if (recipeTitle.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter a recipe title", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!recipeTime.getText().toString().trim().isEmpty()) {
            try {
                Integer.parseInt(recipeTime.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Time must be a number", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void addIngredientField() {
        LinearLayout ingredientLayout = new LinearLayout(this);
        ingredientLayout.setOrientation(LinearLayout.HORIZONTAL);
        ingredientLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText amount = new EditText(this);
        amount.setHint("Amount");
        amount.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        EditText name = new EditText(this);
        name.setHint("Ingredient Name");
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ingredientLayout.addView(amount);
        ingredientLayout.addView(name);
        ingredientsContainer.addView(ingredientLayout);
    }

    private void addInstructionField() {
        EditText instruction = new EditText(this);
        instruction.setHint("Instruction Step");
        instruction.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        instructionsContainer.addView(instruction);
    }

    private void addCategoryField() {
        EditText category = new EditText(this);
        category.setHint("Category");
        category.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        categoriesContainer.addView(category);
    }

    private void postRecipe() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in to post a recipe", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No user logged in for posting recipe");
            return;
        }

        int recipeId = new Random().nextInt(900000) + 100000;

        List<Map<String, String>> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientsContainer.getChildCount(); i++) {
            LinearLayout layout = (LinearLayout) ingredientsContainer.getChildAt(i);
            EditText amount = (EditText) layout.getChildAt(0);
            EditText name = (EditText) layout.getChildAt(1);
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

        String instructions = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            instructions = instructionList.isEmpty() ? "No instructions found" :
                    String.join(" ", instructionList.stream().map(s -> (instructionList.indexOf(s) + 1) + ". " + s).toList());
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

        db.collection("recipes")
                .document(String.valueOf(recipeId))
                .set(recipe)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recipe posted successfully, pending approval", Toast.LENGTH_SHORT).show();
                    clearForm();
                    fetchUserRecipes(user.getUid());
                    Log.d(TAG, "Recipe posted: " + recipeId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error posting recipe: " + e.getMessage(), e);
                });
    }

    private void clearForm() {
        recipeTitle.setText("");
        recipeTime.setText("");
        imageStatusIcon.setVisibility(View.GONE); // Hide icon
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
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    Log.d(TAG, "Profile image selected: " + imageUri);
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
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
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    Log.d(TAG, "Recipe image selected: " + imageUri);
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap != null) {
                            uploadImageToCloudinary(bitmap, false);
                        } else {
                            Log.e(TAG, "Failed to decode bitmap");
                            imageStatusIcon.setVisibility(View.GONE); // Hide icon on failure
                        }
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "File not found", e);
                        imageStatusIcon.setVisibility(View.GONE); // Hide icon on failure
                    }
                } else {
                    Log.d(TAG, "Recipe image selection cancelled or failed");
                    imageStatusIcon.setVisibility(View.GONE); // Hide icon on cancellation
                }
            }
    );

    private void uploadImageToCloudinary(Bitmap bitmap, boolean isProfilePicture) {
        new Thread(() -> {
            try {
                File file = convertBitmapToFile(bitmap);
                if (file == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error preparing image for upload", Toast.LENGTH_SHORT).show();
                        imageStatusIcon.setVisibility(View.GONE); // Hide icon on failure
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
                        runOnUiThread(() -> {
                            Glide.with(this)
                                    .load(finalImageUrl)
                                    .apply(new RequestOptions()
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .placeholder(R.drawable.user)
                                            .error(R.drawable.user))
                                    .into(profileImage);
                        });
                    } else {
                        recipeImageUrl = imageUrl;
                        runOnUiThread(() -> {
                            imageStatusIcon.setImageResource(R.drawable.ic_check); // Show check icon
                            imageStatusIcon.setVisibility(View.VISIBLE);
                        });
                    }
                    Log.d(TAG, "Image uploaded: " + imageUrl);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Image upload returned no URL", Toast.LENGTH_SHORT).show();
                        imageStatusIcon.setVisibility(View.GONE); // Hide icon on failure
                    });
                    Log.e(TAG, "No URL returned from Cloudinary");
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    imageStatusIcon.setVisibility(View.GONE); // Hide icon on failure
                });
                Log.e(TAG, "Upload error", e);
            }
        }).start();
    }

    private File convertBitmapToFile(Bitmap bitmap) {
        File file = new File(getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            Log.d(TAG, "File created at: " + file.getAbsolutePath() + ", size: " + file.length());
            return file;
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error converting image: " + e.getMessage(), Toast.LENGTH_LONG).show());
            Log.e(TAG, "Error converting image", e);
            return null;
        }
    }

    private void saveProfilePictureToFirestore(String imageUrl) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in for saving profile picture");
            runOnUiThread(() -> Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show());
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
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Profile picture saved successfully", Toast.LENGTH_SHORT).show();
                        loadProfilePicture(user.getEmail());
                    });
                    Log.d(TAG, "Profile picture saved for user: " + userId + ", URL: " + imageUrl);
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to save profile picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Failed to save profile picture for user: " + userId + ", error: " + e.getMessage(), e);
                    });
                });
    }

    private void loadProfilePicture(String email) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String imageUrl = queryDocumentSnapshots.getDocuments().get(0).getString("profilePicture");
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            imageUrl = imageUrl.replace("http://", "https://");
                            Glide.with(this)
                                    .load(imageUrl)
                                    .apply(new RequestOptions()
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
                    Log.e(TAG, "Error loading profile picture: " + e.getMessage(), e);
                    profileImage.setImageResource(R.drawable.user);
                });
    }
}