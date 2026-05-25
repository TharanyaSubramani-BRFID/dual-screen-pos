package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VerifyRfidsResponse {
    @SerializedName("existing_rfids") public List<String> existingRfids;
    @SerializedName("missing_rfids") public List<String> missingRfids;
}
