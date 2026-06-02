package com.example.dualscreenpos.printing;

import com.example.dualscreenpos.data.model.ReceiptData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReceiptFormatter {

    private static final int LINE_WIDTH = 42;

    // ── ESC/POS command constants ─────────────────────────────────────────────
    private static final byte[] ESC_INIT        = {0x1B, 0x40};           // initialise
    private static final byte[] ALIGN_CENTER    = {0x1B, 0x61, 0x01};
    private static final byte[] ALIGN_LEFT      = {0x1B, 0x61, 0x00};
    private static final byte[] BOLD_ON         = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF        = {0x1B, 0x45, 0x00};
    private static final byte[] DOUBLE_SIZE     = {0x1B, 0x21, 0x30};     // bold + double height/width
    private static final byte[] NORMAL_SIZE     = {0x1B, 0x21, 0x00};
    private static final byte[] LINE_FEED       = {0x0A};
    private static final byte[] PAPER_CUT       = {0x1D, 0x56, 0x01};     // partial cut

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns ESC/POS byte array ready to send to the printer. */
    public static byte[] format(ReceiptData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            write(out, ESC_INIT);

            // Header
            write(out, ALIGN_CENTER);
            write(out, BOLD_ON);
            writeLine(out, "BRITANNIA RFID POS");
            write(out, BOLD_OFF);
            writeLine(out, repeat("=", LINE_WIDTH));
            writeLine(out, "Date: " + data.dateTime);
            writeLine(out, "Ref : " + data.checkoutNumber);
            writeLine(out, repeat("-", LINE_WIDTH));

            // Items
            write(out, ALIGN_LEFT);
            for (ReceiptData.ReceiptItem item : data.items) {
                String qtyTag = "x" + item.quantity;
                String nameLine = padRight(item.productName, LINE_WIDTH - qtyTag.length()) + qtyTag;
                writeLine(out, nameLine);
                String priceLine = "  Sale " + fmt(item.salePrice)
                        + "  GST " + (int) item.gstPercent + "%"
                        + "  = " + fmt(item.lineTotal);
                writeLine(out, priceLine);
            }

            writeLine(out, repeat("-", LINE_WIDTH));

            // Totals
            writeLine(out, twoCol("Subtotal:", fmt(data.subtotal)));
            writeLine(out, twoCol("GST:", fmt(data.gstTotal)));
            writeLine(out, repeat("-", LINE_WIDTH));
            write(out, BOLD_ON);
            writeLine(out, twoCol("GRAND TOTAL:", fmt(data.grandTotal)));
            write(out, BOLD_OFF);

            // Footer
            writeLine(out, repeat("=", LINE_WIDTH));
            write(out, ALIGN_CENTER);
            writeLine(out, "Thank you for shopping!");
            writeLine(out, repeat("=", LINE_WIDTH));
            write(out, LINE_FEED);
            write(out, LINE_FEED);
            write(out, LINE_FEED);
            write(out, PAPER_CUT);

        } catch (IOException ignored) {}
        return out.toByteArray();
    }

    /** Returns a human-readable receipt string for Logcat / error dialogs. */
    public static String formatText(ReceiptData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(repeat("=", LINE_WIDTH)).append("\n");
        sb.append(centre("BRITANNIA RFID POS")).append("\n");
        sb.append(repeat("=", LINE_WIDTH)).append("\n");
        sb.append("Date: ").append(data.dateTime).append("\n");
        sb.append("Ref : ").append(data.checkoutNumber).append("\n");
        sb.append(repeat("-", LINE_WIDTH)).append("\n");
        for (ReceiptData.ReceiptItem item : data.items) {
            String qtyTag = "x" + item.quantity;
            sb.append(padRight(item.productName, LINE_WIDTH - qtyTag.length())).append(qtyTag).append("\n");
            sb.append("  Sale ").append(fmt(item.salePrice))
              .append("  GST ").append((int) item.gstPercent).append("%")
              .append("  = ").append(fmt(item.lineTotal)).append("\n");
        }
        sb.append(repeat("-", LINE_WIDTH)).append("\n");
        sb.append(twoCol("Subtotal:", fmt(data.subtotal))).append("\n");
        sb.append(twoCol("GST:", fmt(data.gstTotal))).append("\n");
        sb.append(repeat("-", LINE_WIDTH)).append("\n");
        sb.append(twoCol("GRAND TOTAL:", fmt(data.grandTotal))).append("\n");
        sb.append(repeat("=", LINE_WIDTH)).append("\n");
        sb.append(centre("Thank you for shopping!")).append("\n");
        sb.append(repeat("=", LINE_WIDTH)).append("\n");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void write(ByteArrayOutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }

    private static void writeLine(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.write(LINE_FEED);
    }

    private static String fmt(double amount) {
        return String.format("Rs%.2f", amount);
    }

    private static String twoCol(String left, String right) {
        int pad = LINE_WIDTH - left.length() - right.length();
        if (pad < 1) pad = 1;
        return left + repeat(" ", pad) + right;
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + repeat(" ", width - s.length());
    }

    private static String centre(String s) {
        int pad = (LINE_WIDTH - s.length()) / 2;
        return pad > 0 ? repeat(" ", pad) + s : s;
    }

    private static String repeat(String c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
