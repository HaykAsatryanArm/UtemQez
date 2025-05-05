package com.haykasatryan.utemqez;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.squareup.picasso.Picasso;

import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {
    private final List<Recipe> recipeList;
    private final int layoutResource;
    private OnDeleteClickListener onDeleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(Recipe recipe);
    }

    public RecipeAdapter(List<Recipe> recipeList, int layoutResource) {
        this.recipeList = recipeList;
        this.layoutResource = layoutResource;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutResource, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        Recipe recipe = recipeList.get(position);
        holder.title.setText(recipe.getTitle());

        // Load image using Picasso
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            Picasso.get()
                    .load(recipe.getImageUrl().replace("http://", "https://"))
                    .placeholder(R.drawable.user)
                    .error(R.drawable.user)
                    .into(holder.image);
        }

        // Show delete button only for user's own recipes
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null && currentUserId.equals(recipe.getUserId())) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> {
                if (onDeleteClickListener != null) {
                    onDeleteClickListener.onDeleteClick(recipe);
                }
            });
        } else {
            holder.deleteButton.setVisibility(View.GONE);
        }

        holder.viewDetailsButton.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), RecipeDetailActivity.class);
            intent.putExtra("recipe", recipe);
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return recipeList.size();
    }

    public static class RecipeViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView image;
        Button viewDetailsButton;
        ImageButton deleteButton;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.recipeTitle);
            image = itemView.findViewById(R.id.recipeImage);
            viewDetailsButton = itemView.findViewById(R.id.viewDetailsButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}