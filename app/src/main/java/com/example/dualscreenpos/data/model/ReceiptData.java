package com.example.dualscreenpos.data.model;

import java.util.List;

public class ReceiptData {
    public String checkoutNumber;
    public String dateTime;
    public List<ReceiptItem> items;
    public double subtotal;
    public double gstTotal;
    public double grandTotal;

    public static class ReceiptItem {
        public String productName;
        public double salePrice;
        public double gstPercent;
        public double lineTotal;
        public int quantity;
    }
}
