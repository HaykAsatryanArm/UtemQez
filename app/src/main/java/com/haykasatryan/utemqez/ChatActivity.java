package com.haykasatryan.utemqez;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivity extends AppCompatActivity implements ResponseCallback {

    private FirebaseAuth mAuth;
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private Button closeButton;
    private ChatMessageAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private ChatFutures chatModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        // Set keyboard handling mode
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        TextView headerTitle = findViewById(R.id.header_title);
        ImageButton profileButton = findViewById(R.id.nav_profile);
        closeButton = findViewById(R.id.closeButton);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        // Set up RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatAdapter = new ChatMessageAdapter(messageList);
        chatRecyclerView.setAdapter(chatAdapter);

        // Initialize Gemini model
        GeminiPro geminiPro = new GeminiPro();
        chatModel = geminiPro.getModel().startChat();

        // Set up authentication-based UI
        if (mAuth.getCurrentUser() != null) {
            String userName = mAuth.getCurrentUser().getDisplayName() != null ?
                    mAuth.getCurrentUser().getDisplayName() : mAuth.getCurrentUser().getEmail();
            headerTitle.setText("Welcome, " + userName + "!");
        } else {
            headerTitle.setText("Chat with AI");
        }

        // Profile button click listener
        profileButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(ChatActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(ChatActivity.this, ProfileActivity.class));
            }
        });

        // Close button click listener
        closeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Send button click listener
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                messageList.add(new ChatMessage(message, true));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                messageInput.setText("");
                GeminiPro.getResponse(chatModel, message, this);
            }
        });

        // Keyboard visibility listener
        final View rootView = findViewById(R.id.main);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) { // Keyboard is visible
                chatRecyclerView.postDelayed(() -> {
                    if (messageList.size() > 0) {
                        chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                    }
                }, 100);
            }
        });
    }

    @Override
    public void onResponse(String response) {
        runOnUiThread(() -> {
            messageList.add(new ChatMessage(response, false));
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
        });
    }

    @Override
    public void onError(Throwable throwable) {
        runOnUiThread(() -> {
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
    public MessageViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
        View view = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chat_message_item, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        // Format the text if it's an AI message, otherwise set it as plain text
        if (message.isUserMessage()) {
            holder.messageText.setText(message.getText());
        } else {
            SpannableStringBuilder formattedText = formatMessageText(message.getText());
            holder.messageText.setText(formattedText);
        }

        // Adjust alignment and background based on message type
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

    // Helper method to format AI responses with Markdown-like syntax
    private SpannableStringBuilder formatMessageText(String text) {
        SpannableStringBuilder spannable = new SpannableStringBuilder();

        // Split the text into lines
        String[] lines = text.split("\n");
        int currentPosition = 0;

        for (String line : lines) {
            if (currentPosition > 0) {
                // Add a newline between lines
                spannable.append("\n");
                currentPosition++;
            }

            // Check for bullet points (lines starting with "* ")
            if (line.startsWith("* ")) {
                // Remove the "* " prefix and append the rest of the line
                String bulletText = line.substring(2);
                int lineStart = currentPosition;
                spannable.append(bulletText);
                int lineEnd = currentPosition + bulletText.length();
                // Apply BulletSpan to the entire line
                spannable.setSpan(new BulletSpan(15), lineStart, lineEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                currentPosition = lineEnd;
            }
            // Check for numbered lists (lines starting with "1. ", "2. ", etc.)
            else if (line.matches("\\d+\\.\\s+.*")) {
                // Extract the number and the text (e.g., "1. Item" -> "1. " and "Item")
                int dotIndex = line.indexOf(". ");
                String numberText = line.substring(0, dotIndex + 2); // e.g., "1. "
                String itemText = line.substring(dotIndex + 2); // e.g., "Item"

                int lineStart = currentPosition;
                spannable.append(numberText).append(itemText);
                int lineEnd = currentPosition + numberText.length() + itemText.length();
                // Apply LeadingMarginSpan to indent the text after the number
                spannable.setSpan(new LeadingMarginSpan.Standard(30), lineStart, lineEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                currentPosition = lineEnd;
            }
            else {
                // Handle bold (**text**) and italic (_text_) within the line
                String remainingText = line;
                int lastIndex = 0;

                // Process bold and italic in a loop to handle nested or overlapping patterns
                while (lastIndex < remainingText.length()) {
                    // Find the next Markdown pattern (bold or italic)
                    Matcher boldMatcher = Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(remainingText);
                    Matcher italicMatcher = Pattern.compile("_(.+?)_").matcher(remainingText);

                    int nextBoldStart = boldMatcher.find(lastIndex) ? boldMatcher.start() : Integer.MAX_VALUE;
                    int nextItalicStart = italicMatcher.find(lastIndex) ? italicMatcher.start() : Integer.MAX_VALUE;

                    if (nextBoldStart == Integer.MAX_VALUE && nextItalicStart == Integer.MAX_VALUE) {
                        // No more Markdown patterns, append the remaining text
                        String plainText = remainingText.substring(lastIndex);
                        spannable.append(plainText);
                        currentPosition += plainText.length();
                        break;
                    }

                    // Determine which pattern comes first
                    if (nextBoldStart < nextItalicStart) {
                        // Process bold text
                        String beforeBold = remainingText.substring(lastIndex, nextBoldStart);
                        spannable.append(beforeBold);
                        currentPosition += beforeBold.length();

                        String boldText = boldMatcher.group(1);
                        int boldStart = currentPosition;
                        spannable.append(boldText);
                        int boldEnd = currentPosition + boldText.length();
                        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), boldStart, boldEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                        currentPosition = boldEnd;

                        lastIndex = boldMatcher.end();
                    } else {
                        // Process italic text
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
        }

        return spannable;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public MessageViewHolder(android.view.View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }
}