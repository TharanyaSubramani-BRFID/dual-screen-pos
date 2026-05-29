package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BulkUploadRequest {
    public List<String> rfids;
    @SerializedName("sku_id")          public int    skuId          = 0;
    @SerializedName("rack_id")         public String rackId         = "";
    @SerializedName("storage_bin_rfid") public String storageBinRfid = "";
    public String track;
}
