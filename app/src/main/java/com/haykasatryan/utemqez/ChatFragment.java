package com.haykasatryan.utemqez;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFragment extends Fragment implements ResponseCallback {

    private FirebaseAuth mAuth;
    private RecyclerView chatRecyclerView;
    private RecyclerView suggestionsRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private Button closeButton;
    private ChatMessageAdapter chatAdapter;
    private SuggestionsAdapter suggestionsAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<Suggestion> suggestionList = new ArrayList<>();
    private ChatFutures chatModel;
    private boolean isFirstMessageSent = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        List<String> suggestionPool = new ArrayList<>();
        suggestionPool.add("What's a quick dinner recipe?");
        suggestionPool.add("Suggest a vegan dessert.");
        suggestionPool.add("How to make a perfect omelette?");
        suggestionPool.add("What's a healthy breakfast idea?");
        suggestionPool.add("Can you recommend a low-carb dinner?");
        suggestionPool.add("How to bake a chocolate cake?");
        suggestionPool.add("What's a good vegetarian lunch?");
        suggestionPool.add("Suggest a gluten-free snack.");
        suggestionPool.add("How to make homemade pizza?");
        suggestionPool.add("What's a quick pasta recipe?");
        suggestionPool.add("Suggest a refreshing summer drink.");
        suggestionPool.add("How to cook a steak perfectly?");
        suggestionPool.add("What's a traditional Italian dish?");
        suggestionPool.add("How to make a creamy soup?");
        suggestionPool.add("Suggest a spicy Mexican recipe.");
        suggestionPool.add("What's a good side dish for chicken?");
        suggestionPool.add("How to prepare sushi at home?");
        suggestionPool.add("Suggest a dairy-free breakfast.");
        suggestionPool.add("What's a simple salad dressing?");
        suggestionPool.add("How to make fluffy pancakes?");
        suggestionPool.add("Suggest a quick appetizer for a party.");
        suggestionPool.add("What's a healthy smoothie recipe?");
        suggestionPool.add("How to cook quinoa perfectly?");
        suggestionPool.add("Suggest a budget-friendly meal.");
        suggestionPool.add("What's a classic French dessert?");
        suggestionPool.add("How to make homemade bread?");
        suggestionPool.add("Suggest a low-sugar dessert.");
        suggestionPool.add("What's a good recipe for fish?");
        suggestionPool.add("How to make a vegetarian curry?");
        suggestionPool.add("Suggest a kid-friendly meal.");
        suggestionPool.add("What's a quick stir-fry recipe?");
        suggestionPool.add("How to make a perfect risotto?");
        suggestionPool.add("Suggest a high-protein snack.");
        suggestionPool.add("What's a traditional Indian dish?");
        suggestionPool.add("How to make a fruit tart?");
        suggestionPool.add("Suggest a warm winter drink.");
        suggestionPool.add("What's a good recipe for shrimp?");
        suggestionPool.add("How to make a vegan pizza?");
        suggestionPool.add("Suggest a healthy lunch idea.");
        suggestionPool.add("What's a simple BBQ sauce recipe?");
        suggestionPool.add("How to cook lentils properly?");
        suggestionPool.add("Suggest a festive holiday dessert.");
        suggestionPool.add("What's a quick breakfast for busy mornings?");
        suggestionPool.add("How to make a creamy pasta sauce?");
        suggestionPool.add("Suggest a savory pie recipe.");
        suggestionPool.add("What's a good recipe for tacos?");
        suggestionPool.add("How to make homemade ice cream?");
        suggestionPool.add("Suggest a plant-based dinner.");
        suggestionPool.add("What's a simple soup for beginners?");
        suggestionPool.add("How to make a perfect cheesecake?");

        Random random = new Random();
        Collections.shuffle(suggestionPool, random);
        int numberOfSuggestions = Math.min(3, suggestionPool.size());
        for (int i = 0; i < numberOfSuggestions; i++) {
            suggestionList.add(new Suggestion(suggestionPool.get(i)));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        ImageButton profileButton = view.findViewById(R.id.nav_profile);
        closeButton = view.findViewById(R.id.closeButton);
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        suggestionsRecyclerView = view.findViewById(R.id.suggestionsRecyclerView);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);

        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(requireContext());
        chatLayoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(chatLayoutManager);
        chatAdapter = new ChatMessageAdapter(messageList);
        chatRecyclerView.setAdapter(chatAdapter);

        LinearLayoutManager suggestionsLayoutManager = new LinearLayoutManager(requireContext());
        suggestionsRecyclerView.setLayoutManager(suggestionsLayoutManager);
        suggestionsAdapter = new SuggestionsAdapter(suggestionList, (suggestionText, position) -> {
            if (!isFirstMessageSent) {
                messageList.add(new ChatMessage(suggestionText, true));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                GeminiPro.getResponse(chatModel, suggestionText, this);
                isFirstMessageSent = true;
                suggestionList.clear();
                suggestionsAdapter.notifyDataSetChanged();
                suggestionsRecyclerView.setVisibility(View.GONE);
            }
        });
        suggestionsRecyclerView.setAdapter(suggestionsAdapter);

        GeminiPro geminiPro = new GeminiPro();
        chatModel = geminiPro.getModel().startChat();

        profileButton.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            if (mAuth.getCurrentUser() == null) {
                navController.navigate(R.id.action_chatFragment_to_loginActivity);
            } else {
                navController.navigate(R.id.action_chatFragment_to_profileFragment);
            }
        });

        closeButton.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_chatFragment_to_homeFragment));

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty() && !isFirstMessageSent) {
                messageList.add(new ChatMessage(message, true));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                messageInput.setText("");
                GeminiPro.getResponse(chatModel, message, this);
                isFirstMessageSent = true;
                suggestionList.clear();
                suggestionsAdapter.notifyDataSetChanged();
                suggestionsRecyclerView.setVisibility(View.GONE);
            } else if (!message.isEmpty()) {
                messageList.add(new ChatMessage(message, true));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                messageInput.setText("");
                GeminiPro.getResponse(chatModel, message, this);
            }
        });

        final View rootView = view.findViewById(R.id.main);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                chatRecyclerView.postDelayed(() -> {
                    if (messageList.size() > 0) {
                        chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                    }
                }, 100);
            }
        });

        return view;
    }

    @Override
    public void onResponse(String response) {
        requireActivity().runOnUiThread(() -> {
            messageList.add(new ChatMessage(response, false));
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
        });
    }

    @Override
    public void onError(Throwable throwable) {
        requireActivity().runOnUiThread(() -> {
            messageList.add(new ChatMessage("Error: " + throwable.getMessage(), false));
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
        });
    }
}

