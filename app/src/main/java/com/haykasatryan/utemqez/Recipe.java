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

    public Recipe() {
        // Empty constructor required for Firestore deserialization
    }

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

    // Setter for instructions (added to resolve private access issue)
    public void setInstructions(String instructions) { this.instructions = instructions; }
}

// Ingredient class (unchanged)
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
}

// Nutrition class (unchanged)
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
}