package com.example.dualscreenpos.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;

public class ErrorFragment extends Fragment {

    public static ErrorFragment newInstance(String message) {
        ErrorFragment f = new ErrorFragment();
        Bundle args = new Bundle();
        args.putString("error_message", message);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_error, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        String msg = getArguments() != null ? getArguments().getString("error_message", "") : "";
        ((TextView) view.findViewById(R.id.tv_error_message)).setText(msg);

        // TRY AGAIN — if cart has items, go back to operations screen; otherwise go idle
        view.findViewById(R.id.btn_try_again).setOnClickListener(v ->
                viewModel.resumeCart());
        // CANCEL — clear cart and go to idle
        view.findViewById(R.id.btn_cancel_error).setOnClickListener(v ->
                viewModel.cancelAndClearCart());
    }
}