class ChatMessage {
    private String text;
    private boolean isUserMessage;

    public ChatMessage(String text, boolean isUserMessage) {
        this.text = text;
        this.isUserMessage = isUserMessage;
    }

    public String getText() {
        return text;
    }

    public boolean isUserMessage() {
        return isUserMessage;
    }
}

class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> messageList;

    public ChatMessageAdapter(List<ChatMessage> messageList) {
        this.messageList = messageList;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chat_message_item, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if (message.isUserMessage()) {
            holder.messageText.setText(message.getText());
        } else {
            SpannableStringBuilder formattedText = formatMessageText(message.getText());
            holder.messageText.setText(formattedText);
        }

        if (message.isUserMessage()) {
            holder.messageText.setBackgroundResource(R.drawable.chat_message_background_user);
            holder.messageText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.white));
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageText.getLayoutParams();
            params.startToStart = ConstraintLayout.LayoutParams.UNSET;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            holder.messageText.setLayoutParams(params);
        } else {
            holder.messageText.setBackgroundResource(R.drawable.chat_message_background);
            holder.messageText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.blackot));
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageText.getLayoutParams();
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            holder.messageText.setLayoutParams(params);
        }
    }

    private SpannableStringBuilder formatMessageText(String text) {
        SpannableStringBuilder spannable = new SpannableStringBuilder();

        text = text.replaceAll("\\*", "");

        String[] lines = text.split("\n");
        int currentPosition = 0;

        for (String line : lines) {
            if (currentPosition > 0) {
                spannable.append("\n");
                currentPosition++;
            }

            if (line.matches("\\d+\\.\\s+.*")) {
                int dotIndex = line.indexOf(". ");
                String numberText = line.substring(0, dotIndex + 2);
                String itemText = line.substring(dotIndex + 2);

                int lineStart = currentPosition;
                spannable.append(numberText).append(itemText);
                int lineEnd = currentPosition + numberText.length() + itemText.length();
                spannable.setSpan(new LeadingMarginSpan.Standard(30), lineStart, lineEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                currentPosition = lineEnd;
            } else {
                String remainingText = line;
                int lastIndex = 0;

                while (lastIndex < remainingText.length()) {
                    Matcher italicMatcher = Pattern.compile("_(.+?)_").matcher(remainingText);

                    int nextItalicStart = italicMatcher.find(lastIndex) ? italicMatcher.start() : Integer.MAX_VALUE;

                    if (nextItalicStart == Integer.MAX_VALUE) {
                        String plainText = remainingText.substring(lastIndex);
                        spannable.append(plainText);
                        currentPosition += plainText.length();
                        break;
                    }

                    String beforeItalic = remainingText.substring(lastIndex, nextItalicStart);
                    spannable.append(beforeItalic);
                    currentPosition += beforeItalic.length();

                    String italicText = italicMatcher.group(1);
                    int italicStart = currentPosition;
                    spannable.append(italicText);
                    int italicEnd = currentPosition + italicText.length();
                    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), italicStart, italicEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    currentPosition = italicEnd;

                    lastIndex = italicMatcher.end();
                }
            }
        }

        return spannable;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }
}