package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.squareup.picasso.Picasso;

import java.util.Locale;

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
    private Button closeButton;
    private Button voiceButton;
    private TextToSpeech textToSpeech;

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
        closeButton = findViewById(R.id.closeButton);
        voiceButton = findViewById(R.id.voiceButton);

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    voiceButton.setEnabled(false);
                    Log.e("TextToSpeech", "Language not supported or missing data");
                    Toast.makeText(this, "Text-to-speech language not available", Toast.LENGTH_SHORT).show();
                } else {
                    voiceButton.setEnabled(true);
                    Log.d("TextToSpeech", "Initialization and language set successfully");
                }
            } else {
                voiceButton.setEnabled(false);
                Log.e("TextToSpeech", "Initialization failed with status: " + status);
                Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up close button click listener to navigate to HomeActivity
        closeButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecipeDetailActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Set up voice button click listener
        voiceButton.setOnClickListener(v -> {
            String text = Html.fromHtml(instructions.getText().toString(), Html.FROM_HTML_MODE_LEGACY).toString();
            if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                textToSpeech.setSpeechRate(0.9f);
                textToSpeech.setPitch(1.0f);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "recipe_instructions");
                Log.d("TextToSpeech", "Speaking: " + text);
            } else {
                Log.d("TextToSpeech", "TextToSpeech is null or already speaking");
            }
        });

        // Get recipe data from intent
        Recipe recipe = getIntent().getParcelableExtra("recipe");
        if (recipe != null) {
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
            recipeDescription.setVisibility(View.GONE);
            readyInMinutes.setVisibility(View.GONE);
            ingredients.setVisibility(View.GONE);
            instructions.setVisibility(View.GONE);
            caloriesText.setVisibility(View.GONE);
            carbsText.setVisibility(View.GONE);
            proteinText.setVisibility(View.GONE);
            fatText.setVisibility(View.GONE);
            recipeImage.setImageResource(R.drawable.profile_background);
            voiceButton.setVisibility(View.GONE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}