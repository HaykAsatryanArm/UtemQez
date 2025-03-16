package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
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

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    private RecyclerView recyclerView;
    private RecipeAdapter adapter;
    private final List<Recipe> recipeList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        recyclerView = findViewById(R.id.recipeRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fetchRecipes();
    }

    private void fetchRecipes() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com/recipes/complexSearch?query=mix&number=100")
                .get()
                .addHeader("x-rapidapi-key", "c4aa63cb6bmsh5d7b5feeec3761dp12d296jsn98c838a3be2f")
                .addHeader("x-rapidapi-host", "spoonacular-recipe-food-nutrition-v1.p.rapidapi.com")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "API call failed", e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        JSONArray results = jsonObject.getJSONArray("results");

                        for (int i = 0; i < results.length(); i++) {
                            JSONObject obj = results.getJSONObject(i);
                            String title = obj.getString("title");
                            String image = obj.getString("image");
                            int id = obj.getInt("id");

                            Recipe recipe = new Recipe(title, image, id);
                            recipeList.add(recipe);
                        }

                        runOnUiThread(() -> {
                            adapter = new RecipeAdapter(recipeList, R.layout.recipe_item_search);
                            recyclerView.setAdapter(adapter);
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error", e);
                    }
                } else {
                    Log.e(TAG, "API response unsuccessful. Code: " + response.code());
                }
            }
        });
    }
}
