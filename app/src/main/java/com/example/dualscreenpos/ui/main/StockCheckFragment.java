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
    private static final int PAGE_SIZE = 10;
    private int displayedCount = PAGE_SIZE;

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
            @Override public void afterTextChanged(Editable s) {
                displayedCount = PAGE_SIZE;
                filterAndRender(s.toString().trim());
            }
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

        llResults.addView(buildHeaderRow());

        int showUpTo = Math.min(displayedCount, matches.size());
        for (int i = 0; i < showUpTo; i++) {
            llResults.addView(buildSkuRow(matches.get(i)));
        }

        if (showUpTo < matches.size()) {
            int remaining = matches.size() - showUpTo;
            llResults.addView(buildLoadMoreButton(query, remaining));
        }
    }

    private View buildLoadMoreButton(String query, int remaining) {
        android.widget.Button btn = new android.widget.Button(requireContext());
        btn.setText("Load More");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(dp(32), dp(10), dp(32), dp(10));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#1A1A2E"));
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(12);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            displayedCount += PAGE_SIZE;
            filterAndRender(query);
        });
        return btn;
    }

    // Column weights: name gets 3, each data column gets 1.2, badge wraps
    private static final float W_NAME  = 3f;
    private static final float W_COL   = 1.2f;

    private View buildHeaderRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setBackgroundColor(Color.parseColor("#F5F5F5"));

        String[] headers = {"Product Name", "SKU Code", "Category", "MRP", "Sale Price", "GST"};
        float[]  weights = {W_NAME, W_COL, W_COL, W_COL, W_COL, W_COL};

        for (int i = 0; i < headers.length; i++) {
            TextView tv = new TextView(requireContext());
            tv.setText(headers[i]);
            tv.setTextSize(11f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(Color.parseColor("#757575"));
            tv.setGravity(i == 0 ? android.view.Gravity.START : android.view.Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, weights[i]));
            row.addView(tv);
        }

        // Placeholder to align with badge column
        TextView tvStock = new TextView(requireContext());
        tvStock.setText("Stock");
        tvStock.setTextSize(11f);
        tvStock.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStock.setTextColor(Color.parseColor("#757575"));
        tvStock.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams stockLp = new LinearLayout.LayoutParams(dp(100),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stockLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tvStock.setLayoutParams(stockLp);
        row.addView(tvStock);

        return row;
    }

    private View buildSkuRow(int idx) {
        SkuDetail sku = allSkus.get(idx);

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(4);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(8));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Product name — left-aligned, bold
        TextView tvName = makeCell(sku.productName, W_NAME, android.view.Gravity.START, 14f,
                Color.parseColor("#1A1A2E"), android.graphics.Typeface.BOLD);

        // SKU code
        TextView tvSku = makeCell(
                sku.skuCode != null ? sku.skuCode : "—", W_COL,
                android.view.Gravity.CENTER, 13f, Color.parseColor("#424242"),
                android.graphics.Typeface.NORMAL);

        // Category
        TextView tvCat = makeCell(
                sku.category != null && !sku.category.isEmpty() ? sku.category : "—", W_COL,
                android.view.Gravity.CENTER, 13f, Color.parseColor("#424242"),
                android.graphics.Typeface.NORMAL);

        // MRP
        TextView tvMrp = makeCell(
                String.format("₹%.0f", sku.mrp), W_COL,
                android.view.Gravity.CENTER, 13f, Color.parseColor("#424242"),
                android.graphics.Typeface.NORMAL);

        // Sale price
        TextView tvSale = makeCell(
                String.format("₹%.0f", sku.salePrice), W_COL,
                android.view.Gravity.CENTER, 13f, Color.parseColor("#2E7D32"),
                android.graphics.Typeface.BOLD);

        // GST
        TextView tvGst = makeCell(
                String.format("%.0f%%", sku.gstPercent), W_COL,
                android.view.Gravity.CENTER, 13f, Color.parseColor("#424242"),
                android.graphics.Typeface.NORMAL);

        // Stock status — plain coloured text, no background
        TextView tvBadge = new TextView(requireContext());
        tvBadge.setTag("badge_" + idx);
        tvBadge.setTextSize(12f);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(100),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tvBadge.setLayoutParams(badgeLp);
        applyBadgeStyle(tvBadge, skuCounts[idx]);

        row.addView(tvName);
        row.addView(tvSku);
        row.addView(tvCat);
        row.addView(tvMrp);
        row.addView(tvSale);
        row.addView(tvGst);
        row.addView(tvBadge);
        card.addView(row);
        return card;
    }

    private TextView makeCell(String text, float weight, int gravity,
                              float textSizeSp, int color, int typefaceStyle) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(textSizeSp);
        tv.setTextColor(color);
        tv.setTypeface(null, typefaceStyle);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return tv;
    }

    private void updateBadgeForRow(int idx) {
        // Find badge by tag and update it without a full re-render
        View badge = llResults.findViewWithTag("badge_" + idx);
        if (badge instanceof TextView) {
            applyBadgeStyle((TextView) badge, skuCounts[idx]);
        }
    }

    private void applyBadgeStyle(TextView badge, int count) {
        badge.setBackgroundColor(Color.TRANSPARENT);
        if (count < 0) {
            badge.setText("Loading…");
            badge.setTextColor(Color.parseColor("#9E9E9E"));
        } else if (count == 0) {
            badge.setText("Out of Stock");
            badge.setTextColor(Color.parseColor("#C62828"));
        } else {
            badge.setText(count + " In Store");
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
