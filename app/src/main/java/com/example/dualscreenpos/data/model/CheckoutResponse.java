package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;

public class CheckoutResponse {
    public int id;
    @SerializedName("checkout_number") public String checkoutNumber;
    public double subtotal;
    @SerializedName("gst_total")   public double gstTotal;
    @SerializedName("grand_total") public double grandTotal;
    @SerializedName("payment_mode") public String paymentMode;
    public String status;
}
