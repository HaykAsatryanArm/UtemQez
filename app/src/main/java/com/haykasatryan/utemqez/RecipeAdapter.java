package com.haykasatryan.utemqez;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "RecipeAdapter";
    private static final int TYPE_RECIPE = 0;
    private static final int TYPE_LOADING = 1;
    private List<Recipe> recipeList;
    private final int layoutResId;
    private OnDeleteClickListener onDeleteClickListener;
    private OnApproveClickListener onApproveClickListener;
    private boolean isLoading = false;
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final List<String> likedRecipeIds = Collections.synchronizedList(new ArrayList<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnDeleteClickListener {
        void onDeleteClick(Recipe recipe);
    }

    public interface OnApproveClickListener {
        void onApproveClick(Recipe recipe);
    }

    public RecipeAdapter(List<Recipe> recipeList, int layoutResId) {
        this.recipeList = recipeList != null ? new ArrayList<>(recipeList) : new ArrayList<>();
        this.layoutResId = layoutResId;
        this.mAuth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        setHasStableIds(true);
        fetchLikedRecipes();
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_RECIPE && position < recipeList.size()) {
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
        newListSafe.removeIf(recipe -> recipe == null);
        mainHandler.post(() -> {
            Log.d(TAG, "Updating list with " + newListSafe.size() + " recipes");
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RecipeDiffCallback(recipeList, newListSafe));
            recipeList.clear();
            recipeList.addAll(newListSafe);
            diffResult.dispatchUpdatesTo(this);
            notifyDataSetChanged();
        });
    }

    public void setLoading(boolean loading) {
        mainHandler.post(() -> {
            if (isLoading == loading) return;
            isLoading = loading;
            notifyItemChanged(recipeList.size());
        });
    }

    private void fetchLikedRecipes() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        synchronized (likedRecipeIds) {
                            likedRecipeIds.clear();
                            List<String> fetchedIds = (List<String>) documentSnapshot.get("likedRecipes");
                            if (fetchedIds != null) {
                                likedRecipeIds.addAll(fetchedIds);
                            }
                            Log.d(TAG, "Fetched liked recipe IDs for user " + userId + ": " + likedRecipeIds);
                            mainHandler.post(() -> notifyItemRangeChanged(0, recipeList.size()));
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching liked recipes for user " + userId + ": " + e.getMessage(), e));
        } else {
            Log.w(TAG, "No user logged in, skipping liked recipes fetch");
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < recipeList.size() ? TYPE_RECIPE : TYPE_LOADING;
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
            if (position >= recipeList.size()) {
                Log.e(TAG, "Invalid position for RecipeViewHolder: " + position);
                return;
            }
            Recipe recipe = recipeList.get(position);
            RecipeViewHolder recipeHolder = (RecipeViewHolder) holder;
            Log.d(TAG, "Binding recipe ID: " + recipe.getId() + ", title: " + recipe.getTitle() + ", position: " + position + ", layout: " + layoutResId);

            if (recipeHolder.recipeTitle != null) {
                recipeHolder.recipeTitle.setText(recipe.getTitle() != null ? recipe.getTitle() : "Untitled");
            } else {
                Log.w(TAG, "recipeTitle is null for position: " + position);
            }
            if (recipeHolder.recipeTime != null) {
                recipeHolder.recipeTime.setText(recipe.getReadyInMinutes() > 0 ? recipe.getReadyInMinutes() + " min" : "N/A");
            } else {
                Log.w(TAG, "recipeTime is null for position: " + position);
            }
            if (recipeHolder.likesCount != null) {
                recipeHolder.likesCount.setText(String.valueOf(recipe.getLikes()));
            } else {
                Log.w(TAG, "likesCount is null for position: " + position);
            }

            if (recipeHolder.recipeImage != null) {
                String imageUrl = recipe.getImageUrl();
                RequestOptions options = new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(120, 100)
                        .placeholder(R.drawable.recipe_image)
                        .error(R.drawable.recipe_image);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imageUrl = imageUrl.replace("http://", "https://");
                    Glide.with(holder.itemView.getContext())
                            .load(imageUrl)
                            .apply(options)
                            .thumbnail(0.25f)
                            .into(recipeHolder.recipeImage);
                } else {
                    Glide.with(holder.itemView.getContext())
                            .load(R.drawable.recipe_image)
                            .apply(options)
                            .into(recipeHolder.recipeImage);
                }
            } else {
                Log.w(TAG, "recipeImage is null for position: " + position);
            }

            String recipeIdStr = String.valueOf(recipe.getId());
            boolean isLiked = likedRecipeIds.contains(recipeIdStr);
            if (recipeHolder.likeButton != null) {
                recipeHolder.likeButton.setImageResource(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                recipeHolder.likeButton.setOnClickListener(v -> handleLike(recipe, recipeHolder));
            } else {
                Log.w(TAG, "likeButton is null for position: " + position);
            }

            if (recipeHolder.viewDetailsButton != null) {
                recipeHolder.viewDetailsButton.setOnClickListener(v -> {
                    Intent intent = new Intent(holder.itemView.getContext(), RecipeDetailActivity.class);
                    intent.putExtra("recipe", recipe);
                    holder.itemView.getContext().startActivity(intent);
                });
            } else {
                Log.w(TAG, "viewDetailsButton is null for position: " + position);
            }

            FirebaseUser user = mAuth.getCurrentUser();
            String userId = user != null ? user.getUid() : "";
            if (recipeHolder.deleteButton != null) {
                recipeHolder.deleteButton.setVisibility(onDeleteClickListener != null && userId.equals(recipe.getUserId()) ? View.VISIBLE : View.GONE);
                recipeHolder.deleteButton.setOnClickListener(v -> {
                    if (onDeleteClickListener != null) {
                        onDeleteClickListener.onDeleteClick(recipe);
                    }
                });
            } else {
                Log.w(TAG, "deleteButton is null for position: " + position);
            }

            if (recipeHolder.approveButton != null) {
                recipeHolder.approveButton.setVisibility(onApproveClickListener != null && !recipe.isApproved() ? View.VISIBLE : View.GONE);
                recipeHolder.approveButton.setOnClickListener(v -> {
                    if (onApproveClickListener != null) {
                        onApproveClickListener.onApproveClick(recipe);
                    }
                });
            } else {
                Log.w(TAG, "approveButton is null for position: " + position);
            }
        }
    }

    @Override
    public int getItemCount() {
        int count = recipeList.size() + (isLoading ? 1 : 0);
        Log.d(TAG, "Item count: " + count);
        return count;
    }

    private void handleLike(Recipe recipe, RecipeViewHolder holder) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Context context = holder.itemView.getContext();
            context.startActivity(new Intent(context, LoginActivity.class));
            Toast.makeText(context, "Please log in to like recipes", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = user.getUid();
        String recipeIdStr = String.valueOf(recipe.getId());
        DocumentReference recipeRef = db.collection("recipes").document(recipeIdStr);
        DocumentReference userRef = db.collection("users").document(userId);

        synchronized (likedRecipeIds) {
            boolean isLiked = likedRecipeIds.contains(recipeIdStr);
            if (isLiked) {
                userRef.update("likedRecipes", FieldValue.arrayRemove(recipeIdStr))
                        .addOnSuccessListener(aVoid -> mainHandler.post(() -> {
                            synchronized (likedRecipeIds) {
                                likedRecipeIds.remove(recipeIdStr);
                                recipe.setLikes(recipe.getLikes() - 1);
                                recipeRef.update("likes", FieldValue.increment(-1));
                                if (holder.likesCount != null) {
                                    holder.likesCount.setText(String.valueOf(recipe.getLikes()));
                                }
                                if (holder.likeButton != null) {
                                    holder.likeButton.setImageResource(R.drawable.ic_heart_outline);
                                }
                                notifyItemChanged(holder.getAdapterPosition());
                            }
                        }))
                        .addOnFailureListener(e -> mainHandler.post(() -> {
                            Log.e(TAG, "Failed to unlike recipe for user " + userId + ": " + e.getMessage(), e);
                            Toast.makeText(holder.itemView.getContext(), "Failed to unlike recipe", Toast.LENGTH_SHORT).show();
                        }));
            } else {
                userRef.update("likedRecipes", FieldValue.arrayUnion(recipeIdStr))
                        .addOnSuccessListener(aVoid -> mainHandler.post(() -> {
                            synchronized (likedRecipeIds) {
                                likedRecipeIds.add(recipeIdStr);
                                recipe.setLikes(recipe.getLikes() + 1);
                                recipeRef.update("likes", FieldValue.increment(1));
                                if (holder.likesCount != null) {
                                    holder.likesCount.setText(String.valueOf(recipe.getLikes()));
                                }
                                if (holder.likeButton != null) {
                                    holder.likeButton.setImageResource(R.drawable.ic_heart_filled);
                                }
                                notifyItemChanged(holder.getAdapterPosition());
                            }
                        }))
                        .addOnFailureListener(e -> mainHandler.post(() -> {
                            Log.e(TAG, "Failed to like recipe for user " + userId + ": " + e.getMessage(), e);
                            Toast.makeText(holder.itemView.getContext(), "Failed to like recipe", Toast.LENGTH_SHORT).show();
                        }));
            }
        }
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView recipeImage;
        TextView recipeTitle;
        TextView recipeTime;
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
                recipeTime = itemView.findViewById(R.id.recipeTime);
                viewDetailsButton = itemView.findViewById(R.id.viewDetailsButton);
                likeButton = itemView.findViewById(R.id.likeButton);
                likesCount = itemView.findViewById(R.id.likesCount);
                deleteButton = itemView.findViewById(R.id.deleteButton);
                approveButton = itemView.findViewById(R.id.approveButton);
                Log.d(TAG, "RecipeViewHolder initialized - recipeImage: " + (recipeImage != null) +
                        ", recipeTitle: " + (recipeTitle != null) +
                        ", recipeTime: " + (recipeTime != null) +
                        ", viewDetailsButton: " + (viewDetailsButton != null) +
                        ", likeButton: " + (likeButton != null) +
                        ", likesCount: " + (likesCount != null) +
                        ", deleteButton: " + (deleteButton != null) +
                        ", approveButton: " + (approveButton != null));
            } catch (Exception e) {
                Log.e(TAG, "Error initializing views for layout: " + itemView.getResources().getResourceName(itemView.getId()), e);
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
            this.oldList = oldList != null ? new ArrayList<>(oldList) : new ArrayList<>();
            this.newList = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
            this.oldList.removeIf(recipe -> recipe == null);
            this.newList.removeIf(recipe -> recipe == null);
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
                    oldRecipe.isApproved() == newRecipe.isApproved() &&
                    oldRecipe.getReadyInMinutes() == newRecipe.getReadyInMinutes() &&
                    (oldRecipe.getImageUrl() != null ? oldRecipe.getImageUrl().equals(newRecipe.getImageUrl()) : newRecipe.getImageUrl() == null);
        }
    }
}