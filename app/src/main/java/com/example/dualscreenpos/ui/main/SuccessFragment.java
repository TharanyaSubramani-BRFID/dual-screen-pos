package com.example.dualscreenpos.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;

public class SuccessFragment extends Fragment {

    public static SuccessFragment newInstance(String itemName, String type) {
        SuccessFragment f = new SuccessFragment();
        Bundle args = new Bundle();
        args.putString("item_name", itemName);
        args.putString("type", type != null ? type : "CHECKOUT");
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_success, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        String itemName = getArguments() != null ? getArguments().getString("item_name", "") : "";
        String type     = getArguments() != null ? getArguments().getString("type", "CHECKOUT") : "CHECKOUT";

        View root          = view.findViewById(R.id.layout_success_root);
        ImageView ivIcon   = view.findViewById(R.id.iv_success_icon);
        TextView tvTitle   = view.findViewById(R.id.tv_success_title);
        TextView tvItem    = view.findViewById(R.id.tv_success_item);
        TextView tvTimer   = view.findViewById(R.id.tv_return_timer);

        applyType(root, ivIcon, tvTitle, type);
        tvItem.setText(itemName);

        // ── Bounce icon in ────────────────────────────────────────────────────
        ivIcon.setScaleX(0f);
        ivIcon.setScaleY(0f);
        ivIcon.animate()
                .scaleX(1.25f).scaleY(1.25f)
                .setDuration(350)
                .setInterpolator(new OvershootInterpolator(3f))
                .withEndAction(() ->
                        ivIcon.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(180)
                                .start())
                .start();

        // ── Fade + slide title in ─────────────────────────────────────────────
        tvTitle.setAlpha(0f);
        tvTitle.setTranslationY(24f);
        tvTitle.animate().alpha(1f).translationY(0f)
                .setDuration(380).setStartDelay(220).start();

        // ── Fade subtitle and timer ───────────────────────────────────────────
        tvItem.setAlpha(0f);
        tvItem.animate().alpha(1f).setDuration(300).setStartDelay(380).start();

        tvTimer.setAlpha(0f);
        tvTimer.animate().alpha(1f).setDuration(300).setStartDelay(500).start();

        // ── Auto-return to idle after 4 s ─────────────────────────────────────
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> viewModel.postUiState(new MainViewModel.UiState.Idle()), 4000);
    }

    private void applyType(View root, ImageView ivIcon, TextView tvTitle, String type) {
        switch (type) {
            case "RETURN_TO_STORE":
                root.setBackgroundColor(Color.parseColor("#E3F2FD"));
                ivIcon.setImageResource(R.drawable.ic_success_return_store);
                tvTitle.setTextColor(Color.parseColor("#1565C0"));
                tvTitle.setText("Returned to Store\nSuccessfully!");
                break;

            case "RETURN_TO_WAREHOUSE":
                root.setBackgroundColor(Color.parseColor("#FFF8E1"));
                ivIcon.setImageResource(R.drawable.ic_success_return_warehouse);
                tvTitle.setTextColor(Color.parseColor("#E65100"));
                tvTitle.setText("Returned to\nWarehouse Successfully!");
                break;

            default: // CHECKOUT
                root.setBackgroundColor(Color.parseColor("#E8F5E9"));
                ivIcon.setImageResource(R.drawable.ic_success_checkout);
                tvTitle.setTextColor(Color.parseColor("#2E7D32"));
                tvTitle.setText("Checkout\nSuccessful!");
                break;
        }
    }
}
