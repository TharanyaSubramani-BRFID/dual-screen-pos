package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;

public class StorageBin {
    public int id;
    public String rfid;
    @SerializedName("rack_id") public String rackId;
    public int capacity;
    @SerializedName("item_count") public int itemCount;

    public String getDisplayLabel() {
        return rfid + " (" + itemCount + "/" + capacity + ")";
    }

    public boolean isNearlyFull() {
        return capacity > 0 && (itemCount * 100 / capacity) >= 80;
    }
}
