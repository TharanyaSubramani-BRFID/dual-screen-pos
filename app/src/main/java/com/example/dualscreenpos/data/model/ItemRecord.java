package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;

public class ItemRecord {
    public String id;
    public String rfid;
    @SerializedName("sku_id") public String skuId;
    @SerializedName("rack_id") public String rackId;
    @SerializedName("storage_bin_rfid") public String storageBinRfid;
    public String status;
    public String track;
    @SerializedName("created_at") public String createdAt;
}
