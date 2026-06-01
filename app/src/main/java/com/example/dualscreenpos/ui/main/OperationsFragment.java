package com.example.dualscreenpos.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.app.Dialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.StorageBin;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class OperationsFragment extends Fragment {

    private MainViewModel viewModel;
    private int activeTab = 0; // 0=checkout, 1=return

    // Checkout views
    private LinearLayout llCheckoutAlert, llCheckoutItems;
    private TextView tvCheckoutAlert, tvCheckoutHeader, tvCheckoutSubtotal, tvCheckoutGst, tvCheckoutTotal;
    private ScrollView svCheckoutItems;
    private View checkoutSummary;

    // Return views
    private LinearLayout llReturnAlert, llReturnItems, llWarehouse;
    private TextView tvReturnAlert, tvReturnHeader;
    private RadioGroup rgReturnType;
    private RadioButton rbStore, rbWarehouse;
    private Spinner spinnerRack, spinnerBin;
    private EditText etReason;

    // Shared
    private Button btnAction;

    // Bin data
    private List<StorageBin> currentBins = new ArrayList<>();
    private List<String> currentRacks = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_operations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        bindViews(view);
        setupTabs(view);
        setupReturnTypeRadio();
        setupButtons(view);

        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof MainViewModel.UiState.CartReady) {
                List<ReturnRoute> cart = ((MainViewModel.UiState.CartReady) state).cart;
                populateCheckoutTab(cart);
                populateReturnItems(cart);
            }
        });

        viewModel.getBinsLiveData().observe(getViewLifecycleOwner(), bins -> {
            currentBins = bins != null ? bins : new ArrayList<>();
            currentRacks = viewModel.getAvailableRacks();
            refreshRackSpinner();
        });
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews(View view) {
        llCheckoutAlert    = view.findViewById(R.id.ll_checkout_alert);
        tvCheckoutAlert    = view.findViewById(R.id.tv_checkout_alert);
        llCheckoutItems    = view.findViewById(R.id.ll_checkout_items);
        tvCheckoutHeader   = view.findViewById(R.id.tv_checkout_header);
        tvCheckoutSubtotal = view.findViewById(R.id.tv_checkout_subtotal);
        tvCheckoutGst      = view.findViewById(R.id.tv_checkout_gst);
        tvCheckoutTotal    = view.findViewById(R.id.tv_checkout_total);
        svCheckoutItems    = view.findViewById(R.id.sv_checkout_items);
        checkoutSummary    = view.findViewById(R.id.checkout_summary);

        llReturnAlert  = view.findViewById(R.id.ll_return_alert);
        tvReturnAlert  = view.findViewById(R.id.tv_return_alert);
        llReturnItems  = view.findViewById(R.id.ll_return_items);
        tvReturnHeader = view.findViewById(R.id.tv_return_header);
        rgReturnType   = view.findViewById(R.id.rg_return_type);
        rbStore        = view.findViewById(R.id.rb_store);
        rbWarehouse    = view.findViewById(R.id.rb_warehouse);
        llWarehouse    = view.findViewById(R.id.ll_warehouse);
        spinnerRack    = view.findViewById(R.id.spinner_rack);
        spinnerBin     = view.findViewById(R.id.spinner_bin);
        etReason       = view.findViewById(R.id.et_reason);

        btnAction = view.findViewById(R.id.btn_action);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void setupTabs(View view) {
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("CHECKOUT"));
        tabLayout.addTab(tabLayout.newTab().setText("RETURN"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                if (activeTab == 0) {
                    view.findViewById(R.id.card_checkout).setVisibility(View.VISIBLE);
                    checkoutSummary.setVisibility(View.VISIBLE);
                    view.findViewById(R.id.scroll_return).setVisibility(View.GONE);
                    btnAction.setText("CONFIRM CHECKOUT");
                } else {
                    view.findViewById(R.id.card_checkout).setVisibility(View.GONE);
                    checkoutSummary.setVisibility(View.GONE);
                    view.findViewById(R.id.scroll_return).setVisibility(View.VISIBLE);
                    btnAction.setText("PROCEED WITH RETURN");
                    viewModel.forceFetchBins();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ── Return type radio ─────────────────────────────────────────────────────

    private void setupReturnTypeRadio() {
        rgReturnType.setOnCheckedChangeListener((group, checkedId) -> {
            llWarehouse.setVisibility(checkedId == R.id.rb_warehouse ? View.VISIBLE : View.GONE);
            // Re-populate return items: warehouse mode shows IN_STORE too
            MainViewModel.UiState s = viewModel.getUiStateLiveData().getValue();
            if (s instanceof MainViewModel.UiState.CartReady) {
                populateReturnItems(((MainViewModel.UiState.CartReady) s).cart);
            }
        });
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private void setupButtons(View view) {
        view.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                viewModel.cancelAndClearCart());

        view.findViewById(R.id.btn_scan_more).setOnClickListener(v ->
                viewModel.addMoreScan());

        btnAction.setOnClickListener(v -> {
            if (activeTab == 0) handleCheckout();
            else handleReturn();
        });
    }

    // ── Checkout tab logic ────────────────────────────────────────────────────

    private void populateCheckoutTab(List<ReturnRoute> cart) {
        List<ReturnRoute> checkoutItems = filterByType(cart, "CHECKOUT");
        List<ReturnRoute> returnItems   = filterByType(cart, "RETURN");

        if (!returnItems.isEmpty()) {
            llCheckoutAlert.setVisibility(View.VISIBLE);
            tvCheckoutAlert.setText(returnItems.size() + " already sold item(s) excluded;"
                    + "use the Return tab to process them.");
        } else {
            llCheckoutAlert.setVisibility(View.GONE);
        }

        tvCheckoutHeader.setText("Checkout Items (" + checkoutItems.size() + ")");
        llCheckoutItems.removeAllViews();

        // Group by SKU so same product shows ×N with one delete button
        Map<Integer, List<ReturnRoute>> grouped = new LinkedHashMap<>();
        for (ReturnRoute r : checkoutItems) {
            if (r.skuDetail != null) {
                grouped.computeIfAbsent(r.skuDetail.id, k -> new ArrayList<>()).add(r);
            }
        }

        double subtotal = 0, gstTotal = 0;
        for (Map.Entry<Integer, List<ReturnRoute>> entry : grouped.entrySet()) {
            List<ReturnRoute> group = entry.getValue();
            ReturnRoute first = group.get(0);
            int qty          = group.size();
            double unitPrice = first.skuDetail.salePrice;
            double gstPct    = first.skuDetail.gstPercent;
            double gstUnit   = unitPrice * gstPct / 100.0;
            double lineTotal = (unitPrice + gstUnit) * qty;

            subtotal += unitPrice * qty;
            gstTotal += gstUnit * qty;

            llCheckoutItems.addView(checkoutItemRow(
                    first.skuDetail.productName, qty,
                    unitPrice, gstPct, lineTotal,
                    first.skuDetail.id));
        }

        tvCheckoutSubtotal.setText(String.format("₹%.2f", subtotal));
        tvCheckoutGst.setText(String.format("₹%.2f", gstTotal));
        tvCheckoutTotal.setText(String.format("₹%.2f", subtotal + gstTotal));

        // Auto-scroll to show the most recently added item
        svCheckoutItems.post(() -> svCheckoutItems.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private View checkoutItemRow(String name, int qty, double unitPrice,
                                  double gstPct, double lineTotal, int skuId) {
        // Outer horizontal row
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8);
        row.setLayoutParams(rp);

        // Left: product name + unit price label
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(name + (qty > 1 ? "  ×" + qty : ""));
        tvName.setTextColor(Color.parseColor("#1A1A2E"));
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSub = new TextView(requireContext());
        tvSub.setText(String.format("₹%.2f  (GST %.1f%%)", unitPrice, gstPct));
        tvSub.setTextColor(Color.parseColor("#757575"));
        tvSub.setTextSize(12f);

        info.addView(tvName);
        info.addView(tvSub);

        // Right: line total
        TextView tvPrice = new TextView(requireContext());
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        priceLp.setMarginStart(dp(8));
        tvPrice.setLayoutParams(priceLp);
        tvPrice.setText(String.format("₹%.2f", lineTotal));
        tvPrice.setTextColor(Color.parseColor("#1A1A2E"));
        tvPrice.setTextSize(14f);
        tvPrice.setTypeface(null, android.graphics.Typeface.BOLD);

        // Delete button
        ImageButton btnDelete = new ImageButton(requireContext());
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        delLp.setMarginStart(dp(8));
        btnDelete.setLayoutParams(delLp);
        btnDelete.setImageResource(R.drawable.ic_delete);
        btnDelete.setBackground(null);
        btnDelete.setContentDescription("Remove one " + name);
        btnDelete.setOnClickListener(v -> viewModel.removeOneCartItemBySku(skuId));

        row.addView(info);
        row.addView(tvPrice);
        row.addView(btnDelete);
        return row;
    }

    private void handleCheckout() {
        MainViewModel.UiState state = viewModel.getUiStateLiveData().getValue();
        List<ReturnRoute> cart = state instanceof MainViewModel.UiState.CartReady
                ? ((MainViewModel.UiState.CartReady) state).cart : new ArrayList<>();
        List<ReturnRoute> checkoutItems = filterByType(cart, "CHECKOUT");

        if (checkoutItems.isEmpty()) {
            showAlert("Nothing to Checkout",
                    "All scanned items are already sold. Use the Return tab.");
            return;
        }

        showCardPaymentDialog(checkoutItems, grandTotal(checkoutItems));
    }

    private void showCardPaymentDialog(List<ReturnRoute> checkoutItems, double total) {
        Dialog paymentDialog = new Dialog(requireContext());
        paymentDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        paymentDialog.setContentView(R.layout.dialog_card_payment);
        paymentDialog.setCancelable(true);

        TextView tvAmount         = paymentDialog.findViewById(R.id.tv_payment_amount);
        TextInputLayout tilPin    = paymentDialog.findViewById(R.id.til_pin);
        android.widget.EditText etCard = paymentDialog.findViewById(R.id.et_card_number);
        android.widget.EditText etPin  = paymentDialog.findViewById(R.id.et_pin);
        TextView tvPinHint        = paymentDialog.findViewById(R.id.tv_pin_hint);
        Button btnCancel          = paymentDialog.findViewById(R.id.btn_payment_cancel);
        Button btnConfirm         = paymentDialog.findViewById(R.id.btn_payment_confirm);

        tvAmount.setText(String.format("₹%.2f", total));

        // PIN and Confirm start disabled
        tilPin.setEnabled(false);
        tilPin.setAlpha(0.45f);
        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);

        // Card number: strip spaces → limit 16 digits → reformat XXXX XXXX XXXX XXXX
        etCard.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;

                String digits = s.toString().replace(" ", "");
                if (digits.length() > 16) digits = digits.substring(0, 16);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && i % 4 == 0) formatted.append(' ');
                    formatted.append(digits.charAt(i));
                }
                s.replace(0, s.length(), formatted.toString());
                formatting = false;

                boolean cardComplete = digits.length() == 16;
                tilPin.setEnabled(cardComplete);
                tilPin.setAlpha(cardComplete ? 1.0f : 0.45f);
                tvPinHint.setVisibility(cardComplete ? android.view.View.GONE : android.view.View.VISIBLE);
                if (!cardComplete) {
                    etPin.setText("");
                    btnConfirm.setEnabled(false);
                    btnConfirm.setAlpha(0.5f);
                }
            }
        });

        etPin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean ready = s.length() == 3;
                btnConfirm.setEnabled(ready);
                btnConfirm.setAlpha(ready ? 1.0f : 0.5f);
            }
        });

        btnCancel.setOnClickListener(v -> paymentDialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            paymentDialog.dismiss();
            viewModel.confirmCheckoutItems(checkoutItems);
        });

        paymentDialog.show();
        if (paymentDialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.50);
            paymentDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }


    // ── Return tab logic ──────────────────────────────────────────────────────

    private void populateReturnItems(List<ReturnRoute> cart) {
        boolean warehouseMode = rbWarehouse != null && rbWarehouse.isChecked();

        List<ReturnRoute> soldItems    = filterByType(cart, "RETURN");    // SOLD items
        List<ReturnRoute> inStoreItems = filterByType(cart, "CHECKOUT");  // IN_STORE items

        // Return to Store: only SOLD eligible
        // Return to Warehouse: SOLD + IN_STORE eligible
        List<ReturnRoute> visibleItems = new ArrayList<>(soldItems);
        if (warehouseMode) visibleItems.addAll(inStoreItems);

        // Alert for excluded items
        if (!warehouseMode && !inStoreItems.isEmpty()) {
            llReturnAlert.setVisibility(View.VISIBLE);
            tvReturnAlert.setText(inStoreItems.size() + " in-store item(s) not shown;"
                    + "switch to \"Return to Warehouse\" to include them.");
        } else {
            llReturnAlert.setVisibility(View.GONE);
        }

        tvReturnHeader.setText("Return Items (" + visibleItems.size() + ")");
        llReturnItems.removeAllViews();

        for (ReturnRoute r : visibleItems) {
            String name   = r.skuDetail != null ? r.skuDetail.productName : r.itemRecord.rfid;
            String status = r.itemRecord != null ? r.itemRecord.status : "";
            llReturnItems.addView(itemRow(name, status));
        }
    }

    private void handleReturn() {
        MainViewModel.UiState state = viewModel.getUiStateLiveData().getValue();
        List<ReturnRoute> cart = state instanceof MainViewModel.UiState.CartReady
                ? ((MainViewModel.UiState.CartReady) state).cart : new ArrayList<>();

        boolean warehouseMode = rbWarehouse.isChecked();
        List<ReturnRoute> returnItems = new ArrayList<>(filterByType(cart, "RETURN")); // SOLD
        if (warehouseMode) returnItems.addAll(filterByType(cart, "CHECKOUT")); // + IN_STORE

        if (returnItems.isEmpty()) {
            showAlert("Nothing to Return",
                    "No sold items in the cart. Scan sold items to process a return.");
            return;
        }

        String reason = etReason.getText().toString().trim();
        if (reason.isEmpty()) {
            etReason.setError("Return reason is required");
            etReason.requestFocus();
            return;
        }
        etReason.setError(null);

        if (rbWarehouse.isChecked()) {
            if (currentRacks.isEmpty()) {
                showAlert("No Bins Available",
                        "Could not load warehouse bins. Check backend connection.");
                return;
            }
            int rackPos = spinnerRack.getSelectedItemPosition();
            int binPos  = spinnerBin.getSelectedItemPosition();
            if (rackPos < 0 || binPos < 0) {
                showAlert("Selection Required", "Please select a rack and bin.");
                return;
            }
            String selectedRack = currentRacks.get(rackPos);
            List<StorageBin> binsForRack = viewModel.getBinsForRack(selectedRack);
            if (binsForRack.isEmpty()) {
                showAlert("No Bins", "No bins found for the selected rack.");
                return;
            }
            StorageBin selectedBin = binsForRack.get(binPos);

            int available = selectedBin.capacity > 0
                    ? selectedBin.capacity - selectedBin.itemCount : Integer.MAX_VALUE;
            if (returnItems.size() > available) {
                showAlert("Bin Capacity Exceeded",
                        "Bin " + selectedBin.rfid + " has only " + available
                                + " space(s) available. You're returning " + returnItems.size() + " item(s).");
                return;
            }

            int count = returnItems.size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Return to Warehouse")
                    .setMessage(count + " item" + (count > 1 ? "s" : "")
                            + "\nRack: " + selectedRack
                            + "\nBin: " + selectedBin.rfid
                            + "\n\nProceed?")
                    .setPositiveButton("Confirm",
                            (d, w) -> viewModel.confirmReturnToWarehouse(
                                    returnItems, reason, selectedBin.rfid, selectedRack))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            int count = returnItems.size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Return to Store")
                    .setMessage(count + " item" + (count > 1 ? "s" : "")
                            + "\nReason: " + reason + "\n\nProceed?")
                    .setPositiveButton("Confirm",
                            (d, w) -> viewModel.confirmReturnToStore(returnItems, reason))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    // ── Rack / Bin spinners ───────────────────────────────────────────────────

    private void refreshRackSpinner() {
        if (currentRacks.isEmpty()) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, currentRacks);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRack.setAdapter(adapter);

        spinnerRack.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshBinSpinner(currentRacks.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        if (!currentRacks.isEmpty()) refreshBinSpinner(currentRacks.get(0));
    }

    private void refreshBinSpinner(String rackId) {
        List<StorageBin> bins = viewModel.getBinsForRack(rackId);
        List<String> labels = new ArrayList<>();
        for (StorageBin b : bins) labels.add(b.getDisplayLabel());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBin.setAdapter(adapter);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ReturnRoute> filterByType(List<ReturnRoute> cart, String type) {
        List<ReturnRoute> result = new ArrayList<>();
        for (ReturnRoute r : cart) if (type.equals(r.returnType)) result.add(r);
        return result;
    }

    private double grandTotal(List<ReturnRoute> items) {
        double total = 0;
        for (ReturnRoute r : items) {
            if (r.skuDetail != null)
                total += r.skuDetail.salePrice * (1 + r.skuDetail.gstPercent / 100.0);
        }
        return total;
    }

    private View itemRow(String name, String right) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(5);
        row.setLayoutParams(rp);

        TextView tvName = new TextView(requireContext());
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setText(name);
        tvName.setTextColor(Color.parseColor("#1A1A2E"));
        tvName.setTextSize(13f);

        TextView tvRight = new TextView(requireContext());
        tvRight.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tvRight.setText(right);
        tvRight.setTextColor(Color.parseColor("#757575"));
        tvRight.setTextSize(13f);

        row.addView(tvName);
        row.addView(tvRight);
        return row;
    }

    private void showAlert(String title, String msg) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title).setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
