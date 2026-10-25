package com.mira.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mira.app.R;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.databinding.ItemConversationBinding;
import com.mira.app.util.DateFormats;

import java.util.Objects;

public class ConversationAdapter extends ListAdapter<
        ConversationResponse,
        ConversationAdapter.Holder
        > {

    public interface Listener {
        void onOpen(ConversationResponse conversation);

        void onDelete(ConversationResponse conversation);
    }

    private static final DiffUtil.ItemCallback<ConversationResponse> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ConversationResponse oldItem,
                        @NonNull ConversationResponse newItem
                ) {
                    return Objects.equals(
                            oldItem.getId(),
                            newItem.getId()
                    );
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ConversationResponse oldItem,
                        @NonNull ConversationResponse newItem
                ) {
                    return Objects.equals(
                            oldItem.getTitle(),
                            newItem.getTitle()
                    ) && Objects.equals(
                            oldItem.getUpdatedAt(),
                            newItem.getUpdatedAt()
                    );
                }
            };

    private final Listener listener;

    public ConversationAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemConversationBinding binding =
                ItemConversationBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                );

        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        holder.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {

        private final ItemConversationBinding binding;

        Holder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ConversationResponse conversation) {
            Context context = binding.getRoot().getContext();

            String title = conversation.getTitle();

            binding.titleText.setText(
                    title == null || title.isBlank()
                            ? context.getString(
                                    R.string.untitled_conversation
                            )
                            : title
            );

            String updated = DateFormats.shortDateTime(
                    conversation.getUpdatedAt()
            );

            if (updated.isEmpty()) {
                binding.updatedText.setVisibility(View.GONE);
            } else {
                binding.updatedText.setVisibility(View.VISIBLE);
                binding.updatedText.setText(
                        context.getString(R.string.updated_at, updated)
                );
            }

            binding.getRoot().setOnClickListener(
                    view -> listener.onOpen(conversation)
            );

            binding.deleteButton.setOnClickListener(
                    view -> listener.onDelete(conversation)
            );
        }
    }
}