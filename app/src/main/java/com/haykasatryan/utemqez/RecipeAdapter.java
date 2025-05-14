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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_RECIPE = 0;
    private static final int TYPE_LOADING = 1;
    private List<Recipe> recipeList;
    private final int layoutResId;
    private OnDeleteClickListener onDeleteClickListener;
    private boolean isLoading = false;

    // Interface for delete button click events
    public interface OnDeleteClickListener {
        void onDeleteClick(Recipe recipe);
    }

    public RecipeAdapter(List<Recipe> recipeList, int layoutResId) {
        this.recipeList = recipeList != null ? recipeList : new ArrayList<>();
        this.layoutResId = layoutResId;
        setHasStableIds(true); // Enable stable IDs
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_RECIPE) {
            return recipeList.get(position).getId();
        }
        return -1; // Loading item has no ID
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    public void updateList(List<Recipe> newList) {
        List<Recipe> newListSafe = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RecipeDiffCallback(recipeList, newListSafe));
        this.recipeList = newListSafe;
        diffResult.dispatchUpdatesTo(this);
    }

    public void setLoading(boolean loading) {
        if (isLoading != loading) {
            isLoading = loading;
            if (loading) {
                notifyItemInserted(recipeList.size());
            } else {
                notifyItemRemoved(recipeList.size());
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (position == recipeList.size() && isLoading) ? TYPE_LOADING : TYPE_RECIPE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_RECIPE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false);
            return new RecipeViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.loading_item, parent, false);
            return new LoadingViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RecipeViewHolder) {
            Recipe recipe = recipeList.get(position);
            RecipeViewHolder recipeHolder = (RecipeViewHolder) holder;
            recipeHolder.recipeTitle.setText(recipe.getTitle() != null ? recipe.getTitle() : "");

            // Optimized Glide loading
            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(layoutResId == R.layout.recipe_item_main ? 168 : 120,
                            layoutResId == R.layout.recipe_item_main ? 128 : 100)
                    .placeholder(R.drawable.recipe_image)
                    .error(R.drawable.recipe_image);

            Glide.with(holder.itemView.getContext())
                    .load(recipe.getImageUrl())
                    .apply(options)
                    .thumbnail(0.25f) // Load a low-res thumbnail first
                    .into(recipeHolder.recipeImage);

            // Handle View Details button click
            recipeHolder.viewDetailsButton.setOnClickListener(v -> {
                Intent intent = new Intent(holder.itemView.getContext(), RecipeDetailActivity.class);
                intent.putExtra("recipe", recipe);
                holder.itemView.getContext().startActivity(intent);
            });

            if (recipeHolder.deleteButton != null) {
                recipeHolder.deleteButton.setVisibility(onDeleteClickListener != null ? View.VISIBLE : View.GONE);
                recipeHolder.deleteButton.setOnClickListener(v -> {
                    if (onDeleteClickListener != null) {
                        onDeleteClickListener.onDeleteClick(recipe);
                    }
                });
            }
        }
        // No binding needed for LoadingViewHolder
    }

    @Override
    public int getItemCount() {
        return recipeList.size() + (isLoading ? 1 : 0);
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView recipeImage;
        TextView recipeTitle;
        Button viewDetailsButton;
        ImageButton deleteButton;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            recipeImage = itemView.findViewById(R.id.recipeImage);
            recipeTitle = itemView.findViewById(R.id.recipeTitle);
            viewDetailsButton = itemView.findViewById(R.id.viewDetailsButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    // DiffUtil callback for efficient updates
    static class RecipeDiffCallback extends DiffUtil.Callback {
        private final List<Recipe> oldList;
        private final List<Recipe> newList;

        RecipeDiffCallback(List<Recipe> oldList, List<Recipe> newList) {
            this.oldList = oldList != null ? oldList : new ArrayList<>();
            this.newList = newList != null ? newList : new ArrayList<>();
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Recipe oldRecipe = oldList.get(oldItemPosition);
            Recipe newRecipe = newList.get(newItemPosition);
            return oldRecipe.getTitle() != null && oldRecipe.getTitle().equals(newRecipe.getTitle()) &&
                    (oldRecipe.getImageUrl() != null ? oldRecipe.getImageUrl().equals(newRecipe.getImageUrl()) :
                            newRecipe.getImageUrl() == null);
        }
    }
}