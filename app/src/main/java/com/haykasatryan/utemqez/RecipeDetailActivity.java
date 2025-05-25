package com.haykasatryan.utemqez;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipeDetailActivity extends AppCompatActivity {
    private static final String TAG = "RecipeDetailActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private Recipe recipe;
    private TextView detailRecipeTitle, detailReadyInMinutes, detailIngredients, detailInstructions;
    private TextView ingredientsHeader, instructionsHeader;
    private LinearLayout ingredientsContent, instructionsContent;
    private ImageView detailRecipeImage;
    private TextView caloriesText, proteinText, fatText, carbsText;
    private Button voiceButton;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private List<String> instructionSteps;
    private int currentInstructionIndex = -1;
    private boolean isVoiceModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        // Configure audio settings
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
        int maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);

        // Initialize views
        detailRecipeTitle = findViewById(R.id.detailRecipeTitle);
        detailReadyInMinutes = findViewById(R.id.detailReadyInMinutes);
        detailIngredients = findViewById(R.id.detailIngredients);
        detailInstructions = findViewById(R.id.detailInstructions);
        detailRecipeImage = findViewById(R.id.detailRecipeImage);
        caloriesText = findViewById(R.id.caloriesText);
        proteinText = findViewById(R.id.proteinText);
        fatText = findViewById(R.id.fatText);
        carbsText = findViewById(R.id.carbsText);
        ingredientsHeader = findViewById(R.id.ingredientsHeader);
        instructionsHeader = findViewById(R.id.instructionsHeader);
        ingredientsContent = findViewById(R.id.ingredientsContent);
        instructionsContent = findViewById(R.id.instructionsContent);
        voiceButton = findViewById(R.id.voiceButton);
        Button closeButton = findViewById(R.id.closeButton);

        // Get recipe from intent
        recipe = getIntent().getParcelableExtra("recipe");
        if (recipe == null) {
            Log.e(TAG, "No recipe provided in intent");
            Toast.makeText(this, "Error loading recipe", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Populate views
        detailRecipeTitle.setText(recipe.getTitle() != null ? recipe.getTitle() : "Untitled");
        detailReadyInMinutes.setText(recipe.getReadyInMinutes() > 0 ? recipe.getReadyInMinutes() + " Min" : "N/A");

        // Load image with Glide, ensuring HTTPS
        String imageUrl = recipe.getImageUrl();
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.recipe_image)
                .error(R.drawable.recipe_image);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageUrl = imageUrl.replace("http://", "https://");
            Glide.with(this)
                    .load(imageUrl)
                    .apply(options)
                    .into(detailRecipeImage);
        } else {
            Log.w(TAG, "No image URL provided for recipe: " + recipe.getTitle());
            detailRecipeImage.setImageResource(R.drawable.recipe_image);
        }

        // Populate nutrition
        Nutrition nutrition = recipe.getNutrition();
        if (nutrition != null) {
            caloriesText.setText(nutrition.getCalories() != null ? nutrition.getCalories() : "N/A");
            proteinText.setText(nutrition.getProtein() != null ? nutrition.getProtein() : "N/A");
            fatText.setText(nutrition.getFat() != null ? nutrition.getFat() : "N/A");
            carbsText.setText(nutrition.getCarbs() != null ? nutrition.getCarbs() : "N/A");
        } else {
            Log.w(TAG, "No nutrition data for recipe: " + recipe.getTitle());
            caloriesText.setText("N/A");
            proteinText.setText("N/A");
            fatText.setText("N/A");
            carbsText.setText("N/A");
        }

        // Populate ingredients
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients != null && !ingredients.isEmpty()) {
            StringBuilder ingredientsText = new StringBuilder();
            for (Ingredient ingredient : ingredients) {
                ingredientsText.append("• ")
                        .append(ingredient.getAmount() != null ? ingredient.getAmount() : "")
                        .append(" ")
                        .append(ingredient.getName() != null ? ingredient.getName() : "")
                        .append("\n");
            }
            detailIngredients.setText(ingredientsText.toString().trim());
        } else {
            Log.w(TAG, "No ingredients for recipe: " + recipe.getTitle());
            detailIngredients.setText("No ingredients available");
        }

        // Parse and display instructions
        instructionSteps = parseInstructions(recipe.getInstructions());
        StringBuilder instructionsText = new StringBuilder();
        for (String step : instructionSteps) {
            instructionsText.append(step).append("\n");
        }
        detailInstructions.setText(instructionsText.length() > 0 ? instructionsText.toString().trim() : "No instructions available");

        // Toggle content visibility
        ingredientsHeader.setOnClickListener(v -> toggleContent(true));
        instructionsHeader.setOnClickListener(v -> toggleContent(false));

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                Log.d(TAG, "TextToSpeech initialized successfully");
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: status " + status);
                Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Next' or 'Continue' to proceed");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Speech recognizer ready for input");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech input started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech input ended");
            }

            @Override
            public void onError(int error) {
                if (isVoiceModeActive) {
                    String errorMessage;
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            errorMessage = "No speech recognized";
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            errorMessage = "Speech timeout";
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            errorMessage = "Recognizer busy";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            errorMessage = "Insufficient permissions";
                            break;
                        default:
                            errorMessage = "Speech recognition error: " + error;
                    }
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    Toast.makeText(RecipeDetailActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                            ContextCompat.checkSelfPermission(RecipeDetailActivity.this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        speechRecognizer.startListening(recognizerIntent);
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (isVoiceModeActive) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    boolean commandRecognized = false;
                    if (matches != null && !matches.isEmpty()) {
                        String command = matches.get(0).toLowerCase();
                        Log.d(TAG, "Recognized command: " + command + ", matches: " + matches);
                        Toast.makeText(RecipeDetailActivity.this, "Heard: " + command, Toast.LENGTH_SHORT).show();
                        if (command.contains("next") || command.contains("neck") || command.contains("text") || command.contains("continue")) {
                            readNextInstruction();
                            commandRecognized = true;
                        }
                    } else {
                        Log.d(TAG, "No speech matches recognized");
                    }
                    if (!commandRecognized &&
                            ContextCompat.checkSelfPermission(RecipeDetailActivity.this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        speechRecognizer.startListening(recognizerIntent);
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (isVoiceModeActive) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String command = matches.get(0).toLowerCase();
                        Log.d(TAG, "Partial command: " + command + ", matches: " + matches);
                        if (command.contains("next") || command.contains("neck") || command.contains("text") || command.contains("continue")) {
                            readNextInstruction();
                            if (ContextCompat.checkSelfPermission(RecipeDetailActivity.this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                speechRecognizer.startListening(recognizerIntent);
                            }
                        }
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        // Voice button to start/stop voice mode
        voiceButton.setOnClickListener(v -> {
            if (!isVoiceModeActive) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
                    Toast.makeText(this, "Please grant microphone permission to use voice instructions", Toast.LENGTH_LONG).show();
                } else {
                    startVoiceMode();
                }
            } else {
                stopVoiceMode();
            }
        });

        // Close button
        closeButton.setOnClickListener(v -> finish());
    }

    private void startVoiceMode() {
        isVoiceModeActive = true;
        voiceButton.setText("Stop Voice Instructions");
        currentInstructionIndex = -1;
        readNextInstruction();
        speechRecognizer.startListening(recognizerIntent);
        Toast.makeText(this, "Say 'Next' or 'Continue' to hear the next instruction", Toast.LENGTH_LONG).show();
    }

    private void stopVoiceMode() {
        isVoiceModeActive = false;
        voiceButton.setText("Start Voice Instructions");
        speechRecognizer.stopListening();
        textToSpeech.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceMode();
            } else {
                Toast.makeText(this, "Microphone permission denied. Voice instructions unavailable.", Toast.LENGTH_LONG).show();
                voiceButton.setEnabled(false);
            }
        }
    }

    private List<String> parseInstructions(String instructions) {
        if (instructions == null || instructions.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Pattern pattern = Pattern.compile("(\\d+\\..*?)(?=\\d+\\.|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(instructions);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            String step = matcher.group(1).trim();
            if (!step.isEmpty()) {
                if (!step.endsWith(".")) {
                    step += ".";
                }
                result.add(step);
            }
        }
        if (result.isEmpty()) {
            String[] steps = instructions.split("\\s*\\d+\\.[\\s]*");
            for (String step : steps) {
                step = step.trim();
                if (!step.isEmpty()) {
                    if (!step.endsWith(".")) {
                        step += ".";
                    }
                    result.add(step);
                }
            }
        }
        return result;
    }

    private void readNextInstruction() {
        if (currentInstructionIndex + 1 < instructionSteps.size()) {
            currentInstructionIndex++;
            String step = instructionSteps.get(currentInstructionIndex);
            textToSpeech.speak(step, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            textToSpeech.speak("No more instructions.", TextToSpeech.QUEUE_FLUSH, null, null);
            stopVoiceMode();
        }
    }

    private void toggleContent(boolean showIngredients) {
        if (showIngredients) {
            ingredientsContent.setVisibility(View.VISIBLE);
            instructionsContent.setVisibility(View.GONE);
            ingredientsHeader.setBackgroundResource(R.drawable.header_background);
            ingredientsHeader.setTextColor(getResources().getColor(android.R.color.white));
            instructionsHeader.setBackgroundResource(R.drawable.header_unselected_background);
            instructionsHeader.setTextColor(getResources().getColor(R.color.black_background));
        } else {
            ingredientsContent.setVisibility(View.GONE);
            instructionsContent.setVisibility(View.VISIBLE);
            ingredientsHeader.setBackgroundResource(R.drawable.header_unselected_background);
            ingredientsHeader.setTextColor(getResources().getColor(R.color.black_background));
            instructionsHeader.setBackgroundResource(R.drawable.header_background);
            instructionsHeader.setTextColor(getResources().getColor(android.R.color.white));
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}