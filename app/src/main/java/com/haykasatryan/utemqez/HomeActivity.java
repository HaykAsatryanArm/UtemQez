package com.haykasatryan.utemqez;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button btnLogin, btnRegister;
    private TextView welcomeText;

    private static final String TAG = "SearchActivity";
    private RecyclerView recyclerView;
    private RecipeAdapter adapter;
    private final List<Recipe> recipeList = new ArrayList<>();

    private Button buttonBreakfast;
    private Button buttonLunch;
    private Button buttonDinner;
    private Button buttonSnack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        welcomeText = findViewById(R.id.header_title);

        FirebaseUser user = mAuth.getCurrentUser(); // Get currently logged-in user

        if (user != null) {
            // User is logged in, display their name
            String userName = user.getDisplayName();
            if (userName == null || userName.isEmpty()) {
                userName = user.getEmail(); // Fallback to email if name is not set
            }
            welcomeText.setText("Welcome, " + userName + "!");
            btnLogin.setVisibility(View.GONE);  // Hide Login button
            btnRegister.setVisibility(View.GONE); // Hide Register button
        } else {
            // User is not logged in, show buttons
            welcomeText.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            btnRegister.setVisibility(View.VISIBLE);
        }

        // Redirect to Login Page
        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Redirect to Register Page
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        welcomeText.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        recyclerView = findViewById(R.id.recipeRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);

        buttonBreakfast = findViewById(R.id.buttonBreakfast);
        buttonLunch = findViewById(R.id.buttonLunch);
        buttonDinner = findViewById(R.id.buttonDinner);
        buttonSnack = findViewById(R.id.buttonSnack);

        buttonBreakfast.setBackgroundColor(ContextCompat.getColor(this, R.color.main));
        buttonLunch.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonDinner.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonSnack.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));

        buttonBreakfast.setOnClickListener(v -> {
            fetchRecipes("breakfast");
            updateButtonColors(buttonBreakfast);
        });
        buttonLunch.setOnClickListener(v -> {
            fetchRecipes("meal");
            updateButtonColors(buttonLunch);
        });
        buttonDinner.setOnClickListener(v -> {
            fetchRecipes("dinner");
            updateButtonColors(buttonDinner);
        });
        buttonSnack.setOnClickListener(v -> {
            fetchRecipes("dessert");
            updateButtonColors(buttonSnack);
        });

        fetchRecipes("breakfast");
    }

    private void updateButtonColors(Button activeButton) {
        buttonBreakfast.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonBreakfast.setTextColor(ContextCompat.getColor(this, R.color.blackot));
        buttonLunch.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonLunch.setTextColor(ContextCompat.getColor(this, R.color.blackot));
        buttonDinner.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonDinner.setTextColor(ContextCompat.getColor(this, R.color.blackot));
        buttonSnack.setBackgroundColor(ContextCompat.getColor(this, R.color.grey));
        buttonSnack.setTextColor(ContextCompat.getColor(this, R.color.blackot));

        activeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.main));
        activeButton.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void fetchRecipes(String category) {
        OkHttpClient client = new OkHttpClient();

        String url = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/complexSearch?query="
                + category + "&number=100";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("x-rapidapi-key", "c4aa63cb6bmsh5d7b5feeec3761dp12d296jsn98c838a3be2f")
                .addHeader("x-rapidapi-host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "API call failed", e);
                useMockData(category);
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        JSONArray results = jsonObject.getJSONArray("results");

                        recipeList.clear();

                        for (int i = 0; i < results.length(); i++) {
                            JSONObject obj = results.getJSONObject(i);
                            String title = obj.getString("title");
                            String image = obj.getString("image");
                            int id = obj.getInt("id");

                            Recipe recipe = new Recipe(title, image, id);
                            recipeList.add(recipe);
                        }

                        runOnUiThread(() -> {
                            if (adapter == null) {
                                adapter = new RecipeAdapter(recipeList, R.layout.recipe_item_main);
                                recyclerView.setAdapter(adapter);
                            } else {
                                adapter.notifyDataSetChanged();
                            }
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error", e);
                    }
                } else {
                    Log.e(TAG, "API response unsuccessful. Code: " + response.code());
                    useMockData(category);
                }
            }
        });
    }

    private void useMockData(String category) {
        recipeList.clear();
        switch (category) {
            case "breakfast":
                recipeList.add(new Recipe("Pancakes", String.valueOf(R.drawable.burger), 1));
                recipeList.add(new Recipe("Omelette", String.valueOf(R.drawable.burger), 2));
                recipeList.add(new Recipe("French Toast", String.valueOf(R.drawable.burger), 3));
                recipeList.add(new Recipe("Smoothie Bowl", String.valueOf(R.drawable.burger), 4));
                recipeList.add(new Recipe("Avocado Toast", String.valueOf(R.drawable.burger), 5));
                break;
            case "meal":
                recipeList.add(new Recipe("Grilled Chicken", String.valueOf(R.drawable.burger), 6));
                recipeList.add(new Recipe("Pasta", String.valueOf(R.drawable.burger), 7));
                recipeList.add(new Recipe("Salad", String.valueOf(R.drawable.burger), 8));
                recipeList.add(new Recipe("Burger", String.valueOf(R.drawable.burger), 9));
                recipeList.add(new Recipe("Steak", String.valueOf(R.drawable.burger), 10));
                break;
            case "dinner":
                recipeList.add(new Recipe("Roast Chicken", String.valueOf(R.drawable.burger), 11));
                recipeList.add(new Recipe("Fish Tacos", String.valueOf(R.drawable.burger), 12));
                recipeList.add(new Recipe("Beef Stew", String.valueOf(R.drawable.burger), 13));
                recipeList.add(new Recipe("Vegetable Stir Fry", String.valueOf(R.drawable.burger), 14));
                recipeList.add(new Recipe("Lasagna", String.valueOf(R.drawable.burger), 15));
                break;
            case "dessert":
                recipeList.add(new Recipe("Brownies", String.valueOf(R.drawable.burger), 16));
                recipeList.add(new Recipe("Ice Cream", String.valueOf(R.drawable.burger), 17));
                recipeList.add(new Recipe("Cheesecake", String.valueOf(R.drawable.burger), 18));
                recipeList.add(new Recipe("Cupcakes", String.valueOf(R.drawable.burger), 19));
                recipeList.add(new Recipe("Pudding", String.valueOf(R.drawable.burger), 20));
                break;
        }

        runOnUiThread(() -> {
            if (adapter == null) {
                adapter = new RecipeAdapter(recipeList, R.layout.recipe_item_main);
                recyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
        });
    }
}
