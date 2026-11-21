package com.mira.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mira.app.data.ApiCallback;
import com.mira.app.data.ConversationRepository;
import com.mira.app.data.MemoryRepository;
import com.mira.app.data.MessageRepository;
import com.mira.app.data.model.AssistantResponseResponse;
import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.model.MessagePageResponse;
import com.mira.app.data.model.MessageResponse;
import com.mira.app.data.model.MessageRole;
import com.mira.app.data.model.StyleProfileResponse;
import com.mira.app.data.model.RegenerateResponse;
import com.mira.app.data.model.ResponseStatus;
import com.mira.app.data.model.RetryResponse;
import com.mira.app.data.model.SendMessageResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityChatBinding;
import com.mira.app.ui.ConversationAdapter;
import com.mira.app.ui.MessageAdapter;
import com.mira.app.util.MiraTheme;
import com.mira.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private MemoryRepository memoryRepository;
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
    private boolean dynamicColorEnabled;
    private boolean wideLayout;
    private boolean drawerLoading;
    private ObjectAnimator chatSkeletonAnimator;
    private ObjectAnimator drawerSkeletonAnimator;
    private boolean sending;

    private String pendingSendKey;
    private String pendingSendContent;
    private String optimisticPromptId;

    private String pendingRetryKey;
    private String pendingRetryResponseId;

    private int effortIndex = 1;

    private ObjectAnimator glowAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        MiraTheme.apply(this);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dynamicColorEnabled = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getBoolean(MiraApplication.PREF_DYNAMIC_COLOR, false);

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
        memoryRepository = new MemoryRepository(this);

        setUpToolbar();
        setUpDrawer();
        setUpBackHandling();
        setUpEffort();
        setUpMemoryChip();
        setUpList();
        applyWideLayout();
        setUpGreeting();
        setUpSuggestions();
        binding.sendButton.setOnClickListener(
                view -> sendMessage()
        );
        binding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonAlpha();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        updateSendButtonAlpha();

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
        binding.newChatTopButton.setOnClickListener(
                view -> startNewChat()
        );

        applyConversationTitle(
                getIntent().getStringExtra(EXTRA_CONVERSATION_TITLE)
        );
    }

    private void applyConversationTitle(String title) {
        String display = title == null || title.isBlank()
                ? getString(
                        conversationId == null
                                ? R.string.new_chat
                                : R.string.chat_title
                )
                : title;
        binding.toolbarTitleText.setText(display);
        binding.chatToolbar.setTitle("");
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

        binding.drawerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
                /* Filtering is purely local over the loaded page. */
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                applyConversationFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
                /* Handled in onTextChanged. */
            }
        });

        /*
         * Only right-swipes are offered: a left drag inside the drawer is the
         * DrawerLayout's own close gesture, so it would never reach the row.
         */
        final Paint swipeBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable swipeIcon = ContextCompat.getDrawable(
                this,
                R.drawable.ic_delete
        );
        final int swipeIconSize = Math.round(
                20 * getResources().getDisplayMetrics().density
        );

        new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(
                        0,
                        ItemTouchHelper.RIGHT
                ) {
                    @Override
                    public boolean onMove(
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            @NonNull RecyclerView.ViewHolder target
                    ) {
                        return false;
                    }

                    @Override
                    public void onChildDraw(
                            @NonNull Canvas canvas,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX,
                            float dY,
                            int actionState,
                            boolean isCurrentlyActive
                    ) {
                        if (dX > 0) {
                            View item = viewHolder.itemView;

                            swipeBackground.setColor(
                                    MaterialColors.getColor(
                                            item,
                                            com.google.android.material.R.attr
                                                    .colorErrorContainer
                                    )
                            );
                            canvas.drawRect(
                                    item.getLeft(),
                                    item.getTop(),
                                    item.getLeft() + dX,
                                    item.getBottom(),
                                    swipeBackground
                            );

                            if (swipeIcon != null) {
                                int top = item.getTop()
                                        + (item.getHeight() - swipeIconSize) / 2;
                                int left = item.getLeft() + swipeIconSize;

                                swipeIcon.setBounds(
                                        left,
                                        top,
                                        left + swipeIconSize,
                                        top + swipeIconSize
                                );
                                swipeIcon.setTint(
                                        MaterialColors.getColor(
                                                item,
                                                com.google.android.material.R.attr
                                                        .colorOnErrorContainer
                                        )
                                );
                                swipeIcon.draw(canvas);
                            }
                        }

                        super.onChildDraw(
                                canvas,
                                recyclerView,
                                viewHolder,
                                dX,
                                dY,
                                actionState,
                                isCurrentlyActive
                        );
                    }

                    @Override
                    public void onSwiped(
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            int direction
                    ) {
                        int position = viewHolder.getBindingAdapterPosition();

                        if (position == RecyclerView.NO_POSITION) {
                            return;
                        }

                        ConversationResponse conversation =
                                drawerAdapter.getCurrentList().get(position);

                        /* Bring the row back; deletion is confirmed first. */
                        drawerAdapter.notifyItemChanged(position);
                        confirmDeleteConversation(conversation);
                    }
                }
        ).attachToRecyclerView(binding.drawerConversations);

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
                        binding.drawerSearch.setText("");
                        loadRecentConversations();
                    }
                }
        );
    }

    private void showChatSkeleton(boolean show) {
        if (show) {
            binding.chatSkeleton.setVisibility(View.VISIBLE);

            if (chatSkeletonAnimator == null) {
                chatSkeletonAnimator = pulse(binding.chatSkeleton);
            }
        } else {
            binding.chatSkeleton.setVisibility(View.GONE);
            stopPulse(chatSkeletonAnimator);
            chatSkeletonAnimator = null;
        }
    }

    private void showDrawerSkeleton(boolean show) {
        drawerLoading = show;

        if (show) {
            binding.drawerSkeleton.setVisibility(View.VISIBLE);

            if (drawerSkeletonAnimator == null) {
                drawerSkeletonAnimator = pulse(binding.drawerSkeleton);
            }
        } else {
            binding.drawerSkeleton.setVisibility(View.GONE);
            stopPulse(drawerSkeletonAnimator);
            drawerSkeletonAnimator = null;
        }
    }

    private ObjectAnimator pulse(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(
                view,
                View.ALPHA,
                0.35f,
                0.85f
        );
        animator.setDuration(650L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.start();

        return animator;
    }

    private static void stopPulse(ObjectAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    private void loadRecentConversations() {
        showDrawerSkeleton(recentConversations.isEmpty());

        conversationRepository.list(
                null,
                new ApiCallback<ConversationPageResponse>() {
                    @Override
                    public void onSuccess(ConversationPageResponse page) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        showDrawerSkeleton(false);

                        recentConversations.clear();

                        if (page.getItems() != null) {
                            recentConversations.addAll(page.getItems());
                        }

                        applyConversationFilter();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        showDrawerSkeleton(false);

                        if (statusCode == 401) {
                            openLogin();
                        }
                    }
                }
        );
    }

    /**
     * Filters the loaded conversations by title. Kept local because the drawer
     * only shows the first page, so there is no reason to hit the network while
     * the user types.
     */
    private void applyConversationFilter() {
        CharSequence raw = binding.drawerSearch.getText();
        String query = raw == null
                ? ""
                : raw.toString().trim().toLowerCase(Locale.getDefault());

        List<ConversationResponse> filtered = new ArrayList<>(
                recentConversations.size()
        );

        for (ConversationResponse conversation : recentConversations) {
            String title = conversation.getTitle();

            if (query.isEmpty()
                    || (title != null
                    && title.toLowerCase(Locale.getDefault()).contains(query))) {
                filtered.add(conversation);
            }
        }

        drawerAdapter.submitList(filtered);

        boolean empty = filtered.isEmpty() && !drawerLoading;

        binding.drawerEmpty.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );
        binding.drawerEmpty.setText(
                query.isEmpty()
                        ? R.string.drawer_no_chats
                        : R.string.drawer_no_matches
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
        /*
         * Lock mode stops gesture closing, but a programmatic close still hides
         * the panel, which would leave the sidebar blank on tablets.
         */
        if (wideLayout) {
            return;
        }

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
        renderGreeting();
    }

    private void renderGreeting() {
        if (binding == null) {
            return;
        }

        String name = greetingName();

        binding.greetingText.setText(
                name.isEmpty()
                        ? getString(R.string.greeting_default)
                        : getString(R.string.greeting_named, name)
        );

        binding.greetingText.post(() -> {
            CharSequence text = binding.greetingText.getText();
            if (text != null && text.length() > 0) {
                float width = binding.greetingText.getPaint().measureText(text.toString());
                if (width > 0) {
                    Shader gradient = new LinearGradient(
                            0f, 0f, width, 0f,
                            new int[]{0xFF9E7BFF, 0xFF6C4CE0, 0xFFC7A2FF},
                            null, Shader.TileMode.CLAMP
                    );
                    binding.greetingText.getPaint().setShader(gradient);
                    binding.greetingText.invalidate();
                }
            }
        });
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
    /**
     * The saved name wins; otherwise a plausible name is derived from the email
     * local part. Generated-looking accounts greet without a name instead of
     * inventing one.
     */
    private String greetingName() {
        String saved = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getString(MiraApplication.PREF_DISPLAY_NAME, "");

        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }

        return displayName(tokenStore.email());
    }

    private static String displayName(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }

        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;

        /* Digits usually mean a generated address, not a person's name. */
        for (int index = 0; index < local.length(); index++) {
            if (Character.isDigit(local.charAt(index))) {
                return "";
            }
        }

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
                new MessageAdapter.Listener() {
                    @Override
                    public void onRetry(
                            MessageResponse.ResponseMetadata response
                    ) {
                        retry(response);
                    }

                    @Override
                    public void onRegenerate(
                            MessageResponse.ResponseMetadata response
                    ) {
                        regenerate(response);
                    }
                }
        );
        adapter.setRevealProgressListener(this::followReveal);

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
        showChatSkeleton(messages.isEmpty());

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
                        showChatSkeleton(false);

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
                        showChatSkeleton(false);

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

        /*
         * Feedback first: the prompt and the thinking row appear before any
         * network round trip, including the conversation creation below.
         */
        addOptimisticPrompt(content);
        setSending(true);

        if (conversationId == null) {
            createConversationThenSend(content, idempotencyKey);
            return;
        }

        dispatchSend(content, idempotencyKey);
    }

    private void dispatchSend(String content, String idempotencyKey) {
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
                            MessageResponse assistantMessage =
                                    buildAssistantMessage(assistant);

                            messages.add(assistantMessage);
                            adapter.setRevealTarget(
                                    assistantMessage.getId()
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
            MessageResponse assistantMessage =
                    buildAssistantMessage(assistant);

            messages.add(userIndex + 1, assistantMessage);
            adapter.setRevealTarget(assistantMessage.getId());
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
                new MessageResponse.ResponseMetadata(
                        assistant.getId(),
                        assistant.getStatus(),
                        0
                )
        );
    }

    /**
     * Asks the backend for a fresh answer to the same prompt. The reply replaces
     * the text in place, so the thread keeps one answer per question; if the
     * provider fails the old answer stays and the user is told.
     */
    private void regenerate(MessageResponse.ResponseMetadata metadata) {
        if (sending || metadata == null || metadata.getId() == null) {
            return;
        }

        binding.errorText.setVisibility(View.GONE);
        setSending(true);

        repository.regenerate(
                conversationId,
                metadata.getId(),
                UUID.randomUUID().toString(),
                new ApiCallback<RegenerateResponse>() {
                    @Override
                    public void onSuccess(RegenerateResponse result) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setSending(false);

                        AssistantResponseResponse assistant =
                                result.getAssistantResponse();

                        if (assistant == null) {
                            return;
                        }

                        int index = indexOfResponse(metadata.getId());

                        if (index >= 0) {
                            MessageResponse current = messages.get(index);

                            MessageResponse updated = new MessageResponse(
                                    current.getId(),
                                    MessageRole.ASSISTANT,
                                    assistant.getContent(),
                                    current.getCreatedAt(),
                                    current.getResponse()
                            );

                            messages.set(index, updated);
                            adapter.setRevealTarget(updated.getId());
                            renderMessages(false);
                        }

                        if (!result.isRegenerated()) {
                            Toast.makeText(
                                    ChatActivity.this,
                                    R.string.regenerate_failed,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onError(String message, int statusCode) {
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

    private int indexOfResponse(String responseId) {
        for (int index = 0; index < messages.size(); index++) {
            MessageResponse message = messages.get(index);

            /* The user prompt carries the same response metadata, so the
               assistant row is the only replacement target. */
            if (message.getRole() != MessageRole.ASSISTANT) {
                continue;
            }

            MessageResponse.ResponseMetadata metadata =
                    message.getResponse();

            if (metadata != null
                    && responseId.equals(metadata.getId())) {
                return index;
            }
        }

        return -1;
    }

    private void setSending(boolean value) {
        sending = value;
        binding.sendButton.setEnabled(!value);
        binding.messageInput.setEnabled(!value);
        updateSendButtonAlpha();

        if (value) {
            showThinking();
        } else {
            hideThinking();
            removeOptimisticPrompt();
        }

        updateGlow();
    }

    private void updateSendButtonAlpha() {
        if (sending) {
            binding.sendButton.setAlpha(0.35f);
            return;
        }
        CharSequence text = binding.messageInput.getText();
        boolean hasText = text != null && !text.toString().trim().isEmpty();
        binding.sendButton.setAlpha(hasText ? 1.0f : 0.45f);
    }

    /**
     * The glow breathes while an answer is generating and while the thread is
     * empty, so the prompt bar area always has a hint of the brand.
     */
    private void updateGlow() {
        boolean show = sending || (messages.isEmpty() && !loading);

        binding.glowView.setVisibility(
                show ? View.VISIBLE : View.GONE
        );

        if (show) {
            startGlowPulse();
        } else {
            stopGlowPulse();
        }
    }

    private void startGlowPulse() {
        if (glowAnimator != null) {
            return;
        }

        glowAnimator = ObjectAnimator.ofFloat(
                binding.glowView,
                View.ALPHA,
                0.45f,
                1f
        );
        glowAnimator.setDuration(1700L);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.start();
    }

    private void stopGlowPulse() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }

        binding.glowView.setAlpha(1f);
    }

    /**
     * Keeps the newest text in view while the answer types itself in, but only
     * when the reader has not scrolled away deliberately.
     */
    private void followReveal() {
        LinearLayoutManager manager =
                (LinearLayoutManager) binding.messageList.getLayoutManager();

        if (manager == null) {
            return;
        }

        int lastVisible = manager.findLastVisibleItemPosition();

        if (adapter.getItemCount() - 1 - lastVisible <= 1) {
            binding.messageList.scrollToPosition(
                    adapter.getItemCount() - 1
            );
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
                getString(effortOption(effortIndex))
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

        updateGlow();
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
     * then continue with the normal send so the user sees one uninterrupted
     * flow. The prompt and thinking row are already on screen by this point.
     */
    private void createConversationThenSend(
            final String content,
            final String idempotencyKey
    ) {
        conversationRepository.create(
                titleFrom(content),
                new ApiCallback<ConversationResponse>() {
                    @Override
                    public void onSuccess(ConversationResponse conversation) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        conversationId = conversation.getId();
                        applyConversationTitle(conversation.getTitle());
                        loadRecentConversations();

                        dispatchSend(content, idempotencyKey);
                    }

                    @Override
                    public void onError(String message, int statusCode) {
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
    /**
     * Surfaces the style memory inside the chat instead of only in Settings, so
     * the personalization is visible while a conversation is happening. The chip
     * reflects the saved profile and opens the memory screen.
     */
    private void setUpMemoryChip() {
        binding.memoryChip.setOnClickListener(
                view -> startActivity(
                        new Intent(this, MemoryActivity.class)
                )
        );
    }

    private void refreshMemoryChip() {
        if (binding == null || memoryRepository == null) {
            return;
        }

        memoryRepository.getStyle(new ApiCallback<StyleProfileResponse>() {
            @Override
            public void onSuccess(StyleProfileResponse profile) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (profile == null || !profile.isEnabled()) {
                    binding.memoryChip.setVisibility(View.GONE);
                    return;
                }

                binding.memoryChip.setText(
                        hasPreferences(profile)
                                ? R.string.memory_chip_on
                                : R.string.memory_chip_enabled
                );
                binding.memoryChip.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message, int statusCode) {
                /* Memory is advisory; never interrupt the chat over it. */
                if (binding != null) {
                    binding.memoryChip.setVisibility(View.GONE);
                }
            }
        });
    }

    private static boolean hasPreferences(StyleProfileResponse profile) {
        Map<String, String> values = profile.getProfile();

        if (values == null) {
            return false;
        }

        for (String value : values.values()) {
            if (value != null && !value.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Colours are applied per activity, so pick up a toggle made in Settings. */
        boolean dynamicNow = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getBoolean(MiraApplication.PREF_DYNAMIC_COLOR, false);

        if (dynamicNow != dynamicColorEnabled) {
            recreate();
            return;
        }

        if (tokenStore != null && tokenStore.hasSession()) {
            renderGreeting();
            refreshMemoryChip();
        }
    }

    /**
     * Tablets get a permanently visible history sidebar instead of a drawer:
     * the content is inset past the panel, the scrim is dropped and the toggle
     * affordances disappear. Phones keep the overlay drawer.
     */
    private void applyWideLayout() {
        boolean wide = getResources()
                .getConfiguration()
                .smallestScreenWidthDp >= 600;

        if (!wide) {
            return;
        }

        wideLayout = true;

        binding.drawerLayout.setScrimColor(Color.TRANSPARENT);
        binding.drawerLayout.setDrawerLockMode(
                DrawerLayout.LOCK_MODE_LOCKED_OPEN,
                GravityCompat.START
        );

        int drawerWidth = Math.round(
                320 * getResources().getDisplayMetrics().density
        );

        binding.contentRoot.setPadding(drawerWidth, 0, 0, 0);
        binding.chatToolbar.setNavigationIcon(null);
        binding.drawerClose.setVisibility(View.GONE);

        /* No drawer-open event fires here, so load the history directly. */
        loadRecentConversations();
    }

    private void setUpBackHandling() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!wideLayout && binding.drawerLayout.isDrawerOpen(
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

    @Override
    protected void onDestroy() {
        stopGlowPulse();
        stopPulse(chatSkeletonAnimator);
        stopPulse(drawerSkeletonAnimator);
        super.onDestroy();
    }
}