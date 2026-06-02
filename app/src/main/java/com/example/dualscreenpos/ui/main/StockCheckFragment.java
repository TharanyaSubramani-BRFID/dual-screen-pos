package com.example.dualscreenpos.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.SkuDetail;
import com.example.dualscreenpos.data.network.RetailApi;
import com.example.dualscreenpos.data.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StockCheckFragment extends Fragment {

    private MainViewModel viewModel;
    private LinearLayout llResults;
    private TextView tvStatus;
    private View progressBar;
    private EditText etSearch;

    private List<SkuDetail> allSkus = new ArrayList<>();
    private int[] skuCounts; // parallel array: in-store count per SKU

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stock_check, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        llResults   = view.findViewById(R.id.ll_results);
        tvStatus    = view.findViewById(R.id.tv_status);
        progressBar = view.findViewById(R.id.progress_bar);
        etSearch    = view.findViewById(R.id.et_search);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                viewModel.postUiState(new MainViewModel.UiState.Idle()));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filterAndRender(s.toString().trim()); }
        });

        loadSkusAndCounts();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadSkusAndCounts() {
        showLoading("Loading products…");
        String baseUrl = SettingsRepository.getInstance().getBaseUrl();
        RetailApi api  = RetailApi.getInstance(baseUrl);

        api.getSkus(new RetailApi.ApiCallback<List<SkuDetail>>() {
            @Override public void onSuccess(List<SkuDetail> skus) {
                if (skus == null || skus.isEmpty()) {
                    showStatus("No products found in the system.");
                    return;
                }
                allSkus    = skus;
                skuCounts  = new int[skus.size()];
                // initialise counts to -1 (meaning "still loading")
                for (int i = 0; i < skuCounts.length; i++) skuCounts[i] = -1;

                // render list immediately with "Loading…" badges, then update counts
                filterAndRender(etSearch.getText().toString().trim());
                fetchCountsInParallel(skus, api);
            }
            @Override public void onFailure(String error) {
                showStatus("Failed to load products.\n" + error);
            }
        });
    }

    private void fetchCountsInParallel(List<SkuDetail> skus, RetailApi api) {
        AtomicInteger pending = new AtomicInteger(skus.size());

        for (int i = 0; i < skus.size(); i++) {
            final int idx = i;
            api.getItemCountForSku(skus.get(i).id, new RetailApi.ApiCallback<Integer>() {
                @Override public void onSuccess(Integer count) {
                    skuCounts[idx] = count;
                    if (pending.decrementAndGet() == 0) {
                        // All counts fetched — re-render with real numbers
                        if (isAdded()) filterAndRender(etSearch.getText().toString().trim());
                    } else {
                        // Partial update for this row
                        if (isAdded()) updateBadgeForRow(idx);
                    }
                }
                @Override public void onFailure(String error) {
                    skuCounts[idx] = 0;
                    pending.decrementAndGet();
                }
            });
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void filterAndRender(String query) {
        hideStatus();
        llResults.removeAllViews();

        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < allSkus.size(); i++) {
            SkuDetail s = allSkus.get(i);
            if (query.isEmpty()
                    || s.productName.toLowerCase().contains(query.toLowerCase())
                    || (s.skuCode != null && s.skuCode.toLowerCase().contains(query.toLowerCase()))) {
                matches.add(i);
            }
        }

        if (matches.isEmpty()) {
            showStatus(allSkus.isEmpty() ? "" : "No products match \"" + query + "\"");
            return;
        }

        for (int idx : matches) {
            llResults.addView(buildSkuRow(idx));
        }
    }

    private View buildSkuRow(int idx) {
        SkuDetail sku = allSkus.get(idx);

        // Card container
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(8);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(8));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(dp(14), dp(12), dp(14), dp(12));

        // Left: product info
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(sku.productName);
        tvName.setTextColor(Color.parseColor("#1A1A2E"));
        tvName.setTextSize(15f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvMeta = new TextView(requireContext());
        String meta = (sku.skuCode != null ? sku.skuCode : "")
                + (sku.category != null && !sku.category.isEmpty() ? "  ·  " + sku.category : "");
        tvMeta.setText(meta);
        tvMeta.setTextColor(Color.parseColor("#757575"));
        tvMeta.setTextSize(12f);

        TextView tvPrice = new TextView(requireContext());
        tvPrice.setText(String.format("MRP ₹%.0f  ·  Sale ₹%.0f  ·  GST %.0f%%",
                sku.mrp, sku.salePrice, sku.gstPercent));
        tvPrice.setTextColor(Color.parseColor("#9E9E9E"));
        tvPrice.setTextSize(11f);

        info.addView(tvName);
        info.addView(tvMeta);
        info.addView(tvPrice);

        // Right: availability badge
        TextView tvBadge = new TextView(requireContext());
        tvBadge.setTag("badge_" + idx);
        tvBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        tvBadge.setTextSize(12f);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        badgeLp.setMarginStart(dp(8));
        tvBadge.setLayoutParams(badgeLp);
        applyBadgeStyle(tvBadge, skuCounts[idx]);

        inner.addView(info);
        inner.addView(tvBadge);
        card.addView(inner);
        return card;
    }

    private void updateBadgeForRow(int idx) {
        // Find badge by tag and update it without a full re-render
        View badge = llResults.findViewWithTag("badge_" + idx);
        if (badge instanceof TextView) {
            applyBadgeStyle((TextView) badge, skuCounts[idx]);
        }
    }

    private void applyBadgeStyle(TextView badge, int count) {
        if (count < 0) {
            badge.setText("Loading…");
            badge.setBackgroundColor(Color.parseColor("#F5F5F5"));
            badge.setTextColor(Color.parseColor("#9E9E9E"));
        } else if (count == 0) {
            badge.setText("OUT OF STOCK");
            badge.setBackgroundColor(Color.parseColor("#FFEBEE"));
            badge.setTextColor(Color.parseColor("#C62828"));
        } else {
            badge.setText(count + " IN STORE");
            badge.setBackgroundColor(Color.parseColor("#E8F5E9"));
            badge.setTextColor(Color.parseColor("#2E7D32"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showLoading(String msg) {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText(msg);
        tvStatus.setVisibility(View.VISIBLE);
        llResults.removeAllViews();
    }

    private void showStatus(String msg) {
        progressBar.setVisibility(View.GONE);
        tvStatus.setText(msg);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
