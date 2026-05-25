package com.example.dualscreenpos.rfid;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.lifecycle.MutableLiveData;

import com.uhf.api.cls.Reader;

public class RfidCardReaderManager {

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

    public void connect(String ip) {
        readerHandler.post(() -> {
            if (reader != null) {
                reader.CloseReader();
            }
            reader = new Reader();
            Reader.READER_ERR err = reader.InitReader_Notype(ip, 1);
            if (err != Reader.READER_ERR.MT_OK_ERR) {
                postState(new ReaderState.ReaderError("Connection failed: " + err));
            } else {
                postState(new ReaderState.Idle());
            }
        });
    }

    // TagInventory_Raw blocks the thread for up to timeoutMs.
    // Always call from readerHandler, never from the main thread.
    public void scanOnce() {
        readerHandler.post(() -> {
            postState(new ReaderState.Scanning());
            if (reader == null) {
                postState(new ReaderState.ReaderError("Reader not initialized. Check IP in Settings."));
                return;
            }
            int[] tagcnt = new int[1];
            Reader.TAGINFO ti = reader.new TAGINFO();
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

    private void postState(ReaderState s) {
        stateLiveData.postValue(s);
    }
}
