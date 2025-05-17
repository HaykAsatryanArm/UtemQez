package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private TextView recipeDescription;
    private TextView readyInMinutes;
    private TextView ingredients;
    private TextView instructions;
    private TextView caloriesText;
    private TextView carbsText;
    private TextView proteinText;
    private TextView fatText;
    private LinearLayout ingredientsContent;
    private LinearLayout instructionsContent;
    private TextView ingredientsHeader;
    private TextView instructionsHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recipe_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        recipeImage = findViewById(R.id.detailRecipeImage);
        recipeTitle = findViewById(R.id.detailRecipeTitle);
        readyInMinutes = findViewById(R.id.detailReadyInMinutes);
        ingredients = findViewById(R.id.detailIngredients);
        instructions = findViewById(R.id.detailInstructions);
        caloriesText = findViewById(R.id.caloriesText);
        carbsText = findViewById(R.id.carbsText);
        proteinText = findViewById(R.id.proteinText);
        fatText = findViewById(R.id.fatText);
        ingredientsContent = findViewById(R.id.ingredientsContent);
        instructionsContent = findViewById(R.id.instructionsContent);
        ingredientsHeader = findViewById(R.id.ingredientsHeader);
        instructionsHeader = findViewById(R.id.instructionsHeader);

        // Get recipe data from intent
        Recipe recipe = getIntent().getParcelableExtra("recipe");
        if (recipe != null) {
            // Set basic info
            recipeTitle.setText(recipe.getTitle());
            readyInMinutes.setText(String.valueOf(recipe.getReadyInMinutes()) + " Min");

            // Set nutrition info
            if (recipe.getNutrition() != null) {
                Nutrition nutr = recipe.getNutrition();
                caloriesText.setText(nutr.getCalories() != null ? nutr.getCalories() : "N/A");
                carbsText.setText(nutr.getCarbs() != null ? nutr.getCarbs() : "N/A");
                proteinText.setText(nutr.getProtein() != null ? nutr.getProtein() : "N/A");
                fatText.setText(nutr.getFat() != null ? nutr.getFat() : "N/A");
            } else {
                caloriesText.setText("N/A");
                carbsText.setText("N/A");
                proteinText.setText("N/A");
                fatText.setText("N/A");
            }

            // Format and set ingredients
            StringBuilder ingredientsText = new StringBuilder();
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredientsText.append("• ")
                        .append(ingredient.getAmount())
                        .append(" ")
                        .append(ingredient.getName())
                        .append("\n");
            }
            ingredients.setText(Html.fromHtml(ingredientsText.toString(), Html.FROM_HTML_MODE_LEGACY));

            // Set instructions with fallback
            String instructionsText = recipe.getInstructions() != null && !recipe.getInstructions().isEmpty()
                    ? recipe.getInstructions()
                    : "No instructions available";
            instructions.setText(Html.fromHtml(instructionsText, Html.FROM_HTML_MODE_LEGACY));

            // Load image
            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                Picasso.get()
                        .load(recipe.getImageUrl().replace("http://", "https://"))
                        .placeholder(R.drawable.user)
                        .error(R.drawable.user)
                        .into(recipeImage);
            }
        } else {
            recipeTitle.setText("Error: No recipe data");
            recipeDescription.setVisibility(TextView.GONE);
            readyInMinutes.setVisibility(TextView.GONE);
            ingredients.setVisibility(TextView.GONE);
            instructions.setVisibility(TextView.GONE);
            caloriesText.setVisibility(TextView.GONE);
            carbsText.setVisibility(TextView.GONE);
            proteinText.setVisibility(TextView.GONE);
            fatText.setVisibility(TextView.GONE);
            recipeImage.setImageResource(R.drawable.profile_background);
        }
    }

    public void toggleContent(View view) {
        if (view.getId() == R.id.ingredientsHeader) {
            ingredientsContent.setVisibility(View.VISIBLE);
            instructionsContent.setVisibility(View.GONE);
            ingredientsHeader.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            instructionsHeader.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        } else if (view.getId() == R.id.instructionsHeader) {
            ingredientsContent.setVisibility(View.GONE);
            instructionsContent.setVisibility(View.VISIBLE);
            ingredientsHeader.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            instructionsHeader.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }
}