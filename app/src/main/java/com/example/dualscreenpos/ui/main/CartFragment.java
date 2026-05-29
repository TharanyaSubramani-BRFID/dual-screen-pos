package com.example.dualscreenpos.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.ReturnRoute;

import java.util.ArrayList;
import java.util.List;

public class CartFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        LinearLayout llCartItems  = view.findViewById(R.id.ll_cart_items);
        TextView tvHeader         = view.findViewById(R.id.tv_cart_header);
        TextView tvTotalGst       = view.findViewById(R.id.tv_total_gst);
        TextView tvGrandTotal     = view.findViewById(R.id.tv_grand_total);
        Button btnScanMore        = view.findViewById(R.id.btn_scan_more);
        Button btnConfirm         = view.findViewById(R.id.btn_confirm);
        Button btnCancel          = view.findViewById(R.id.btn_cancel);

        viewModel.getUiStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof MainViewModel.UiState.CartReady) {
                populateCart(((MainViewModel.UiState.CartReady) state).cart,
                        llCartItems, tvHeader, tvTotalGst, tvGrandTotal);
            }
        });

        btnScanMore.setOnClickListener(v -> viewModel.addMoreScan());

        btnConfirm.setOnClickListener(v -> {
            MainViewModel.UiState current = viewModel.getUiStateLiveData().getValue();
            List<ReturnRoute> cart = current instanceof MainViewModel.UiState.CartReady
                    ? ((MainViewModel.UiState.CartReady) current).cart : new ArrayList<>();
            if (cart.isEmpty()) return;

            double grandTotal = grandTotal(cart);
            int count = cart.size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Payment")
                    .setMessage(count + " item" + (count > 1 ? "s" : "")
                            + "\nTotal: " + String.format("₹%.2f", grandTotal)
                            + "\n\nProceed with checkout?")
                    .setPositiveButton("Confirm", (d, w) -> viewModel.confirmCheckoutItems(cart))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnCancel.setOnClickListener(v ->
                viewModel.cancelAndClearCart());
    }

    private void populateCart(List<ReturnRoute> cart,
                              LinearLayout container, TextView header,
                              TextView tvGst, TextView tvTotal) {
        container.removeAllViews();
        double totalGst = 0, grandTotal = 0;

        for (ReturnRoute r : cart) {
            if (r.skuDetail == null) continue;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(6);
            row.setLayoutParams(rp);

            TextView name = new TextView(requireContext());
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            name.setText(r.skuDetail.productName);
            name.setTextColor(Color.parseColor("#1A1A2E"));
            name.setTextSize(14f);

            TextView price = new TextView(requireContext());
            price.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            price.setText(String.format("₹%.2f", r.skuDetail.salePrice));
            price.setTextColor(Color.parseColor("#1A1A2E"));
            price.setTextSize(14f);

            row.addView(name);
            row.addView(price);
            container.addView(row);

            double gst = r.skuDetail.salePrice * r.skuDetail.gstPercent / 100.0;
            totalGst   += gst;
            grandTotal += r.skuDetail.salePrice + gst;
        }

        header.setText("Cart (" + cart.size() + " item" + (cart.size() > 1 ? "s" : "") + ")");
        tvGst.setText(String.format("₹%.2f", totalGst));
        tvTotal.setText(String.format("₹%.2f", grandTotal));
    }

    private double grandTotal(List<ReturnRoute> cart) {
        double total = 0;
        for (ReturnRoute r : cart) {
            if (r.skuDetail != null)
                total += r.skuDetail.salePrice * (1 + r.skuDetail.gstPercent / 100.0);
        }
        return total;
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
