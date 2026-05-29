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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

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
    private TextView tvCheckoutAlert, tvCheckoutHeader, tvCheckoutGst, tvCheckoutTotal;

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
        llCheckoutAlert  = view.findViewById(R.id.ll_checkout_alert);
        tvCheckoutAlert  = view.findViewById(R.id.tv_checkout_alert);
        llCheckoutItems  = view.findViewById(R.id.ll_checkout_items);
        tvCheckoutHeader = view.findViewById(R.id.tv_checkout_header);
        tvCheckoutGst    = view.findViewById(R.id.tv_checkout_gst);
        tvCheckoutTotal  = view.findViewById(R.id.tv_checkout_total);

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
                    view.findViewById(R.id.scroll_checkout).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.scroll_return).setVisibility(View.GONE);
                    btnAction.setText("CONFIRM CHECKOUT");
                } else {
                    view.findViewById(R.id.scroll_checkout).setVisibility(View.GONE);
                    view.findViewById(R.id.scroll_return).setVisibility(View.VISIBLE);
                    btnAction.setText("PROCEED WITH RETURN");
                    // Always fetch fresh bin data when Return tab is opened
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

        // Alert for excluded SOLD items
        if (!returnItems.isEmpty()) {
            llCheckoutAlert.setVisibility(View.VISIBLE);
            tvCheckoutAlert.setText(returnItems.size() + " already sold item(s) excluded — "
                    + "use the Return tab to process them.");
        } else {
            llCheckoutAlert.setVisibility(View.GONE);
        }

        tvCheckoutHeader.setText("Checkout Items (" + checkoutItems.size() + ")");
        llCheckoutItems.removeAllViews();

        double subtotal = 0, gstTotal = 0;
        for (ReturnRoute r : checkoutItems) {
            if (r.skuDetail == null) continue;
            double gst  = r.skuDetail.salePrice * r.skuDetail.gstPercent / 100.0;
            double line = r.skuDetail.salePrice + gst;
            subtotal  += r.skuDetail.salePrice;
            gstTotal  += gst;
            llCheckoutItems.addView(itemRow(r.skuDetail.productName,
                    String.format("₹%.2f", line)));
        }

        tvCheckoutGst.setText(String.format("₹%.2f", gstTotal));
        tvCheckoutTotal.setText(String.format("₹%.2f", subtotal + gstTotal));
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

        double total = grandTotal(checkoutItems);
        int count = checkoutItems.size();
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Payment")
                .setMessage(count + " item" + (count > 1 ? "s" : "")
                        + "\nTotal: " + String.format("₹%.2f", total)
                        + "\n\nProceed with checkout?")
                .setPositiveButton("Confirm", (d, w) -> viewModel.confirmCheckoutItems(checkoutItems))
                .setNegativeButton("Cancel", null)
                .show();
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
            tvReturnAlert.setText(inStoreItems.size() + " in-store item(s) not shown — "
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
