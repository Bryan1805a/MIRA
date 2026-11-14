package com.mira.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mira.app.R;
import com.mira.app.data.model.MessageResponse;
import com.mira.app.data.model.MessageRole;
import com.mira.app.databinding.ItemMessageThinkingBinding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Covers the local "MIRA is thinking…" placeholder that
 * {@link com.mira.app.ChatActivity} renders while a response is in flight.
 *
 * <p>The placeholder is a UI-only construct, so these checks verify the parts
 * that a unit test cannot: that the dedicated layout inflates under the real
 * application theme and that {@link MessageAdapter} dispatches the correct
 * view type instead of rendering it as an empty assistant bubble.
 */
@RunWith(AndroidJUnit4.class)
public class MessageAdapterThinkingTest {

    private static final long WAIT_MS = 3_000L;

    /**
     * Material components resolve their style attributes from the theme, so the
     * item must be inflated under {@code Theme.MIRA} exactly as the activity
     * does. The bare application context does not carry it.
     */
    private static Context themedContext() {
        return new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Theme_MIRA
        );
    }

    @Test
    public void thinkingPlaceholderIsDetected() {
        MessageResponse placeholder = MessageAdapter.thinking();

        assertTrue(MessageAdapter.isThinking(placeholder));
        assertEquals(MessageRole.ASSISTANT, placeholder.getRole());
        assertEquals(
                MessageAdapter.THINKING_ID,
                placeholder.getId()
        );

        assertFalse(MessageAdapter.isThinking(null));
        assertFalse(MessageAdapter.isThinking(userMessage()));
        assertFalse(
                MessageAdapter.isThinking(
                        new MessageResponse(
                                "assistant-123",
                                MessageRole.ASSISTANT,
                                "Hello",
                                null,
                                null
                        )
                )
        );
    }

    @Test
    public void thinkingLayoutInflatesWithTheme() {
        final Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        final Context context = themedContext();
        final ItemMessageThinkingBinding[] inflated =
                new ItemMessageThinkingBinding[1];

        /*
         * The indeterminate indicator starts an animator while it is being
         * constructed, so inflation must happen on the main thread exactly as
         * RecyclerView does it.
         */
        instrumentation.runOnMainSync(() -> {
            FrameLayout parent = new FrameLayout(context);

            inflated[0] = ItemMessageThinkingBinding.inflate(
                    LayoutInflater.from(context),
                    parent,
                    false
            );
        });

        ItemMessageThinkingBinding binding = inflated[0];

        assertNotNull(binding);
        assertNotNull(binding.thinkingText);
        assertNotNull(binding.thinkingProgress);
        assertEquals(
                context.getString(R.string.assistant_thinking),
                binding.thinkingText.getText().toString()
        );
    }

    @Test
    public void adapterDispatchesDedicatedThinkingViewHolder()
            throws Exception {
        final Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        final FrameLayout parent = new FrameLayout(themedContext());
        final MessageAdapter adapter = new MessageAdapter(response -> { });

        instrumentation.runOnMainSync(
                () -> adapter.submitList(
                        List.of(MessageAdapter.thinking())
                )
        );

        awaitItem(adapter, instrumentation);
        assertEquals(1, count(adapter, instrumentation));

        final int viewType = viewTypeOf(adapter, instrumentation);

        final RecyclerViewHolderResult result = new RecyclerViewHolderResult();
        instrumentation.runOnMainSync(
                () -> result.holder = adapter.onCreateViewHolder(
                        parent,
                        viewType
                )
        );

        assertNotNull(result.holder);
        assertTrue(
                result.holder instanceof MessageAdapter.ThinkingHolder
        );
    }

    @Test
    public void regularUserMessageStillBindsUserHolder()
            throws Exception {
        final Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        final FrameLayout parent = new FrameLayout(themedContext());
        final MessageAdapter adapter = new MessageAdapter(response -> { });

        instrumentation.runOnMainSync(
                () -> adapter.submitList(List.of(userMessage()))
        );

        awaitItem(adapter, instrumentation);
        assertEquals(1, count(adapter, instrumentation));

        final int viewType = viewTypeOf(adapter, instrumentation);

        final RecyclerViewHolderResult result = new RecyclerViewHolderResult();
        instrumentation.runOnMainSync(
                () -> result.holder = adapter.onCreateViewHolder(
                        parent,
                        viewType
                )
        );

        assertNotNull(result.holder);
        assertTrue(result.holder instanceof MessageAdapter.UserHolder);
    }

    private static void awaitItem(
            MessageAdapter adapter,
            Instrumentation instrumentation
    ) {
        long deadline = System.currentTimeMillis() + WAIT_MS;

        while (count(adapter, instrumentation) == 0
                && System.currentTimeMillis() < deadline) {
            android.os.SystemClock.sleep(20L);
        }
    }

    private static int count(
            MessageAdapter adapter,
            Instrumentation instrumentation
    ) {
        final int[] result = new int[1];

        instrumentation.runOnMainSync(
                () -> result[0] = adapter.getItemCount()
        );

        return result[0];
    }

    private static int viewTypeOf(
            MessageAdapter adapter,
            Instrumentation instrumentation
    ) {
        final int[] result = new int[1];

        instrumentation.runOnMainSync(
                () -> result[0] = adapter.getItemViewType(0)
        );

        return result[0];
    }

    private static MessageResponse userMessage() {
        return new MessageResponse(
                "user-1",
                MessageRole.USER,
                "Explain Shor's algorithm.",
                null,
                null
        );
    }

    private static final class RecyclerViewHolderResult {
        private androidx.recyclerview.widget.RecyclerView.ViewHolder holder;
    }
}
