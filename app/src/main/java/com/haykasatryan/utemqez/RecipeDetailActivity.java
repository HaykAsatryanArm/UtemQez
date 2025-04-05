package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.text.Html;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.squareup.picasso.Picasso;

public class RecipeDetailActivity extends AppCompatActivity {

    private ImageView recipeImage;
    private TextView recipeTitle;
    private TextView recipeCategory;
    private TextView readyInMinutes;
    private TextView ingredients;
    private TextView instructions;
    private TextView nutrition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recipe_detail);

        // Use the root view (NestedScrollView) for window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        recipeImage = findViewById(R.id.detailRecipeImage);
        recipeTitle = findViewById(R.id.detailRecipeTitle);
        recipeCategory = findViewById(R.id.detailRecipeCategory);
        readyInMinutes = findViewById(R.id.detailReadyInMinutes);
        ingredients = findViewById(R.id.detailIngredients);
        instructions = findViewById(R.id.detailInstructions);
        nutrition = findViewById(R.id.detailNutrition);

        // Get recipe data from intent
        Recipe recipe = getIntent().getParcelableExtra("recipe");
        if (recipe != null) {
            // Set basic info
            recipeTitle.setText(recipe.getTitle());
            recipeCategory.setText("Category: " + String.join(", ", recipe.getCategory()));
            readyInMinutes.setText("Ready in: " + recipe.getReadyInMinutes() + " minutes");

            // Format and set ingredients
            StringBuilder ingredientsText = new StringBuilder("<b>Ingredients:</b><br>");
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredientsText.append("• ")
                        .append(ingredient.getAmount())
                        .append(" ")
                        .append(ingredient.getName())
                        .append("<br>");
            }
            ingredients.setText(Html.fromHtml(ingredientsText.toString(), Html.FROM_HTML_MODE_LEGACY));

            // Set instructions with fallback
            String instructionsText = recipe.getInstructions() != null && !recipe.getInstructions().isEmpty()
                    ? recipe.getInstructions()
                    : "No instructions available";
            instructions.setText(Html.fromHtml("<b>Instructions:</b><br>" + instructionsText, Html.FROM_HTML_MODE_LEGACY));

            // Set nutrition with null check
            if (recipe.getNutrition() != null) {
                Nutrition nutr = recipe.getNutrition();
                String nutritionText = "<b>Nutrition:</b><br>" +
                        "Calories: " + (nutr.getCalories() != null ? nutr.getCalories() : "N/A") + "<br>" +
                        "Protein: " + (nutr.getProtein() != null ? nutr.getProtein() : "N/A") + "<br>" +
                        "Fat: " + (nutr.getFat() != null ? nutr.getFat() : "N/A") + "<br>" +
                        "Carbs: " + (nutr.getCarbs() != null ? nutr.getCarbs() : "N/A");
                nutrition.setText(Html.fromHtml(nutritionText, Html.FROM_HTML_MODE_LEGACY));
            } else {
                nutrition.setText(Html.fromHtml("<b>Nutrition:</b><br>Not available", Html.FROM_HTML_MODE_LEGACY));
            }

            // Load image with placeholder and error handling
            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                Picasso.get()
                        .load(recipe.getImageUrl())
                        .resize(1000, 1000) // Target size in pixels
                        .onlyScaleDown() // Only scale down, not up
                        .into(recipeImage);
            }
        } else {
            // Handle case where no recipe is passed
            recipeTitle.setText("Error: No recipe data");
            recipeCategory.setVisibility(TextView.GONE);
            readyInMinutes.setVisibility(TextView.GONE);
            ingredients.setVisibility(TextView.GONE);
            instructions.setVisibility(TextView.GONE);
            nutrition.setVisibility(TextView.GONE);
            recipeImage.setImageResource(R.drawable.profile_background);
        }
    }
}