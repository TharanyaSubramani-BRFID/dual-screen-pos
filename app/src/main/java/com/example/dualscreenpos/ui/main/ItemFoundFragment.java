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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.repository.BinRepository;
import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.List;

public class ItemFoundFragment extends Fragment {

    private StorageBin selectedBin = null;

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

        String json = args.getString("route_json", "");
        ReturnRoute route = new Gson().fromJson(json, ReturnRoute.class);
        if (route == null) return;

        TextView tvItemName = view.findViewById(R.id.tv_item_name);
        TextView tvPrice = view.findViewById(R.id.tv_price);
        TextView tvRfid = view.findViewById(R.id.tv_rfid);
        TextView tvStatus = view.findViewById(R.id.tv_status);
        LinearLayout layoutBinSelector = view.findViewById(R.id.layout_bin_selector);
        TextView tvReturnToStoreLabel = view.findViewById(R.id.tv_return_to_store_label);
        RecyclerView rvBins = view.findViewById(R.id.rv_bins);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        if (route.skuDetail != null) {
            tvItemName.setText(route.skuDetail.name);
            tvPrice.setText(String.format("$%.2f", route.skuDetail.price));
        }
        if (route.itemRecord != null) {
            tvRfid.setText(route.itemRecord.rfid);
            tvStatus.setText(route.itemRecord.status);
            applyStatusColor(tvStatus, route.itemRecord.status);
        }

        if (route.requiresBin) {
            layoutBinSelector.setVisibility(View.VISIBLE);
            tvReturnToStoreLabel.setVisibility(View.GONE);
            btnConfirm.setEnabled(false);

            LinkedHashMap<String, List<StorageBin>> grouped =
                    BinRepository.getInstance().getGroupedBins();
            BinGroupAdapter adapter = new BinGroupAdapter(grouped, bin -> {
                selectedBin = bin;
                btnConfirm.setEnabled(true);
            });
            rvBins.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvBins.setAdapter(adapter);
        } else {
            layoutBinSelector.setVisibility(View.GONE);
            tvReturnToStoreLabel.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
        }

        btnConfirm.setOnClickListener(v -> viewModel.confirmReturn(selectedBin));
        btnCancel.setOnClickListener(v -> viewModel.postUiState(new MainViewModel.UiState.Idle()));
    }

    private void applyStatusColor(TextView tv, String status) {
        int bgColor;
        switch (status) {
            case "SOLD":
                bgColor = Color.parseColor("#1B4332");
                break;
            case "DISPATCHED":
            case "IN_STORE":
                bgColor = Color.parseColor("#003566");
                break;
            default:
                bgColor = Color.parseColor("#3A3A3A");
                break;
        }
        tv.setBackgroundColor(bgColor);
    }
}
