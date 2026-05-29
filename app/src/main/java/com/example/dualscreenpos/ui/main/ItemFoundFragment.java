package com.example.dualscreenpos.ui.main;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.SkuDetail;
import com.google.gson.Gson;

public class ItemFoundFragment extends Fragment {

    public static ItemFoundFragment newInstance(Bundle args) {
        ItemFoundFragment f = new ItemFoundFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_item_found, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        Bundle args = getArguments();
        if (args == null) return;

        ReturnRoute route = new Gson().fromJson(args.getString("route_json", ""), ReturnRoute.class);
        if (route == null) return;

        TextView tvItemName   = view.findViewById(R.id.tv_item_name);
        TextView tvSkuCode    = view.findViewById(R.id.tv_sku_code);
        TextView tvCategory   = view.findViewById(R.id.tv_category);
        TextView tvRfid       = view.findViewById(R.id.tv_rfid);
        TextView tvStatus     = view.findViewById(R.id.tv_status);
        TextView tvMrp        = view.findViewById(R.id.tv_mrp);
        TextView tvSalePrice  = view.findViewById(R.id.tv_sale_price);
        TextView tvGstLabel   = view.findViewById(R.id.tv_gst_label);
        TextView tvGstAmount  = view.findViewById(R.id.tv_gst_amount);
        TextView tvTotal      = view.findViewById(R.id.tv_total);
        Button   btnConfirm   = view.findViewById(R.id.btn_confirm);
        Button   btnCancel    = view.findViewById(R.id.btn_cancel);

        if (route.skuDetail != null) {
            SkuDetail sku = route.skuDetail;
            tvItemName.setText(sku.productName);
            tvSkuCode.setText(sku.skuCode);
            tvCategory.setText(sku.category);
            tvMrp.setText(String.format("₹%.2f", sku.mrp));
            tvMrp.setPaintFlags(tvMrp.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tvSalePrice.setText(String.format("₹%.2f", sku.salePrice));

            double gstAmount = sku.salePrice * sku.gstPercent / 100.0;
            double total = sku.salePrice + gstAmount;
            tvGstLabel.setText(String.format("GST (%.0f%%)", sku.gstPercent));
            tvGstAmount.setText(String.format("₹%.2f", gstAmount));
            tvTotal.setText(String.format("₹%.2f", total));
        }

        if (route.itemRecord != null) {
            tvRfid.setText("RFID: " + route.itemRecord.rfid);
            tvStatus.setText(route.itemRecord.status);
            applyStatusColor(tvStatus, route.itemRecord.status);
        }

        final double[] totalHolder = {0};
        if (route.skuDetail != null) {
            double gst = route.skuDetail.salePrice * route.skuDetail.gstPercent / 100.0;
            totalHolder[0] = route.skuDetail.salePrice + gst;
        }

        btnConfirm.setOnClickListener(v -> {
            String productName = route.skuDetail != null ? route.skuDetail.productName : "item";
            String totalStr = String.format("₹%.2f", totalHolder[0]);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Payment")
                    .setMessage("Product: " + productName
                            + "\nTotal: " + totalStr
                            + "\n\nProceed with checkout?")
                    .setPositiveButton("Confirm", (d, w) -> viewModel.confirmCheckoutItems(
                            java.util.Collections.singletonList(route)))
                    .setNegativeButton("Cancel", null)
                    .setCancelable(true)
                    .show();
        });
        btnCancel.setOnClickListener(v -> viewModel.postUiState(new MainViewModel.UiState.Idle()));
    }

    private void applyStatusColor(TextView tv, String status) {
        int bgColor, textColor;
        switch (status) {
            case "IN_STORE":
                bgColor   = Color.parseColor("#C8E6C9");
                textColor = Color.parseColor("#1B5E20");
                break;
            case "DISPATCHED":
                bgColor   = Color.parseColor("#BBDEFB");
                textColor = Color.parseColor("#0D47A1");
                break;
            default:
                bgColor   = Color.parseColor("#F5F5F5");
                textColor = Color.parseColor("#424242");
                break;
        }
        tv.setBackgroundColor(bgColor);
        tv.setTextColor(textColor);
    }
}
