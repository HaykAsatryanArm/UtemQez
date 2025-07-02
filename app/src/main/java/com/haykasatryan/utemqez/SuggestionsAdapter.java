package com.haykasatryan.utemqez;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder> {

    private final List<Suggestion> suggestionList;
    private final OnSuggestionClickListener clickListener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(String suggestionText, int position);
    }

    public SuggestionsAdapter(List<Suggestion> suggestionList, OnSuggestionClickListener clickListener) {
        this.suggestionList = suggestionList;
        this.clickListener = clickListener;
    }

    @Override
    public SuggestionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.suggestion_item, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SuggestionViewHolder holder, int position) {
        Suggestion suggestion = suggestionList.get(position);
        holder.suggestionText.setText(suggestion.getText());
        holder.itemView.setOnClickListener(v -> clickListener.onSuggestionClick(suggestion.getText(), position));
    }

    @Override
    public int getItemCount() {
        return suggestionList.size();
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        TextView suggestionText;

        public SuggestionViewHolder(View itemView) {
            super(itemView);
            suggestionText = itemView.findViewById(R.id.suggestionText);
        }
    }
}