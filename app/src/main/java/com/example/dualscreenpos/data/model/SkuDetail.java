package com.example.dualscreenpos.data.model;

import com.google.gson.annotations.SerializedName;

public class SkuDetail {
    public int id;
    @SerializedName("sku_code")     public String skuCode;
    @SerializedName("product_name") public String productName;
    public String category;
    public double mrp;
    @SerializedName("sale_price")   public double salePrice;
    @SerializedName("gst_percent")  public double gstPercent;
    @SerializedName("is_active")    public boolean isActive;
    @SerializedName("created_at")   public String createdAt;
}
