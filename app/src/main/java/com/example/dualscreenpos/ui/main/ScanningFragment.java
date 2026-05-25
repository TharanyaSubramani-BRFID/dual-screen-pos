package com.example.dualscreenpos.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;

public class ScanningFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanning, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!SettingsRepository.getInstance().isMockMode()) return;

        View btnMockScan = view.findViewById(R.id.btn_mock_scan);
        btnMockScan.setVisibility(View.VISIBLE);

        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String[] epcs = RfidCardReaderManager.getInstance().getMockEpcs();

        // Build display labels: "EPC #1  •  ...360001"
        String[] labels = new String[epcs.length];
        for (int i = 0; i < epcs.length; i++) {
            labels[i] = "EPC #" + (i + 1) + "   •   " + epcs[i];
        }

        btnMockScan.setOnClickListener(v -> {
            if (!isAdded()) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Mock EPC")
                    .setItems(labels, (dialog, which) -> {
                        btnMockScan.setEnabled(false);
                        viewModel.lookupEpc(epcs[which]);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}
