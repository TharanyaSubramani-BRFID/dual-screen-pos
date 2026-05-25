package com.example.dualscreenpos.data.repository;

import android.content.Context;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.network.RetailApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BinRepository {

    private static BinRepository instance;
    private List<StorageBin> cachedBins = new ArrayList<>();

    private BinRepository() {}

    public static void init(Context ctx) {
        if (instance == null) {
            instance = new BinRepository();
        }
    }

    public static BinRepository getInstance() {
        return instance;
    }

    public void loadBins(RetailApi.ApiCallback<List<StorageBin>> cb) {
        String baseUrl = SettingsRepository.getInstance().getBaseUrl();
        RetailApi.getInstance(baseUrl).getAllBins(new RetailApi.ApiCallback<List<StorageBin>>() {
            @Override
            public void onSuccess(List<StorageBin> result) {
                cachedBins = result != null ? result : new ArrayList<>();
                if (cb != null) cb.onSuccess(cachedBins);
            }
            @Override
            public void onFailure(String error) {
                if (cb != null) cb.onFailure(error);
            }
        });
    }

    public LinkedHashMap<String, List<StorageBin>> getGroupedBins() {
        TreeMap<String, List<StorageBin>> sorted = new TreeMap<>();
        for (StorageBin bin : cachedBins) {
            String rack = bin.rackId != null ? bin.rackId : "UNKNOWN";
            if (!sorted.containsKey(rack)) {
                sorted.put(rack, new ArrayList<>());
            }
            sorted.get(rack).add(bin);
        }
        return new LinkedHashMap<>(sorted);
    }

    public boolean isLoaded() {
        return !cachedBins.isEmpty();
    }

    public StorageBin findByRfid(String rfid) {
        for (StorageBin bin : cachedBins) {
            if (rfid != null && rfid.equals(bin.rfid)) return bin;
        }
        return null;
    }
}
