package com.example.dualscreenpos.data.model;

public class TransactionResult {
    public boolean success;
    public String message;

    public TransactionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
