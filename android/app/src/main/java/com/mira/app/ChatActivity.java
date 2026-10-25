package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mira.app.data.ApiCallback;
import com.mira.app.data.MessageRepository;
import com.mira.app.data.model.AssistantResponseResponse;
import com.mira.app.data.model.MessagePageResponse;
import com.mira.app.data.model.MessageResponse;
import com.mira.app.data.model.MessageRole;
import com.mira.app.data.model.ResponseStatus;
import com.mira.app.data.model.RetryResponse;
import com.mira.app.data.model.SendMessageResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityChatBinding;
import com.mira.app.ui.MessageAdapter;
import com.mira.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID =
            "extra_conversation_id";
    public static final String EXTRA_CONVERSATION_TITLE =
            "extra_conversation_title";

    private static final int MAX_ATTEMPTS = 3;

    private ActivityChatBinding binding;
    private MessageRepository repository;
    private MessageAdapter adapter;
    private TokenStore tokenStore;

    private String conversationId;

    private final List<MessageResponse> messages = new ArrayList<>();

    private String nextCursor;
    private boolean loading;
    private boolean sending;

    private String pendingSendKey;
    private String pendingSendContent;

    private String pendingRetryKey;
    private String pendingRetryResponseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        conversationId = getIntent().getStringExtra(
                EXTRA_CONVERSATION_ID
        );

        if (conversationId == null) {
            finish();
            return;
        }

        tokenStore = TokenStore.get(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        repository = new MessageRepository(this);

        setUpToolbar();
        setUpList();
        binding.sendButton.setOnClickListener(
                view -> sendMessage()
        );

        loadMessages(null);
    }

    private void setUpToolbar() {
        setSupportActionBar(binding.chatToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            String title = getIntent().getStringExtra(
                    EXTRA_CONVERSATION_TITLE
            );

            getSupportActionBar().setTitle(
                    title == null || title.isBlank()
                            ? getString(R.string.chat_title)
                            : title
            );
        }
    }

    private void setUpList() {
        adapter = new MessageAdapter(
                response -> retry(response)
        );

        binding.messageList.setLayoutManager(
                new LinearLayoutManager(this)
        );
        binding.messageList.setAdapter(adapter);

        binding.messageList.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy
                    ) {
                        if (dy <= 0
                                || loading
                                || nextCursor == null) {
                            return;
                        }

                        if (!recyclerView.canScrollVertically(1)) {
                            loadMessages(nextCursor);
                        }
                    }
                }
        );
    }

    private void loadMessages(String cursor) {
        if (loading) {
            return;
        }

        loading = true;
        binding.emptyState.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.GONE);
        binding.progress.setVisibility(
                messages.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        if (cursor == null) {
            nextCursor = null;
        }

        repository.list(
                conversationId,
                cursor,
                new ApiCallback<MessagePageResponse>() {
                    @Override
                    public void onSuccess(MessagePageResponse page) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        loading = false;
                        binding.progress.setVisibility(View.GONE);

                        if (cursor == null) {
                            messages.clear();
                        }

                        if (page.getItems() != null) {
                            messages.addAll(page.getItems());
                        }

                        nextCursor = page.getNextCursor();

                        adapter.submitList(
                                new ArrayList<>(messages)
                        );
                        updateEmptyState();

                        if (cursor == null) {
                            scrollToBottom();
                        }
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        loading = false;
                        binding.progress.setVisibility(View.GONE);

                        if (statusCode == 401) {
                            openLogin();
                            return;
                        }

                        showError(message);
                        updateEmptyState();
                    }
                }
        );
    }

    private void sendMessage() {
        if (sending) {
            return;
        }

        String content = textOf(binding.messageInput);

        if (TextUtils.isEmpty(content)) {
            return;
        }

        String idempotencyKey;

        if (pendingSendKey != null
                && content.equals(pendingSendContent)) {
            idempotencyKey = pendingSendKey;
        } else {
            idempotencyKey = UUID.randomUUID().toString();
            pendingSendKey = idempotencyKey;
            pendingSendContent = content;
        }

        binding.errorText.setVisibility(View.GONE);
        setSending(true);

        repository.send(
                conversationId,
                content,
                idempotencyKey,
                new ApiCallback<SendMessageResponse>() {
                    @Override
                    public void onSuccess(SendMessageResponse response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSending(false);
                        pendingSendKey = null;
                        pendingSendContent = null;

                        binding.messageInput.setText("");
                        hideKeyboard();

                        if (response.getUserMessage() != null) {
                            messages.add(response.getUserMessage());
                        }

                        AssistantResponseResponse assistant =
                                response.getAssistantResponse();

                        if (assistant != null
                                && assistant.getStatus()
                                == ResponseStatus.COMPLETED
                                && assistant.getContent() != null) {
                            messages.add(
                                    buildAssistantMessage(assistant)
                            );
                        }

                        adapter.submitList(
                                new ArrayList<>(messages)
                        );
                        updateEmptyState();
                        scrollToBottom();
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSending(false);

                        if (statusCode == 401) {
                            openLogin();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void retry(MessageResponse.ResponseMetadata response) {
        if (sending || response == null) {
            return;
        }

        if (response.getAttemptCount() >= MAX_ATTEMPTS) {
            showError("Retry limit reached.");
            return;
        }

        String idempotencyKey;

        if (pendingRetryKey != null
                && response.getId().equals(
                        pendingRetryResponseId
                )) {
            idempotencyKey = pendingRetryKey;
        } else {
            idempotencyKey = UUID.randomUUID().toString();
            pendingRetryKey = idempotencyKey;
            pendingRetryResponseId = response.getId();
        }

        binding.errorText.setVisibility(View.GONE);
        setSending(true);

        repository.retry(
                conversationId,
                response.getId(),
                idempotencyKey,
                new ApiCallback<RetryResponse>() {
                    @Override
                    public void onSuccess(RetryResponse result) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSending(false);
                        pendingRetryKey = null;
                        pendingRetryResponseId = null;

                        applyRetryResult(
                                response.getId(),
                                result.getAssistantResponse()
                        );
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSending(false);

                        if (statusCode == 401) {
                            openLogin();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void applyRetryResult(
            String responseId,
            AssistantResponseResponse assistant
    ) {
        if (assistant == null) {
            showError("Retry did not return a response.");
            return;
        }

        int userIndex = -1;

        for (int index = 0; index < messages.size(); index++) {
            MessageResponse message = messages.get(index);
            MessageResponse.ResponseMetadata metadata =
                    message.getResponse();

            if (metadata != null
                    && responseId.equals(metadata.getId())) {
                userIndex = index;
                break;
            }
        }

        if (userIndex < 0) {
            loadMessages(null);
            return;
        }

        MessageResponse userMessage = messages.get(userIndex);
        MessageResponse.ResponseMetadata previous =
                userMessage.getResponse();

        int attemptCount = previous == null
                ? 1
                : previous.getAttemptCount() + 1;

        MessageResponse.ResponseMetadata updated =
                new MessageResponse.ResponseMetadata(
                        responseId,
                        assistant.getStatus(),
                        attemptCount
                );

        messages.set(
                userIndex,
                new MessageResponse(
                        userMessage.getId(),
                        userMessage.getRole(),
                        userMessage.getContent(),
                        userMessage.getCreatedAt(),
                        updated
                )
        );

        if (assistant.getStatus() == ResponseStatus.COMPLETED
                && assistant.getContent() != null) {
            messages.add(
                    userIndex + 1,
                    buildAssistantMessage(assistant)
            );
        }

        adapter.submitList(new ArrayList<>(messages));
        updateEmptyState();
        scrollToPosition(userIndex + 1);
    }

    private MessageResponse buildAssistantMessage(
            AssistantResponseResponse assistant
    ) {
        return new MessageResponse(
                "assistant-" + assistant.getId(),
                MessageRole.ASSISTANT,
                assistant.getContent(),
                assistant.getCreatedAt(),
                null
        );
    }

    private void setSending(boolean value) {
        sending = value;
        binding.sendButton.setEnabled(!value);
    }

    private void updateEmptyState() {
        binding.emptyState.setVisibility(
                messages.isEmpty() && !loading
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void scrollToBottom() {
        binding.messageList.post(() -> {
            int count = adapter.getItemCount();

            if (count > 0) {
                binding.messageList.scrollToPosition(count - 1);
            }
        });
    }

    private void scrollToPosition(int position) {
        binding.messageList.post(() ->
                binding.messageList.scrollToPosition(position)
        );
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    binding.messageInput.getWindowToken(),
                    0
            );
        }
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private String textOf(TextView view) {
        return view.getText() == null
                ? ""
                : view.getText().toString().trim();
    }

    private void openLogin() {
        tokenStore.clear();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}