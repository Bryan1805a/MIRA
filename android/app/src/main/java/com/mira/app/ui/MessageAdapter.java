package com.mira.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mira.app.R;
import com.mira.app.data.model.MessageResponse;
import com.mira.app.data.model.MessageRole;
import com.mira.app.data.model.ResponseStatus;
import com.mira.app.databinding.ItemMessageAssistantBinding;
import com.mira.app.databinding.ItemMessageThinkingBinding;
import com.mira.app.databinding.ItemMessageUserBinding;
import com.mira.app.util.DateFormats;

import java.util.Objects;

public class MessageAdapter extends ListAdapter<
        MessageResponse,
        RecyclerView.ViewHolder
        > {

    /**
     * Stable identifier for the local, UI-only placeholder that is rendered
     * while a response is in flight. It is never persisted or sent to the
     * backend; {@link ChatActivity} appends it before the provider call and
     * removes it when the request finishes.
     */
    public static final String THINKING_ID = "mira-pending-assistant";

    public interface Listener {
        void onRetry(MessageResponse.ResponseMetadata response);
    }

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_THINKING = 2;
    private static final int MAX_ATTEMPTS = 3;

    public static MessageResponse thinking() {
        return new MessageResponse(
                THINKING_ID,
                MessageRole.ASSISTANT,
                "",
                null,
                null
        );
    }

    public static boolean isThinking(MessageResponse message) {
        return message != null
                && THINKING_ID.equals(message.getId());
    }

    private static final DiffUtil.ItemCallback<MessageResponse> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull MessageResponse oldItem,
                        @NonNull MessageResponse newItem
                ) {
                    return Objects.equals(
                            oldItem.getId(),
                            newItem.getId()
                    );
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull MessageResponse oldItem,
                        @NonNull MessageResponse newItem
                ) {
                    return Objects.equals(
                            oldItem.getContent(),
                            newItem.getContent()
                    ) && Objects.equals(
                            oldItem.getRole(),
                            newItem.getRole()
                    )
                            && sameResponse(
                                    oldItem.getResponse(),
                                    newItem.getResponse()
                            );
                }
            };

    private final Listener listener;

    public MessageAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        MessageResponse message = getItem(position);

        if (isThinking(message)) {
            return TYPE_THINKING;
        }

        return message.getRole() == MessageRole.USER
                ? TYPE_USER
                : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(
                parent.getContext()
        );

        if (viewType == TYPE_USER) {
            return new UserHolder(
                    ItemMessageUserBinding.inflate(
                            inflater,
                            parent,
                            false
                    )
            );
        }

        if (viewType == TYPE_THINKING) {
            return new ThinkingHolder(
                    ItemMessageThinkingBinding.inflate(
                            inflater,
                            parent,
                            false
                    )
            );
        }

        return new AssistantHolder(
                ItemMessageAssistantBinding.inflate(
                        inflater,
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        MessageResponse message = getItem(position);

        if (holder instanceof UserHolder) {
            ((UserHolder) holder).bind(message);
        } else if (holder instanceof ThinkingHolder) {
            ((ThinkingHolder) holder).bind();
        } else if (holder instanceof AssistantHolder) {
            ((AssistantHolder) holder).bind(message);
        }
    }

    private static boolean sameResponse(
            MessageResponse.ResponseMetadata oldResponse,
            MessageResponse.ResponseMetadata newResponse
    ) {
        if (oldResponse == null || newResponse == null) {
            return oldResponse == newResponse;
        }

        return Objects.equals(
                oldResponse.getId(),
                newResponse.getId()
        )
                && Objects.equals(
                        oldResponse.getStatus(),
                        newResponse.getStatus()
                )
                && oldResponse.getAttemptCount()
                == newResponse.getAttemptCount();
    }

    private static void bindTimestamp(TextView view, String createdAt) {
        String label = DateFormats.shortDateTime(createdAt);

        if (label.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }

        view.setText(label);
        view.setVisibility(View.VISIBLE);
    }

    private static void enableCopyOnLongPress(View anchor, String content) {
        if (TextUtils.isEmpty(content)) {
            anchor.setOnLongClickListener(null);
            return;
        }

        anchor.setOnLongClickListener(view -> {
            Context context = view.getContext();
            ClipboardManager clipboard = (ClipboardManager) context
                    .getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboard == null) {
                return false;
            }

            clipboard.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.app_name), content)
            );
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Toast.makeText(
                    context,
                    R.string.message_copied,
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        });
    }

    class UserHolder extends RecyclerView.ViewHolder {

        private final ItemMessageUserBinding binding;

        UserHolder(ItemMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageResponse message) {
            binding.messageText.setText(message.getContent());
            bindTimestamp(binding.timestampText, message.getCreatedAt());
            enableCopyOnLongPress(binding.messageText, message.getContent());

            MessageResponse.ResponseMetadata response =
                    message.getResponse();

            if (response == null || response.getStatus() == null) {
                binding.statusText.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
                binding.retryButton.setOnClickListener(null);
                return;
            }

            ResponseStatus status = response.getStatus();

            if (status == ResponseStatus.PENDING) {
                binding.statusText.setText(
                        R.string.message_status_pending
                );
                binding.statusText.setVisibility(View.VISIBLE);
            } else if (status == ResponseStatus.FAILED) {
                binding.statusText.setText(
                        R.string.message_status_failed
                );
                binding.statusText.setVisibility(View.VISIBLE);
            } else if (status == ResponseStatus.UNKNOWN) {
                binding.statusText.setText(
                        R.string.message_status_unknown
                );
                binding.statusText.setVisibility(View.VISIBLE);
            } else {
                binding.statusText.setVisibility(View.GONE);
            }

            boolean retryable = status == ResponseStatus.FAILED
                    || status == ResponseStatus.UNKNOWN;

            boolean canRetry = retryable
                    && response.getAttemptCount() < MAX_ATTEMPTS;

            binding.retryButton.setVisibility(
                    canRetry ? View.VISIBLE : View.GONE
            );

            if (canRetry) {
                binding.retryButton.setOnClickListener(
                        view -> listener.onRetry(response)
                );
            } else {
                binding.retryButton.setOnClickListener(null);
            }
        }
    }

    static class AssistantHolder extends RecyclerView.ViewHolder {

        private final ItemMessageAssistantBinding binding;

        AssistantHolder(ItemMessageAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageResponse message) {
            binding.messageText.setText(
                    MarkdownText.toPlainText(message.getContent())
            );
            bindTimestamp(binding.timestampText, message.getCreatedAt());
            enableCopyOnLongPress(binding.messageText, message.getContent());
        }
    }

    static class ThinkingHolder extends RecyclerView.ViewHolder {

        private final ItemMessageThinkingBinding binding;

        ThinkingHolder(ItemMessageThinkingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind() {
            /*
             * The indeterminate progress indicator animates on its own while
             * attached and visible, so no animation state has to be managed
             * across view recycling.
             */
            binding.thinkingText.setText(R.string.assistant_thinking);
        }
    }
}