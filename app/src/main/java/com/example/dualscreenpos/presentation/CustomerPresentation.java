package com.example.dualscreenpos.presentation;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import com.example.dualscreenpos.R;

public class CustomerPresentation extends Presentation {

    private TextView tvStatus;
    private TextView tvItemName;
    private TextView tvPrice;
    private TextView tvBottomLabel;
    private View layoutRoot;

    public CustomerPresentation(Context ctx, Display display) {
        super(ctx, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.presentation_customer);
        layoutRoot = findViewById(R.id.layout_root);
        tvStatus = findViewById(R.id.tv_status);
        tvItemName = findViewById(R.id.tv_item_name);
        tvPrice = findViewById(R.id.tv_price);
        tvBottomLabel = findViewById(R.id.tv_bottom_label);
    }

    public void showIdle() {
        layoutRoot.setBackgroundColor(Color.parseColor("#FFFFFF"));
        tvStatus.setTextSize(44f);
        tvStatus.setText("Welcome");
        tvItemName.setVisibility(View.GONE);
        tvPrice.setVisibility(View.GONE);
        tvBottomLabel.setText("Please place item on reader when prompted");
    }

    public void showScanning() {
        tvStatus.setTextSize(44f);
        tvStatus.setText("Please wait...");
        tvItemName.setVisibility(View.GONE);
        tvPrice.setVisibility(View.GONE);
        tvBottomLabel.setText("SCAN IN PROGRESS");
    }

    public void showItemFound(String name, String formattedPrice) {
        tvStatus.setText(name);
        tvStatus.setTextSize(36f);
        tvItemName.setVisibility(View.VISIBLE);
        tvItemName.setText(name);
        tvPrice.setVisibility(View.VISIBLE);
        tvPrice.setText(formattedPrice);
        tvBottomLabel.setText("Awaiting confirmation");
    }

    public void showProcessing() {
        tvStatus.setTextSize(44f);
        tvStatus.setText("Processing your order…");
        tvItemName.setVisibility(View.GONE);
        tvPrice.setVisibility(View.GONE);
        tvBottomLabel.setText("Please wait");
    }

    public void showSuccess(String itemName, String type) {
        String title, bg;
        switch (type != null ? type : "CHECKOUT") {
            case "RETURN_TO_STORE":
                title = "Returned to Store";
                bg    = "#E3F2FD";
                break;
            case "RETURN_TO_WAREHOUSE":
                title = "Returned to Warehouse";
                bg    = "#FFF8E1";
                break;
            default:
                title = "Checkout Successful!";
                bg    = "#E8F5E9";
                break;
        }
        layoutRoot.setBackgroundColor(Color.parseColor(bg));
        tvStatus.setText(title);
        tvItemName.setVisibility(View.VISIBLE);
        tvItemName.setText(itemName);
        tvPrice.setVisibility(View.GONE);
        tvBottomLabel.setText("Thank you");

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                layoutRoot.setBackgroundColor(Color.parseColor("#FFFFFF")), 4000);
    }

    public void showError(String reason) {
        tvStatus.setText("Please see staff");
        tvItemName.setVisibility(View.GONE);
        tvPrice.setVisibility(View.GONE);
        tvBottomLabel.setText(reason);
    }

    @Override
    public void onDisplayRemoved() {
        dismiss();
    }
}
