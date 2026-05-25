package com.example.dualscreenpos.rfid;

public abstract class ReaderState {

    ReaderState() {}

    public static class Idle extends ReaderState {}

    public static class Scanning extends ReaderState {}

    public static class TagFound extends ReaderState {
        public final String epc;
        public final int rssi;
        public TagFound(String epc, int rssi) {
            this.epc = epc;
            this.rssi = rssi;
        }
    }

    public static class NoTagDetected extends ReaderState {}

    public static class ReaderError extends ReaderState {
        public final String message;
        public ReaderError(String message) {
            this.message = message;
        }
    }
}
