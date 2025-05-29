package com.haykasatryan.utemqez;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreUploader {

    private static final String TAG = "FirestoreUploader";
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    public FirestoreUploader() {
        try {
            db = FirebaseFirestore.getInstance();
            storage = FirebaseStorage.getInstance();
            Log.d(TAG, "Firestore and FirebaseStorage initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firestore or FirebaseStorage: " + e.getMessage(), e);
        }
    }

    private String loadJSONFromAsset(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error reading JSON file: " + e.getMessage(), e);
            return null;
        }
    }

    public void uploadJSONToFirestore(Context context, String fileName) {
        String jsonString = loadJSONFromAsset(context, fileName);
        if (jsonString == null) {
            Log.e(TAG, "Failed to load JSON file");
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                Map<String, Object> recipeData = new HashMap<>();

                recipeData.put("id", jsonObject.optInt("id", -1));
                recipeData.put("title", jsonObject.optString("title", ""));
                recipeData.put("readyInMinutes", jsonObject.optInt("readyInMinutes", 0));
                recipeData.put("sourceUrl", jsonObject.optString("sourceUrl", ""));
                recipeData.put("instructions", jsonObject.optString("instructions", ""));

                JSONArray ingredientsArray = jsonObject.optJSONArray("ingredients");
                if (ingredientsArray != null) {
                    List<Map<String, String>> ingredientsList = new ArrayList<>();
                    for (int j = 0; j < ingredientsArray.length(); j++) {
                        JSONObject ingredientObj = ingredientsArray.getJSONObject(j);
                        Map<String, String> ingredientMap = new HashMap<>();
                        ingredientMap.put("amount", ingredientObj.optString("amount", ""));
                        ingredientMap.put("name", ingredientObj.optString("name", ""));
                        ingredientsList.add(ingredientMap);
                    }
                    recipeData.put("ingredients", ingredientsList);
                }

                JSONObject nutritionObj = jsonObject.optJSONObject("nutrition");
                if (nutritionObj != null) {
                    Map<String, String> nutritionMap = new HashMap<>();
                    nutritionMap.put("calories", nutritionObj.optString("calories", ""));
                    nutritionMap.put("protein", nutritionObj.optString("protein", ""));
                    nutritionMap.put("fat", nutritionObj.optString("fat", ""));
                    nutritionMap.put("carbs", nutritionObj.optString("carbs", ""));
                    recipeData.put("nutrition", nutritionMap);
                }

                JSONArray categoryArray = jsonObject.optJSONArray("category");
                if (categoryArray != null) {
                    List<String> categoryList = new ArrayList<>();
                    for (int k = 0; k < categoryArray.length(); k++) {
                        categoryList.add(categoryArray.optString(k, ""));
                    }
                    recipeData.put("category", categoryList);
                }

                recipeData.put("imageUrl", jsonObject.optString("imageUrl", ""));

                String documentId = String.valueOf(jsonObject.optInt("id", -1));
                if (documentId.equals("-1")) {
                    Log.e(TAG, "Invalid recipe ID, skipping this recipe");
                    continue;
                }

                db.collection("recipes").document(documentId)
                        .set(recipeData)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Recipe successfully uploaded: " + documentId))
                        .addOnFailureListener(e -> Log.e(TAG, "Error uploading recipe: " + documentId, e));
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage(), e);
        }
    }

    public void checkFirestoreInitialization() {
        try {
            db.collection("test").limit(1).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> Log.d(TAG, "Firestore is initialized and connected"))
                    .addOnFailureListener(e -> Log.e(TAG, "Firestore initialization failed: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Error during Firestore operation: " + e.getMessage(), e);
        }
    }
}
