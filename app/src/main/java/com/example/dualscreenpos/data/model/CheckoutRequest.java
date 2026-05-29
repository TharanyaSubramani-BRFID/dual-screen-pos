package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CheckoutRequest {

    public List<CheckoutItemRequest> items;
    public double subtotal;
    @SerializedName("gst_total")   public double gstTotal;
    @SerializedName("grand_total") public double grandTotal;
    @SerializedName("payment_mode") public String paymentMode = "MOCK";

    public static class CheckoutItemRequest {
        public String rfid;
        @SerializedName("sku_id")      public int    skuId;
        @SerializedName("unit_price")  public double unitPrice;
        @SerializedName("gst_percent") public double gstPercent;
        @SerializedName("line_total")  public double lineTotal;
    }
}
