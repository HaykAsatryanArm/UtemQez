package com.haykasatryan.utemqez;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class Recipe implements Parcelable {
    private int id;
    private String title;
    private int readyInMinutes;
    private String sourceUrl;
    private List<Ingredient> ingredients;
    private String instructions;
    private Nutrition nutrition;
    private String imageUrl;
    private List<String> category;
    private String userId; // For Firestore document ID
    private int likes; // Added for like functionality
    private boolean isApproved; // New field for approval status

    public Recipe() {}

    protected Recipe(Parcel in) {
        id = in.readInt();
        title = in.readString();
        readyInMinutes = in.readInt();
        sourceUrl = in.readString();
        ingredients = new ArrayList<>();
        in.readTypedList(ingredients, Ingredient.CREATOR);
        instructions = in.readString();
        nutrition = in.readParcelable(Nutrition.class.getClassLoader());
        imageUrl = in.readString();
        category = new ArrayList<>();
        in.readStringList(category);
        userId = in.readString();
        likes = in.readInt();
        isApproved = in.readByte() != 0; // Read boolean
    }

    public static final Creator<Recipe> CREATOR = new Creator<Recipe>() {
        @Override
        public Recipe createFromParcel(Parcel in) {
            return new Recipe(in);
        }

        @Override
        public Recipe[] newArray(int size) {
            return new Recipe[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeInt(readyInMinutes);
        dest.writeString(sourceUrl);
        dest.writeTypedList(ingredients);
        dest.writeString(instructions);
        dest.writeParcelable(nutrition, flags);
        dest.writeString(imageUrl);
        dest.writeStringList(category);
        dest.writeString(userId);
        dest.writeInt(likes);
        dest.writeByte((byte) (isApproved ? 1 : 0)); // Write boolean
    }

    // Getters
    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getReadyInMinutes() { return readyInMinutes; }
    public String getSourceUrl() { return sourceUrl; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public String getInstructions() { return instructions; }
    public Nutrition getNutrition() { return nutrition; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getCategory() { return category; }
    public String getUserId() { return userId; }
    public int getLikes() { return likes; }
    public boolean isApproved() { return isApproved; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setReadyInMinutes(int readyInMinutes) { this.readyInMinutes = readyInMinutes; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setIngredients(List<Ingredient> ingredients) { this.ingredients = ingredients; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setNutrition(Nutrition nutrition) { this.nutrition = nutrition; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setCategory(List<String> category) { this.category = category; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setLikes(int likes) { this.likes = likes; }
    public void setApproved(boolean isApproved) { this.isApproved = isApproved; }
}

class Ingredient implements Parcelable {
    private String amount;
    private String name;

    public Ingredient() {}

    protected Ingredient(Parcel in) {
        amount = in.readString();
        name = in.readString();
    }

    public static final Creator<Ingredient> CREATOR = new Creator<Ingredient>() {
        @Override
        public Ingredient createFromParcel(Parcel in) {
            return new Ingredient(in);
        }

        @Override
        public Ingredient[] newArray(int size) {
            return new Ingredient[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(amount);
        dest.writeString(name);
    }

    public String getAmount() { return amount; }
    public String getName() { return name; }

    public void setAmount(String amount) { this.amount = amount; }
    public void setName(String name) { this.name = name; }
}

class Nutrition implements Parcelable {
    private String calories;
    private String protein;
    private String fat;
    private String carbs;

    public Nutrition() {}

    protected Nutrition(Parcel in) {
        calories = in.readString();
        protein = in.readString();
        fat = in.readString();
        carbs = in.readString();
    }

    public static final Creator<Nutrition> CREATOR = new Creator<Nutrition>() {
        @Override
        public Nutrition createFromParcel(Parcel in) {
            return new Nutrition(in);
        }

        @Override
        public Nutrition[] newArray(int size) {
            return new Nutrition[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(calories);
        dest.writeString(protein);
        dest.writeString(fat);
        dest.writeString(carbs);
    }

    public String getCalories() { return calories; }
    public String getProtein() { return protein; }
    public String getFat() { return fat; }
    public String getCarbs() { return carbs; }

    public void setCalories(String calories) { this.calories = calories; }
    public void setProtein(String protein) { this.protein = protein; }
    public void setFat(String fat) { this.fat = fat; }
    public void setCarbs(String carbs) { this.carbs = carbs; }
}