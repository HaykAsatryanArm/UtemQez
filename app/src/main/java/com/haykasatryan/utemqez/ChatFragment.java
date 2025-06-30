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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFragment extends Fragment implements ResponseCallback {

    private FirebaseAuth mAuth;
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private Button closeButton;
    private ChatMessageAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private ChatFutures chatModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        // Note: Set android:windowSoftInputMode="adjustResize" in MainActivity's AndroidManifest.xml entry
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        ImageButton profileButton = view.findViewById(R.id.nav_profile);
        closeButton = view.findViewById(R.id.closeButton);
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatAdapter = new ChatMessageAdapter(messageList);
        chatRecyclerView.setAdapter(chatAdapter);

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
            if (!message.isEmpty()) {
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

        // Remove all asterisks from the text
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