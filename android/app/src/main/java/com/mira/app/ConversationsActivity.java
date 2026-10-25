package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mira.app.data.ApiCallback;
import com.mira.app.data.ConversationRepository;
import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityConversationsBinding;
import com.mira.app.databinding.DialogCreateConversationBinding;
import com.mira.app.ui.ConversationAdapter;
import com.mira.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity extends AppCompatActivity {

    private ActivityConversationsBinding binding;
    private ConversationRepository repository;
    private ConversationAdapter adapter;
    private TokenStore tokenStore;

    private final List<ConversationResponse> conversations =
            new ArrayList<>();

    private String nextCursor;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityConversationsBinding.inflate(
                getLayoutInflater()
        );
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        tokenStore = TokenStore.get(this);
        repository = new ConversationRepository(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        setUpList();

        binding.newConversationButton.setOnClickListener(
                view -> showCreateDialog()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tokenStore != null && tokenStore.hasSession()) {
            loadFirstPage();
        }
    }

    private void setUpList() {
        adapter = new ConversationAdapter(
                new ConversationAdapter.Listener() {
                    @Override
                    public void onOpen(
                            ConversationResponse conversation
                    ) {
                        openChat(conversation);
                    }

                    @Override
                    public void onDelete(
                            ConversationResponse conversation
                    ) {
                        confirmDelete(conversation);
                    }
                }
        );

        binding.conversationList.setLayoutManager(
                new LinearLayoutManager(this)
        );
        binding.conversationList.setAdapter(adapter);

        binding.conversationList.addOnScrollListener(
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
                            loadPage(nextCursor);
                        }
                    }
                }
        );
    }

    private void loadFirstPage() {
        if (loading) {
            return;
        }

        conversations.clear();
        nextCursor = null;
        adapter.submitList(new ArrayList<>());
        updateEmptyState();
        loadPage(null);
    }

    private void loadPage(String cursor) {
        loading = true;
        binding.emptyState.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.GONE);
        binding.progress.setVisibility(
                conversations.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        repository.list(cursor, new ApiCallback<ConversationPageResponse>() {
            @Override
            public void onSuccess(ConversationPageResponse page) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                loading = false;
                binding.progress.setVisibility(View.GONE);

                if (cursor == null) {
                    conversations.clear();
                }

                if (page.getItems() != null) {
                    conversations.addAll(page.getItems());
                }

                nextCursor = page.getNextCursor();

                adapter.submitList(
                        new ArrayList<>(conversations)
                );
                updateEmptyState();
            }

            @Override
            public void onError(String message, int statusCode) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                loading = false;
                binding.progress.setVisibility(View.GONE);

                if (statusCode == 401) {
                    tokenStore.clear();
                    openLogin();
                    return;
                }

                showError(message);
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        binding.emptyState.setVisibility(
                conversations.isEmpty() && !loading
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void showCreateDialog() {
        DialogCreateConversationBinding dialogBinding =
                DialogCreateConversationBinding.inflate(
                        getLayoutInflater()
                );

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_conversation)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.create,
                        (dialog, which) -> {
                            String title = textOf(
                                    dialogBinding.titleInput
                            );

                            if (title.isEmpty()) {
                                title = getString(
                                        R.string.untitled_conversation
                                );
                            }

                            createConversation(title);
                        }
                )
                .show();
    }

    private void createConversation(String title) {
        binding.newConversationButton.setEnabled(false);

        repository.create(title, new ApiCallback<ConversationResponse>() {
            @Override
            public void onSuccess(ConversationResponse conversation) {
                binding.newConversationButton.setEnabled(true);
                openChat(conversation);
            }

            @Override
            public void onError(String message, int statusCode) {
                binding.newConversationButton.setEnabled(true);

                if (statusCode == 401) {
                    tokenStore.clear();
                    openLogin();
                    return;
                }

                showError(message);
            }
        });
    }

    private void confirmDelete(ConversationResponse conversation) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_conversation)
                .setMessage(R.string.delete_conversation_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.delete,
                        (dialog, which) ->
                                deleteConversation(conversation)
                )
                .show();
    }

    private void deleteConversation(ConversationResponse conversation) {
        repository.delete(
                conversation.getId(),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        loadFirstPage();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (statusCode == 401) {
                            tokenStore.clear();
                            openLogin();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void openChat(ConversationResponse conversation) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(
                ChatActivity.EXTRA_CONVERSATION_ID,
                conversation.getId()
        );
        intent.putExtra(
                ChatActivity.EXTRA_CONVERSATION_TITLE,
                conversation.getTitle()
        );
        startActivity(intent);
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
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
}