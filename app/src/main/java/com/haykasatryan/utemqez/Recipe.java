package com.haykasatryan.utemqez;

import java.util.List;

public class Recipe {
    private String title;
    private String imageUrl;
    private int id;
    private List<String> category;  // This is important for filtering

    public Recipe() {
        // Empty constructor required for Firestore deserialization
    }

    public Recipe(String title, String imageUrl, int id, List<String> category) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.id = id;
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getId() {
        return id;
    }

    public List<String> getCategory() {
        return category;
    }
}
