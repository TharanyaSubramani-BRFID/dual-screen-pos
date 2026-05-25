package com.example.dualscreenpos.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;

public class SuccessFragment extends Fragment {

    public static SuccessFragment newInstance(String itemName) {
        SuccessFragment f = new SuccessFragment();
        Bundle args = new Bundle();
        args.putString("item_name", itemName);
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
        ((TextView) view.findViewById(R.id.tv_success_item)).setText(itemName);

        ImageView ivCheck = view.findViewById(R.id.iv_check);
        ivCheck.setScaleX(0f);
        ivCheck.setScaleY(0f);
        ivCheck.animate().scaleX(1f).scaleY(1f).setDuration(400).start();

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> viewModel.postUiState(new MainViewModel.UiState.Idle()), 4000);
    }
}
