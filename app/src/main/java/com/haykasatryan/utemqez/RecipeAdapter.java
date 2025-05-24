package com.haykasatryan.utemqez;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_RECIPE = 0;
    private static final int TYPE_LOADING = 1;
    private List<Recipe> recipeList;
    private final int layoutResId;
    private OnDeleteClickListener onDeleteClickListener;
    private OnApproveClickListener onApproveClickListener;
    private boolean isLoading = false;
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private List<String> likedRecipeIds = new ArrayList<>();

    public interface OnDeleteClickListener {
        void onDeleteClick(Recipe recipe);
    }

    public interface OnApproveClickListener {
        void onApproveClick(Recipe recipe);
    }

    public RecipeAdapter(List<Recipe> recipeList, int layoutResId) {
        this.recipeList = recipeList != null ? recipeList : new ArrayList<>();
        this.layoutResId = layoutResId;
        this.mAuth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        setHasStableIds(true);
        fetchLikedRecipes();
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_RECIPE) {
            return recipeList.get(position).getId();
        }
        return -1;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }

    public void setOnApproveClickListener(OnApproveClickListener listener) {
        this.onApproveClickListener = listener;
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

    private void fetchLikedRecipes() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        likedRecipeIds = (List<String>) documentSnapshot.get("likedRecipes");
                        if (likedRecipeIds == null) {
                            likedRecipeIds = new ArrayList<>();
                        }
                        notifyDataSetChanged();
                    })
                    .addOnFailureListener(e -> Log.e("RecipeAdapter", "Error fetching liked recipes", e));
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
            recipeHolder.recipeTitle.setText(recipe.getTitle() != null ? recipe.getTitle() : "Untitled");
            recipeHolder.likesCount.setText(String.valueOf(recipe.getLikes()));

            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(layoutResId == R.layout.recipe_item_main ? 168 : 120,
                            layoutResId == R.layout.recipe_item_main ? 128 : 100)
                    .placeholder(R.drawable.recipe_image)
                    .error(R.drawable.recipe_image);

            Glide.with(holder.itemView.getContext())
                    .load(recipe.getImageUrl())
                    .apply(options)
                    .thumbnail(0.25f)
                    .into(recipeHolder.recipeImage);

            String recipeIdStr = String.valueOf(recipe.getId());
            boolean isLiked = likedRecipeIds.contains(recipeIdStr);
            recipeHolder.likeButton.setImageResource(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

            recipeHolder.likeButton.setOnClickListener(v -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Context context = holder.itemView.getContext();
                    context.startActivity(new Intent(context, LoginActivity.class));
                    Toast.makeText(context, "Please log in to like recipes", Toast.LENGTH_SHORT).show();
                } else {
                    handleLike(recipe, recipeHolder);
                }
            });

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

            if (recipeHolder.approveButton != null) {
                recipeHolder.approveButton.setVisibility(onApproveClickListener != null ? View.VISIBLE : View.GONE);
                recipeHolder.approveButton.setOnClickListener(v -> {
                    if (onApproveClickListener != null) {
                        onApproveClickListener.onApproveClick(recipe);
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return recipeList.size() + (isLoading ? 1 : 0);
    }

    private void handleLike(Recipe recipe, RecipeViewHolder holder) {
        FirebaseUser user = mAuth.getCurrentUser();
        String recipeIdStr = String.valueOf(recipe.getId());
        DocumentReference recipeRef = db.collection("recipes").document(recipe.getUserId()); // Use userId for document ID
        DocumentReference userRef = db.collection("users").document(user.getUid());

        boolean isLiked = likedRecipeIds.contains(recipeIdStr);
        if (isLiked) {
            userRef.update("likedRecipes", FieldValue.arrayRemove(recipeIdStr))
                    .addOnSuccessListener(aVoid -> {
                        likedRecipeIds.remove(recipeIdStr);
                        recipe.setLikes(recipe.getLikes() - 1);
                        recipeRef.update("likes", FieldValue.increment(-1));
                        holder.likesCount.setText(String.valueOf(recipe.getLikes()));
                        holder.likeButton.setImageResource(R.drawable.ic_heart_outline);
                        notifyItemChanged(holder.getAdapterPosition());
                    })
                    .addOnFailureListener(e -> {
                        Log.e("RecipeAdapter", "Failed to unlike recipe", e);
                        Toast.makeText(holder.itemView.getContext(), "Failed to unlike recipe", Toast.LENGTH_SHORT).show();
                    });
        } else {
            userRef.update("likedRecipes", FieldValue.arrayUnion(recipeIdStr))
                    .addOnSuccessListener(aVoid -> {
                        likedRecipeIds.add(recipeIdStr);
                        recipe.setLikes(recipe.getLikes() + 1);
                        recipeRef.update("likes", FieldValue.increment(1));
                        holder.likesCount.setText(String.valueOf(recipe.getLikes()));
                        holder.likeButton.setImageResource(R.drawable.ic_heart_filled);
                        notifyItemChanged(holder.getAdapterPosition());
                    })
                    .addOnFailureListener(e -> {
                        Log.e("RecipeAdapter", "Failed to like recipe", e);
                        Toast.makeText(holder.itemView.getContext(), "Failed to like recipe", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView recipeImage;
        TextView recipeTitle;
        Button viewDetailsButton;
        ImageButton deleteButton;
        ImageButton likeButton;
        TextView likesCount;
        Button approveButton;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            try {
                recipeImage = itemView.findViewById(R.id.recipeImage);
                recipeTitle = itemView.findViewById(R.id.recipeTitle);
                viewDetailsButton = itemView.findViewById(R.id.viewDetailsButton);
                likeButton = itemView.findViewById(R.id.likeButton);
                likesCount = itemView.findViewById(R.id.likesCount);
                deleteButton = itemView.findViewById(R.id.deleteButton); // May be null
                approveButton = itemView.findViewById(R.id.approveButton); // May be null
            } catch (Exception e) {
                Log.e("RecipeViewHolder", "Error initializing views for layout: " + itemView.getResources().getResourceName(itemView.getId()), e);
                throw new RuntimeException("Failed to initialize RecipeViewHolder views", e);
            }
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

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
                    oldRecipe.getLikes() == newRecipe.getLikes() &&
                    (oldRecipe.getImageUrl() != null ? oldRecipe.getImageUrl().equals(newRecipe.getImageUrl()) :
                            newRecipe.getImageUrl() == null);
        }
    }
}