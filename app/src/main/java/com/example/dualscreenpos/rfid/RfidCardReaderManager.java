package com.example.dualscreenpos.rfid;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.lifecycle.MutableLiveData;

import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.uhf.api.cls.Reader;

import java.util.concurrent.atomic.AtomicInteger;

public class RfidCardReaderManager {

    // ── Mock EPC pool (20 tags provided for hardware-free testing) ────────────
    private static final String[] MOCK_EPCS = {
            "E2003411B802011383360001",
            "E2003411B802011383360002",
            "E2003411B802011383360003",
            "E2003411B802011383360004",
            "E2003411B802011383360005",
            "E2003411B802011383360006",
            "E2003411B802011383360007",
            "E2003411B802011383360008",
            "E2003411B802011383360009",
            "E2003411B80201138336000A",
            "E2003411B80201138336000B",
            "E2003411B80201138336000C",
            "E2003411B80201138336000D",
            "E2003411B80201138336000E",
            "E2003411B80201138336000F",
            "E2003411B802011383360010",
            "E2003411B802011383360011",
            "E2003411B802011383360012",
            "E2003411B802011383360013",
            "E2003411B802011383360014"
    };

    private final AtomicInteger mockEpcIndex = new AtomicInteger(0);

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static RfidCardReaderManager instance;

    private Reader reader;
    private final HandlerThread readerThread;
    private final Handler readerHandler;
    private final MutableLiveData<ReaderState> stateLiveData = new MutableLiveData<>();

    private RfidCardReaderManager() {
        readerThread = new HandlerThread("RfidReaderThread");
        readerThread.start();
        readerHandler = new Handler(readerThread.getLooper());
    }

    public static void init(Context ctx) {
        if (instance == null) {
            instance = new RfidCardReaderManager();
        }
    }

    public static RfidCardReaderManager getInstance() {
        return instance;
    }

    public MutableLiveData<ReaderState> getStateLiveData() {
        return stateLiveData;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    public void connect(String ip) {
        readerHandler.post(() -> {
            if (SettingsRepository.getInstance().isMockMode()) {
                // No hardware needed — report connected immediately.
                postState(new ReaderState.Idle());
                return;
            }
            if (reader != null) {
                reader.CloseReader();
            }
            try {
                reader = new Reader();
                Reader.READER_ERR err = reader.InitReader_Notype(ip, 1);
                if (err != Reader.READER_ERR.MT_OK_ERR) {
                    postState(new ReaderState.ReaderError("Connection failed: " + err));
                } else {
                    postState(new ReaderState.Idle());
                }
            } catch (Throwable t) {
                postState(new ReaderState.ReaderError("Reader unavailable: " + t.getMessage()));
            }
        });
    }

    // TagInventory_Raw blocks the thread for up to timeoutMs.
    // Always call from readerHandler, never from the main thread.
    public void scanOnce() {
        readerHandler.post(() -> {
            postState(new ReaderState.Scanning());

            if (SettingsRepository.getInstance().isMockMode()) {
                // Simulate the ~1.5 s read window so the UI feels realistic.
                SystemClock.sleep(1500);
                int idx = mockEpcIndex.getAndIncrement() % MOCK_EPCS.length;
                postState(new ReaderState.TagFound(MOCK_EPCS[idx], -60));
                return;
            }

            if (reader == null) {
                postState(new ReaderState.ReaderError("Reader not initialized. Check IP in Settings."));
                return;
            }
            try {
                int[] tagcnt = new int[1];
                Reader.READER_ERR err = reader.TagInventory_Raw(new int[]{1}, 1, (short) 2000, tagcnt);
                if (err != Reader.READER_ERR.MT_OK_ERR) {
                    postState(new ReaderState.ReaderError("Read error: " + err));
                    return;
                }
                if (tagcnt[0] == 0) {
                    postState(new ReaderState.NoTagDetected());
                    return;
                }
                Reader.TAGINFO bestTag = reader.new TAGINFO();
                reader.GetNextTag(bestTag);
                for (int i = 1; i < tagcnt[0]; i++) {
                    Reader.TAGINFO candidate = reader.new TAGINFO();
                    reader.GetNextTag(candidate);
                    if (candidate.RSSI > bestTag.RSSI) {
                        bestTag = candidate;
                    }
                }
                String epc = Reader.bytes_Hexstr(bestTag.EpcId);
                postState(new ReaderState.TagFound(epc, bestTag.RSSI));
            } catch (Throwable t) {
                postState(new ReaderState.ReaderError("Scan failed: " + t.getMessage()));
            }
        });
    }

    public void disconnect() {
        readerHandler.post(() -> {
            if (reader != null) {
                reader.CloseReader();
                reader = null;
            }
        });
    }

    /** Resets the mock EPC cycling back to the first tag. */
    public void resetMockIndex() {
        mockEpcIndex.set(0);
    }

    public int getMockEpcCount() {
        return MOCK_EPCS.length;
    }

    public String[] getMockEpcs() {
        return MOCK_EPCS;
    }

    public int getMockCurrentIndex() {
        return mockEpcIndex.get() % MOCK_EPCS.length;
    }

    private void postState(ReaderState s) {
        stateLiveData.postValue(s);
    }
}
