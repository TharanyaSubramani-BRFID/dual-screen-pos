package com.example.dualscreenpos.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.repository.SettingsRepository;

public class ScanningFragment extends Fragment {

    private static final String[] MOCK_EPCS = {
        "E2003411B802011383360001", "E2003411B802011383360002",
        "E2003411B802011383360003", "E2003411B802011383360004",
        "E2003411B802011383360005", "E2003411B802011383360006",
        "E2003411B802011383360007", "E2003411B802011383360008",
        "E2003411B802011383360009", "E2003411B80201138336000A",
        "E2003411B80201138336000B", "E2003411B80201138336000C",
        "E2003411B80201138336000D", "E2003411B80201138336000E",
        "E2003411B80201138336000F", "E2003411B802011383360010",
        "E2003411B802011383360011", "E2003411B802011383360012",
        "E2003411B802011383360013", "E2003411B802011383360014",
        "E2003411B802011383360015", "E2003411B802011383360016",
        "E2003411B802011383360017", "E2003411B802011383360018",
        "E2003411B802011383360019", "E2003411B80201138336001A",
        "E2003411B80201138336001B", "E2003411B80201138336001C",
        "E2003411B80201138336001D", "E2003411B80201138336001E",
        "E2003411B80201138336001F", "E2003411B802011383360020",
        "E2003411B802011383360021", "E2003411B802011383360022",
        "E2003411B802011383360023", "E2003411B802011383360024",
        "E2003411B802011383360025", "E2003411B802011383360026",
        "E2003411B802011383360027", "E2003411B802011383360028"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_scanning, container, false);

        if (SettingsRepository.getInstance().isMockMode()) {
            Button btnMock = root.findViewById(R.id.btn_mock_scan);
            btnMock.setVisibility(View.VISIBLE);
            MainViewModel viewModel = ((MainActivity) requireActivity()).getViewModel();
            btnMock.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                    .setTitle("Select Mock EPC")
                    .setItems(MOCK_EPCS, (d, which) -> viewModel.lookupEpcForCart(MOCK_EPCS[which]))
                    .show()
            );
        }

        return root;
    }
}
