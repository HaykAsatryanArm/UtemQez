package com.haykasatryan.utemqez;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

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

    private FirebaseAuth mAuth;
    private TextView userName, userEmail;
    private ImageView profileImage;
    private Button btnLogout, btnSelectImage, btnAddIngredient, btnAddInstruction, btnAddCategory, btnPostRecipe;
    private EditText recipeTitle, recipeTime;
    private TextView imageUrlText;
    private LinearLayout ingredientsContainer, instructionsContainer, categoriesContainer;
    private EditText nutritionCalories, nutritionProtein, nutritionFat, nutritionCarbs;
    private Cloudinary cloudinary;
    private String recipeImageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();

        userName = findViewById(R.id.profileName);
        userEmail = findViewById(R.id.profileEmail);
        profileImage = findViewById(R.id.profilePicture);
        btnLogout = findViewById(R.id.btnLogout);
        recipeTitle = findViewById(R.id.recipeTitle);
        recipeTime = findViewById(R.id.recipeTime);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        imageUrlText = findViewById(R.id.imageUrlText);
        ingredientsContainer = findViewById(R.id.ingredientsContainer);
        btnAddIngredient = findViewById(R.id.btnAddIngredient);
        instructionsContainer = findViewById(R.id.instructionsContainer);
        btnAddInstruction = findViewById(R.id.btnAddInstruction);
        nutritionCalories = findViewById(R.id.nutritionCalories);
        nutritionProtein = findViewById(R.id.nutritionProtein);
        nutritionFat = findViewById(R.id.nutritionFat);
        nutritionCarbs = findViewById(R.id.nutritionCarbs);
        categoriesContainer = findViewById(R.id.categoriesContainer);
        btnAddCategory = findViewById(R.id.btnAddCategory);
        btnPostRecipe = findViewById(R.id.btnPostRecipe);

        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "deor2c9as",
                "api_key", "882433745744291",
                "api_secret", "tPtmtB9HaZ7pTv6dn7lEJJssOh0"
        ));

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userName.setText(user.getDisplayName());
            userEmail.setText(user.getEmail());
            loadProfilePicture(user.getEmail());
        }

        profileImage.setOnClickListener(v -> pickImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
        btnSelectImage.setOnClickListener(v -> pickRecipeImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
        btnAddIngredient.setOnClickListener(v -> addIngredientField());
        btnAddInstruction.setOnClickListener(v -> addInstructionField());
        btnAddCategory.setOnClickListener(v -> addCategoryField());
        btnPostRecipe.setOnClickListener(v -> postRecipe());
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        });

        addIngredientField();
        addInstructionField();
        addCategoryField();
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
        if (user == null) return;

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

        // Collect instructions as a list first
        List<String> instructionList = new ArrayList<>();
        for (int i = 0; i < instructionsContainer.getChildCount(); i++) {
            EditText instruction = (EditText) instructionsContainer.getChildAt(i);
            String instructionText = instruction.getText().toString().trim();
            if (!instructionText.isEmpty()) {
                instructionList.add(instructionText);
            }
        }

        // Combine instructions into a single numbered string
        String instructions;
        if (instructionList.isEmpty()) {
            instructions = "No instructions found";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < instructionList.size(); i++) {
                sb.append(i + 1).append(". ").append(instructionList.get(i));
                if (i < instructionList.size() - 1) {
                    sb.append(" ");
                }
            }
            instructions = sb.toString();
        }

        Map<String, String> nutrition = new HashMap<>();
        nutrition.put("calories", nutritionCalories.getText().toString() + " Calories");
        nutrition.put("protein", nutritionProtein.getText().toString() + "g Protein");
        nutrition.put("fat", nutritionFat.getText().toString() + "g Total Fat");
        nutrition.put("carbs", nutritionCarbs.getText().toString() + "g Carbs");

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
        recipe.put("readyInMinutes", Integer.parseInt(recipeTime.getText().toString()));
        recipe.put("ingredients", ingredients);
        recipe.put("instructions", instructions); // Now a single String
        recipe.put("nutrition", nutrition);
        recipe.put("imageUrl", recipeImageUrl);
        recipe.put("category", categories);
        recipe.put("userId", user.getUid());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("recipes")
                .document(String.valueOf(recipeId))
                .set(recipe)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recipe posted successfully", Toast.LENGTH_SHORT).show();
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileActivity", "Failed to post recipe", e);
                    Toast.makeText(this, "Failed to post recipe", Toast.LENGTH_SHORT).show();
                });
    }

    private void clearForm() {
        recipeTitle.setText("");
        recipeTime.setText("");
        imageUrlText.setText("");
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
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        profileImage.setImageBitmap(bitmap);
                        uploadImageToCloudinary(bitmap, true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> pickRecipeImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        uploadImageToCloudinary(bitmap, false);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    private void uploadImageToCloudinary(Bitmap bitmap, boolean isProfilePicture) {
        new Thread(() -> {
            try {
                File file = convertBitmapToFile(bitmap);
                Map uploadResult = cloudinary.uploader().upload(file, ObjectUtils.emptyMap());
                String imageUrl = (String) uploadResult.get("url");
                if (isProfilePicture) {
                    saveProfilePictureToFirestore(imageUrl);
                } else {
                    recipeImageUrl = imageUrl;
                    runOnUiThread(() -> imageUrlText.setText(recipeImageUrl));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private File convertBitmapToFile(Bitmap bitmap) {
        File file = new File(getCacheDir(), "temp_image.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    private void saveProfilePictureToFirestore(String imageUrl) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(user.getUid())
                    .update("profilePicture", imageUrl)
                    .addOnSuccessListener(aVoid -> runOnUiThread(() -> Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()))
                    .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Failed to update profile picture", Toast.LENGTH_SHORT).show()));
        }
    }

    private void loadProfilePicture(String email) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String imageUrl = queryDocumentSnapshots.getDocuments().get(0).getString("profilePicture");
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Picasso.get()
                                    .load(imageUrl.replace("http://", "https://"))
                                    .placeholder(R.drawable.user)
                                    .error(R.drawable.user)
                                    .into(profileImage);
                        } else {
                            profileImage.setImageResource(R.drawable.user);
                        }
                    } else {
                        profileImage.setImageResource(R.drawable.user);
                    }
                })
                .addOnFailureListener(e -> profileImage.setImageResource(R.drawable.user));
    }
}