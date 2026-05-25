package com.example.dualscreenpos.data.model;

public class ReturnRoute {
    public ItemRecord itemRecord;
    public SkuDetail skuDetail;
    public String returnType;   // "RETURN" | "RETURN_TO_STORE" | "BLOCKED"
    public String blockReason;  // null unless returnType == "BLOCKED"
    public boolean requiresBin; // true only when returnType == "RETURN"
}
