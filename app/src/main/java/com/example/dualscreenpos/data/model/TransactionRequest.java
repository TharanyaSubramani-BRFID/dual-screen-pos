package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

public class TransactionRequest {
    public String type;
    @SerializedName("storage_bin_rfid") public String storageBinRfid;
    public List<String> rfids;
    @SerializedName("tag_count") public int tagCount;
    public String reason;

    public static TransactionRequest forReturn(String rfid, String binRfid) {
        TransactionRequest req = new TransactionRequest();
        req.type = "RETURN";
        req.storageBinRfid = binRfid;
        req.rfids = Collections.singletonList(rfid);
        req.tagCount = 1;
        return req;
    }

    public static TransactionRequest forReturnToStore(String rfid) {
        TransactionRequest req = new TransactionRequest();
        req.type = "RETURN_TO_STORE";
        req.rfids = Collections.singletonList(rfid);
        req.tagCount = 1;
        return req;
    }

    public static TransactionRequest forCheckout(String rfid, String storageBinRfid) {
        TransactionRequest req = new TransactionRequest();
        req.type = "SOLD";
        req.storageBinRfid = storageBinRfid != null ? storageBinRfid : "";
        req.rfids = Collections.singletonList(rfid);
        req.tagCount = 1;
        return req;
    }
}
