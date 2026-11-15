package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mira.app.data.ApiCallback;
import com.mira.app.data.ConversationRepository;
import com.mira.app.data.MessageRepository;
import com.mira.app.data.model.AssistantResponseResponse;
import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.model.MessagePageResponse;
import com.mira.app.data.model.MessageResponse;
import com.mira.app.data.model.MessageRole;
import com.mira.app.data.model.ResponseStatus;
import com.mira.app.data.model.RetryResponse;
import com.mira.app.data.model.SendMessageResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityChatBinding;
import com.mira.app.ui.ConversationAdapter;
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
    private ConversationRepository conversationRepository;
    private MessageAdapter adapter;
    private ConversationAdapter drawerAdapter;
    private TokenStore tokenStore;

    /**
     * Null means "new chat": the conversation is created lazily on the first
     * send, so simply opening the app never litters history with empty threads.
     */
    private String conversationId;

    private final List<MessageResponse> messages = new ArrayList<>();
    private final List<ConversationResponse> recentConversations =
            new ArrayList<>();

    private String nextCursor;
    private boolean loading;
    private boolean sending;

    private String pendingSendKey;
    private String pendingSendContent;
    private String optimisticPromptId;

    private String pendingRetryKey;
    private String pendingRetryResponseId;

    private int effortIndex = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        /*
         * DrawerLayout consumes window insets itself and ignores padding set on
         * the drawer, so the bars are applied to the wrapping container.
         */
        WindowInsetsHelper.apply(binding.rootContainer);

        conversationId = getIntent().getStringExtra(
                EXTRA_CONVERSATION_ID
        );

        tokenStore = TokenStore.get(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        repository = new MessageRepository(this);
        conversationRepository = new ConversationRepository(this);

        setUpToolbar();
        setUpDrawer();
        setUpBackHandling();
        setUpEffort();
        setUpList();
        setUpGreeting();
        setUpSuggestions();
        binding.sendButton.setOnClickListener(
                view -> sendMessage()
        );

        if (conversationId == null) {
            updateEmptyState();
        } else {
            loadMessages(null);
        }
    }

    private void setUpToolbar() {
        binding.chatToolbar.setNavigationIcon(R.drawable.ic_menu);
        binding.chatToolbar.setNavigationContentDescription(
                R.string.open_navigation
        );
        binding.chatToolbar.setNavigationOnClickListener(
                view -> binding.drawerLayout.openDrawer(
                        GravityCompat.START
                )
        );

        applyConversationTitle(
                getIntent().getStringExtra(EXTRA_CONVERSATION_TITLE)
        );
    }

    private void applyConversationTitle(String title) {
        binding.chatToolbar.setTitle(
                title == null || title.isBlank()
                        ? getString(
                                conversationId == null
                                        ? R.string.new_chat
                                        : R.string.chat_title
                        )
                        : title
        );
    }

    private void setUpDrawer() {
        drawerAdapter = new ConversationAdapter(
                new ConversationAdapter.Listener() {
                    @Override
                    public void onOpen(ConversationResponse conversation) {
                        openConversation(conversation);
                    }

                    @Override
                    public void onDelete(ConversationResponse conversation) {
                        confirmDeleteConversation(conversation);
                    }
                }
        );

        binding.drawerConversations.setLayoutManager(
                new LinearLayoutManager(this)
        );
        binding.drawerConversations.setAdapter(drawerAdapter);

        binding.drawerEmail.setText(tokenStore.email());

        binding.newChatButton.setOnClickListener(
                view -> startNewChat()
        );

        binding.drawerSettings.setOnClickListener(
                view -> startActivity(
                        new Intent(this, SettingsActivity.class)
                )
        );

        binding.drawerClose.setOnClickListener(
                view -> closeDrawer()
        );

        binding.drawerLayout.addDrawerListener(
                new DrawerLayout.SimpleDrawerListener() {
                    @Override
                    public void onDrawerOpened(@NonNull View drawerView) {
                        loadRecentConversations();
                    }
                }
        );
    }

    private void loadRecentConversations() {
        conversationRepository.list(
                null,
                new ApiCallback<ConversationPageResponse>() {
                    @Override
                    public void onSuccess(ConversationPageResponse page) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        recentConversations.clear();

                        if (page.getItems() != null) {
                            recentConversations.addAll(page.getItems());
                        }

                        drawerAdapter.submitList(
                                new ArrayList<>(recentConversations)
                        );
                        binding.drawerEmpty.setVisibility(
                                recentConversations.isEmpty()
                                        ? View.VISIBLE
                                        : View.GONE
                        );
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (statusCode == 401) {
                            openLogin();
                        }
                    }
                }
        );
    }

    private void openConversation(ConversationResponse conversation) {
        conversationId = conversation.getId();
        applyConversationTitle(conversation.getTitle());
        closeDrawer();

        messages.clear();
        nextCursor = null;
        renderMessages(false);
        updateEmptyState();
        binding.errorText.setVisibility(View.GONE);

        loadMessages(null);
    }

    private void startNewChat() {
        conversationId = null;
        applyConversationTitle(null);
        closeDrawer();

        messages.clear();
        nextCursor = null;
        binding.messageInput.setText("");
        binding.errorText.setVisibility(View.GONE);
        renderMessages(false);
        updateEmptyState();
    }

    private void closeDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void confirmDeleteConversation(ConversationResponse conversation) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_conversation)
                .setMessage(R.string.delete_conversation_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.delete,
                        (dialog, which) -> deleteConversation(conversation)
                )
                .show();
    }

    private void deleteConversation(ConversationResponse conversation) {
        conversationRepository.delete(
                conversation.getId(),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (conversation.getId().equals(conversationId)) {
                            startNewChat();
                        }

                        loadRecentConversations();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (statusCode == 401) {
                            openLogin();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void setUpGreeting() {
        String name = displayName(tokenStore.email());

        binding.greetingText.setText(
                name.isEmpty()
                        ? getString(R.string.greeting_default)
                        : getString(R.string.greeting_named, name)
        );
    }

    private void setUpSuggestions() {
        binding.suggestionExplain.setOnClickListener(
                view -> sendSuggested(R.string.suggestion_explain_prompt)
        );
        binding.suggestionSummarize.setOnClickListener(
                view -> sendSuggested(R.string.suggestion_summarize_prompt)
        );
        binding.suggestionDebug.setOnClickListener(
                view -> sendSuggested(R.string.suggestion_debug_prompt)
        );
    }

    private void sendSuggested(int promptResource) {
        if (sending) {
            return;
        }

        binding.messageInput.setText(getString(promptResource));

        if (binding.messageInput.getText() != null) {
            binding.messageInput.setSelection(
                    binding.messageInput.getText().length()
            );
        }

        hideKeyboard();
        sendMessage();
    }

    /**
     * Uses the first segment of the email local part so the greeting reads like
     * a name ("bryan.tran@..." becomes "Bryan") without pretending to know it.
     */
    private static String displayName(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }

        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String[] segments = local.split("[._\\-]+");

        for (String segment : segments) {
            if (!segment.isEmpty()) {
                return Character.toUpperCase(segment.charAt(0))
                        + segment.substring(1);
            }
        }

        return "";
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
        if (loading || conversationId == null) {
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

                        renderMessages(cursor == null);
                        updateEmptyState();
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

        if (conversationId == null) {
            createConversationThenSend(content);
            return;
        }

        addOptimisticPrompt(content);
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

                        removeOptimisticPrompt();

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

                        renderMessages(true);
                        updateEmptyState();
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

        renderMessagesAt(userIndex + 1);
        updateEmptyState();
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
        binding.messageInput.setEnabled(!value);

        if (value) {
            showThinking();
        } else {
            hideThinking();
            removeOptimisticPrompt();
        }
    }

    /**
     * AI effort is presentation only: it is remembered locally so the control
     * behaves like a real preference, but generation settings stay fixed.
     */
    private void setUpEffort() {
        effortIndex = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getInt(MiraApplication.PREF_EFFORT, 1);

        renderEffort();

        binding.effortButton.setOnClickListener(
                view -> showEffortDialog()
        );
    }

    private void renderEffort() {
        binding.effortButton.setText(
                getString(
                        R.string.effort_button,
                        getString(effortOption(effortIndex))
                )
        );
    }

    private void showEffortDialog() {
        CharSequence[] options = {
                getString(R.string.effort_option_low),
                getString(R.string.effort_option_medium),
                getString(R.string.effort_option_high)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.effort_dialog_title)
                .setSingleChoiceItems(options, effortIndex, (dialog, which) -> {
                    effortIndex = which;

                    getSharedPreferences(
                            MiraApplication.PREFS,
                            MODE_PRIVATE
                    ).edit()
                            .putInt(MiraApplication.PREF_EFFORT, which)
                            .apply();

                    renderEffort();
                    dialog.dismiss();
                })
                .show();
    }

    private int effortOption(int index) {
        switch (index) {
            case 0:
                return R.string.effort_option_low;
            case 2:
                return R.string.effort_option_high;
            default:
                return R.string.effort_option_medium;
        }
    }

    /**
     * Shows the prompt immediately so a slow provider never leaves the thread
     * looking like the message was swallowed. Replaced by the server message on
     * success and removed on failure.
     */
    private void addOptimisticPrompt(String content) {
        String localId = "local-prompt-" + UUID.randomUUID();

        optimisticPromptId = localId;
        messages.add(
                new MessageResponse(
                        localId,
                        MessageRole.USER,
                        content,
                        null,
                        null
                )
        );

        renderMessages(true);
        updateEmptyState();
    }

    private void removeOptimisticPrompt() {
        if (optimisticPromptId == null) {
            return;
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            if (optimisticPromptId.equals(messages.get(index).getId())) {
                messages.remove(index);
                break;
            }
        }

        optimisticPromptId = null;
    }

    /**
     * Appends the local "MIRA is thinking…" placeholder so a provider call
     * that can take up to 30 seconds is never rendered as a frozen screen.
     */
    private void showThinking() {
        if (isFinishing() || isDestroyed() || hasThinking()) {
            return;
        }

        messages.add(MessageAdapter.thinking());

        renderMessages(true);
        updateEmptyState();
    }

    private void hideThinking() {
        if (!hasThinking()) {
            return;
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            if (MessageAdapter.isThinking(messages.get(index))) {
                messages.remove(index);
                break;
            }
        }

        renderMessages(false);
        updateEmptyState();
    }

    private boolean hasThinking() {
        for (MessageResponse message : messages) {
            if (MessageAdapter.isThinking(message)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Submits the current list and, when requested, scrolls to the newest item
     * only after the diff has been committed. Scrolling from the submit call
     * itself races the asynchronous diff and targets a stale item count, which
     * leaves a freshly added message below the fold.
     */
    private void renderMessages(boolean followBottom) {
        List<MessageResponse> snapshot = new ArrayList<>(messages);

        if (followBottom) {
            adapter.submitList(snapshot, this::scrollToBottom);
        } else {
            adapter.submitList(snapshot);
        }
    }

    private void renderMessagesAt(int position) {
        adapter.submitList(
                new ArrayList<>(messages),
                () -> scrollToPosition(position)
        );
    }

    private void updateEmptyState() {
        boolean showEmpty = messages.isEmpty() && !loading;

        binding.emptyState.setVisibility(
                showEmpty ? View.VISIBLE : View.GONE
        );
        binding.glowView.setVisibility(
                showEmpty ? View.VISIBLE : View.GONE
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

    /**
     * New-chat mode has no conversation yet: create one from the first prompt,
     * then fall through to the normal send path so the user sees a single
     * uninterrupted flow.
     */
    private void createConversationThenSend(final String content) {
        sending = true;
        binding.sendButton.setEnabled(false);

        conversationRepository.create(
                titleFrom(content),
                new ApiCallback<ConversationResponse>() {
                    @Override
                    public void onSuccess(ConversationResponse conversation) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        sending = false;
                        binding.sendButton.setEnabled(true);

                        conversationId = conversation.getId();
                        applyConversationTitle(conversation.getTitle());
                        loadRecentConversations();

                        sendMessage();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        sending = false;
                        binding.sendButton.setEnabled(true);

                        if (statusCode == 401) {
                            openLogin();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    /**
     * Derives a short, readable thread title from the first prompt. Kept to a
     * few words so the toolbar and drawer rows stay legible.
     */
    private String titleFrom(String content) {
        String singleLine = content.replaceAll("\\s+", " ").trim();

        if (singleLine.isEmpty()) {
            return getString(R.string.untitled_conversation);
        }

        String[] words = singleLine.split(" ");
        StringBuilder title = new StringBuilder();

        for (String word : words) {
            if (title.length() == 0) {
                title.append(word);
                continue;
            }

            if (title.length() + 1 + word.length() > 42) {
                break;
            }

            title.append(' ').append(word);
        }

        return title.toString();
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

    /**
     * Predictive-back safe handling: the drawer closes before the activity does.
     * Registering through the dispatcher keeps gesture back working on API 33+.
     */
    private void setUpBackHandling() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (binding.drawerLayout.isDrawerOpen(
                                GravityCompat.START
                        )) {
                            closeDrawer();
                            return;
                        }

                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
        );
    }
}