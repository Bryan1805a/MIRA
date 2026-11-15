package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mira.app.data.ApiCallback;
import com.mira.app.data.MemoryRepository;
import com.mira.app.data.model.MemoryCandidateListResponse;
import com.mira.app.data.model.MemoryCandidateResponse;
import com.mira.app.data.model.MemorySettingsResponse;
import com.mira.app.data.model.StyleProfileResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityMemoryBinding;
import com.mira.app.databinding.ItemMemoryCandidateBinding;
import com.mira.app.ui.MemoryOptions;
import com.mira.app.util.DateFormats;
import com.mira.app.util.WindowInsetsHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryActivity extends AppCompatActivity {

    private ActivityMemoryBinding binding;
    private MemoryRepository repository;
    private TokenStore tokenStore;

    private final Map<String, Integer> selections =
            new LinkedHashMap<>();

    private final Map<String, ChipGroup> chipGroups =
            new LinkedHashMap<>();

    private boolean busy;
    private boolean profileLoaded;
    private boolean suppressToggle;
    private boolean suppressChips;

    private int profileVersion = 1;
    private boolean memoryEnabled;
    private String profileUpdatedAt;
    private Map<String, String> profileValues =
            new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMemoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        tokenStore = TokenStore.get(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        repository = new MemoryRepository(this);

        setUpToolbar();
        setUpChips();
        setUpActions();
        loadAll();
    }

    private void setUpToolbar() {
        binding.memoryToolbar.setNavigationOnClickListener(
                view -> finish()
        );
    }

    /**
     * Preferences are single-select chip groups: one tap sets a preference, and
     * tapping the selected chip again clears it back to "no strong preference".
     */
    private void setUpChips() {
        chipGroups.put(MemoryOptions.FIELD_DEPTH, binding.depthChips);
        chipGroups.put(MemoryOptions.FIELD_TONE, binding.toneChips);
        chipGroups.put(MemoryOptions.FIELD_EXAMPLES, binding.examplesChips);
        chipGroups.put(MemoryOptions.FIELD_FORMATTING, binding.formattingChips);
        chipGroups.put(MemoryOptions.FIELD_TEACHING, binding.teachingChips);

        for (Map.Entry<String, ChipGroup> entry : chipGroups.entrySet()) {
            populateChips(entry.getKey(), entry.getValue());
        }
    }

    private void populateChips(String field, ChipGroup group) {
        group.removeAllViews();

        String[] labels = MemoryOptions.labels(this, field);

        for (int index = 0; index < labels.length; index++) {
            Chip chip = (Chip) getLayoutInflater().inflate(
                    R.layout.item_memory_chip,
                    group,
                    false
            );

            chip.setText(labels[index]);
            chip.setTag(index);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);

            group.addView(chip);
        }

        group.setOnCheckedStateChangeListener((changedGroup, checkedIds) -> {
            if (suppressChips) {
                return;
            }

            if (checkedIds.isEmpty()) {
                selections.put(field, 0);
                return;
            }

            View checked = changedGroup.findViewById(checkedIds.get(0));

            if (checked != null && checked.getTag() instanceof Integer) {
                selections.put(field, (Integer) checked.getTag());
            }
        });

        selections.put(field, 0);
    }

    private void setUpActions() {
        binding.enabledSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (suppressToggle || busy) {
                        return;
                    }

                    updateEnabled(isChecked);
                }
        );

        binding.saveButton.setOnClickListener(
                view -> saveProfile()
        );
        binding.purgeButton.setOnClickListener(
                view -> confirmPurge()
        );
        binding.retryButton.setOnClickListener(
                view -> loadAll()
        );
    }

    private void loadAll() {
        profileLoaded = false;

        binding.errorText.setVisibility(View.GONE);
        binding.retryButton.setVisibility(View.GONE);
        binding.progress.setVisibility(View.VISIBLE);
        setBusy(true);

        loadStyle();
        loadCandidates();
    }

    private void loadStyle() {
        repository.getStyle(new ApiCallback<StyleProfileResponse>() {
            @Override
            public void onSuccess(StyleProfileResponse value) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progress.setVisibility(View.GONE);
                applyProfile(value);
            }

            @Override
            public void onError(String message, int statusCode) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                binding.progress.setVisibility(View.GONE);
                setBusy(false);

                if (handleAuth(statusCode)) {
                    return;
                }

                showError(message);
            }
        });
    }

    private void loadCandidates() {
        repository.listCandidates(
                new ApiCallback<MemoryCandidateListResponse>() {
                    @Override
                    public void onSuccess(
                            MemoryCandidateListResponse value
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        renderCandidates(
                                value == null
                                        ? null
                                        : value.getItems()
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

                        if (handleAuth(statusCode)) {
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void applyProfile(StyleProfileResponse value) {
        if (value == null) {
            profileLoaded = false;
            setBusy(false);
            showError(getString(R.string.memory_load_failed));
            return;
        }

        profileLoaded = true;
        memoryEnabled = value.isEnabled();
        profileVersion = value.getVersion();
        profileUpdatedAt = value.getUpdatedAt();
        profileValues = value.getProfile() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(value.getProfile());

        suppressToggle = true;
        binding.enabledSwitch.setChecked(memoryEnabled);
        suppressToggle = false;

        binding.errorText.setVisibility(View.GONE);
        binding.retryButton.setVisibility(View.GONE);

        renderStatus();
        renderPreferences();
        setBusy(false);
    }

    private void renderStatus() {
        binding.statusText.setText(
                memoryEnabled
                        ? R.string.memory_status_on
                        : R.string.memory_status_off
        );

        String updated = DateFormats.shortDateTime(
                profileUpdatedAt
        );

        binding.versionText.setText(
                updated.isEmpty()
                        ? getString(
                                R.string.memory_version,
                                profileVersion
                        )
                        : getString(
                                R.string.memory_version_updated,
                                profileVersion,
                                updated
                        )
        );
    }

    private void renderPreferences() {
        for (String field : chipGroups.keySet()) {
            renderField(field);
        }
    }

    private void renderField(String field) {
        ChipGroup group = chipGroups.get(field);

        if (group == null) {
            return;
        }

        String value = profileValues.get(field);
        int index = MemoryOptions.indexOf(field, value);

        if (index < 0) {
            index = 0;
        }

        selections.put(field, index);

        suppressChips = true;
        group.clearCheck();

        if (index > 0) {
            for (int childIndex = 0;
                 childIndex < group.getChildCount();
                 childIndex++) {
                View child = group.getChildAt(childIndex);

                if (child.getTag() instanceof Integer
                        && (Integer) child.getTag() == index) {
                    group.check(child.getId());
                    break;
                }
            }
        }

        suppressChips = false;
    }

    private Map<String, String> selectedProfile() {
        Map<String, String> profile = new LinkedHashMap<>();

        for (String field : MemoryOptions.FIELDS) {
            int index = selections.getOrDefault(field, 0);

            if (index <= 0) {
                continue;
            }

            profile.put(field, MemoryOptions.values(field)[index]);
        }

        return profile;
    }

    private void saveProfile() {
        if (busy || !profileLoaded) {
            return;
        }

        setBusy(true);
        binding.errorText.setVisibility(View.GONE);

        repository.replace(
                profileVersion,
                selectedProfile(),
                new ApiCallback<StyleProfileResponse>() {
                    @Override
                    public void onSuccess(StyleProfileResponse value) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        applyProfile(value);
                        toast(R.string.memory_saved);
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setBusy(false);

                        if (handleAuth(statusCode)) {
                            return;
                        }

                        if (statusCode == 409) {
                            toast(R.string.memory_conflict);
                            loadAll();
                            return;
                        }

                        showError(message);
                    }
                }
        );
    }

    private void updateEnabled(boolean enabled) {
        setBusy(true);
        binding.errorText.setVisibility(View.GONE);

        repository.updateSettings(
                enabled,
                new ApiCallback<MemorySettingsResponse>() {
                    @Override
                    public void onSuccess(
                            MemorySettingsResponse value
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (value != null) {
                            memoryEnabled = value.isEnabled();
                            profileVersion = value.getVersion();
                            profileUpdatedAt = value.getUpdatedAt();
                        }

                        suppressToggle = true;
                        binding.enabledSwitch.setChecked(memoryEnabled);
                        suppressToggle = false;

                        renderStatus();
                        setBusy(false);
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setBusy(false);

                        if (handleAuth(statusCode)) {
                            return;
                        }

                        suppressToggle = true;
                        binding.enabledSwitch.setChecked(memoryEnabled);
                        suppressToggle = false;

                        showError(message);
                    }
                }
        );
    }

    private void confirmPurge() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.memory_purge_title)
                .setMessage(R.string.memory_purge_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.memory_purge_confirm,
                        (dialog, which) -> purge()
                )
                .show();
    }

    private void purge() {
        setBusy(true);
        binding.errorText.setVisibility(View.GONE);

        repository.purge(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                toast(R.string.memory_purged);
                loadAll();
            }

            @Override
            public void onError(String message, int statusCode) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                setBusy(false);

                if (handleAuth(statusCode)) {
                    return;
                }

                showError(message);
            }
        });
    }

    private void renderCandidates(
            List<MemoryCandidateResponse> items
    ) {
        binding.candidateContainer.removeAllViews();

        if (items == null || items.isEmpty()) {
            binding.noCandidatesText.setVisibility(View.VISIBLE);
            return;
        }

        binding.noCandidatesText.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (MemoryCandidateResponse candidate : items) {
            ItemMemoryCandidateBinding row =
                    ItemMemoryCandidateBinding.inflate(
                            inflater,
                            binding.candidateContainer,
                            false
                    );

            row.candidateTitle.setText(
                    getString(
                            R.string.memory_candidate_title,
                            MemoryOptions.fieldLabel(
                                    this,
                                    candidate.getField()
                            ),
                            MemoryOptions.valueLabel(
                                    this,
                                    candidate.getField(),
                                    candidate.getValue()
                            )
                    )
            );

            String evidence = candidate.getEvidence();

            if (evidence == null || evidence.isBlank()) {
                row.candidateEvidence.setVisibility(View.GONE);
            } else {
                row.candidateEvidence.setVisibility(View.VISIBLE);
                row.candidateEvidence.setText(
                        getString(
                                R.string.memory_candidate_evidence,
                                evidence,
                                candidate.getEvidenceCount()
                        )
                );
            }

            row.acceptButton.setOnClickListener(
                    view -> acceptCandidate(candidate)
            );
            row.rejectButton.setOnClickListener(
                    view -> rejectCandidate(candidate)
            );

            binding.candidateContainer.addView(row.getRoot());
        }
    }

    private void acceptCandidate(
            MemoryCandidateResponse candidate
    ) {
        if (busy) {
            return;
        }

        setBusy(true);
        binding.errorText.setVisibility(View.GONE);

        repository.acceptCandidate(
                candidate.getId(),
                new ApiCallback<StyleProfileResponse>() {
                    @Override
                    public void onSuccess(
                            StyleProfileResponse value
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        applyProfile(value);
                        toast(R.string.memory_candidate_accepted);
                        loadCandidates();
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setBusy(false);

                        if (handleAuth(statusCode)) {
                            return;
                        }

                        showError(message);

                        if (statusCode == 400) {
                            loadAll();
                        } else if (statusCode == 404) {
                            loadCandidates();
                        }
                    }
                }
        );
    }

    private void rejectCandidate(
            MemoryCandidateResponse candidate
    ) {
        if (busy) {
            return;
        }

        setBusy(true);
        binding.errorText.setVisibility(View.GONE);

        repository.rejectCandidate(
                candidate.getId(),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setBusy(false);
                        toast(R.string.memory_candidate_rejected);
                        loadCandidates();
                    }

                    @Override
                    public void onError(
                            String message,
                            int statusCode
                    ) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setBusy(false);

                        if (handleAuth(statusCode)) {
                            return;
                        }

                        showError(message);

                        if (statusCode == 404) {
                            loadCandidates();
                        }
                    }
                }
        );
    }

    private void setBusy(boolean value) {
        busy = value;

        boolean controlsEnabled = !value && profileLoaded;

        binding.saveButton.setEnabled(controlsEnabled);
        binding.purgeButton.setEnabled(controlsEnabled);
        binding.enabledSwitch.setEnabled(controlsEnabled);
        binding.retryButton.setEnabled(!value);

        for (ChipGroup group : chipGroups.values()) {
            for (int index = 0; index < group.getChildCount(); index++) {
                group.getChildAt(index).setEnabled(controlsEnabled);
            }
        }
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
        binding.retryButton.setVisibility(View.VISIBLE);
    }

    private boolean handleAuth(int statusCode) {
        if (statusCode == 401) {
            openLogin();
            return true;
        }

        return false;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
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
}