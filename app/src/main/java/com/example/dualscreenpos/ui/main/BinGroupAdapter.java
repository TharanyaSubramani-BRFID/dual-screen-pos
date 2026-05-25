package com.example.dualscreenpos.ui.main;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.StorageBin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BinGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnBinSelected {
        void onBinSelected(StorageBin bin);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BIN = 1;

    private final List<Object> items = new ArrayList<>();
    private final OnBinSelected callback;
    private int selectedPosition = -1;

    public BinGroupAdapter(LinkedHashMap<String, List<StorageBin>> grouped, OnBinSelected callback) {
        this.callback = callback;
        for (Map.Entry<String, List<StorageBin>> entry : grouped.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_BIN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_bin_header, parent, false));
        } else {
            return new BinViewHolder(inflater.inflate(R.layout.item_bin, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof BinViewHolder) {
            StorageBin bin = (StorageBin) items.get(position);
            ((BinViewHolder) holder).bind(bin, position == selectedPosition, pos -> {
                int prev = selectedPosition;
                selectedPosition = pos;
                notifyItemChanged(prev);
                notifyItemChanged(pos);
                callback.onBinSelected(bin);
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    interface ClickListener { void onClick(int position); }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvHeader;
        HeaderViewHolder(View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_bin_header);
        }
        void bind(String rack) { tvHeader.setText(rack.toUpperCase()); }
    }

    static class BinViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;
        final ProgressBar progressBar;

        BinViewHolder(View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tv_bin_label);
            progressBar = itemView.findViewById(R.id.pb_bin_fill);
        }

        void bind(StorageBin bin, boolean selected, ClickListener clickListener) {
            tvLabel.setText(bin.getDisplayLabel());
            int fill = bin.capacity > 0 ? (bin.itemCount * 100 / bin.capacity) : 0;
            progressBar.setProgress(fill);

            if (bin.isNearlyFull()) {
                progressBar.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#F57C00")));
                itemView.setBackgroundColor(Color.parseColor("#3E2000"));
            } else {
                progressBar.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#00B4D8")));
                itemView.setBackgroundColor(selected ? Color.parseColor("#1A2A3A") : Color.TRANSPARENT);
            }
            itemView.setOnClickListener(v -> clickListener.onClick(getAdapterPosition()));
        }
    }
}
