package com.example.dualscreenpos.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.model.TransactionRequest;
import com.example.dualscreenpos.data.network.RetailApi;
import com.example.dualscreenpos.data.repository.BinRepository;
import com.example.dualscreenpos.data.repository.ItemRepository;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.rfid.ReaderState;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;

public class MainViewModel extends ViewModel {

    // ── UiState sealed hierarchy ──────────────────────────────────────────────

    public static abstract class UiState {
        UiState() {}

        public static class Idle extends UiState {}

        public static class Scanning extends UiState {}

        public static class ItemFound extends UiState {
            public final ReturnRoute route;
            public ItemFound(ReturnRoute route) { this.route = route; }
            public String getItemName() { return route.skuDetail != null ? route.skuDetail.name : ""; }
            public String getFormattedPrice() {
                return route.skuDetail != null ? String.format("$%.2f", route.skuDetail.price) : "";
            }
        }

        public static class Processing extends UiState {}

        public static class Success extends UiState {
            public final String itemName;
            public Success(String itemName) { this.itemName = itemName; }
        }

        public static class Error extends UiState {
            public final String message;
            public Error(String message) { this.message = message; }
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final RfidCardReaderManager readerManager;
    private final ItemRepository itemRepo;
    private final BinRepository binRepo;
    private final SettingsRepository settingsRepo;

    private final MutableLiveData<UiState> uiStateLiveData = new MutableLiveData<>(new UiState.Idle());
    private ReturnRoute currentRoute = null;
    private Observer<ReaderState> pendingScanObserver = null;

    public MainViewModel() {
        readerManager = RfidCardReaderManager.getInstance();
        itemRepo = ItemRepository.getInstance();
        binRepo = BinRepository.getInstance();
        settingsRepo = SettingsRepository.getInstance();

        String ip = settingsRepo.getReaderIp();
        if (ip != null && !ip.isEmpty()) {
            readerManager.connect(ip);
        }
    }

    // ── LiveData accessors ────────────────────────────────────────────────────

    public LiveData<UiState> getUiStateLiveData() { return uiStateLiveData; }

    public LiveData<ReaderState> getReaderStateLiveData() { return readerManager.getStateLiveData(); }

    // ── Public actions ────────────────────────────────────────────────────────

    public void postUiState(UiState state) {
        uiStateLiveData.postValue(state);
    }

    public void startScan() {
        uiStateLiveData.postValue(new UiState.Scanning());

        // Wait for Scanning state to confirm the new scan started, then process terminal result.
        pendingScanObserver = new Observer<ReaderState>() {
            boolean scanStarted = false;

            @Override
            public void onChanged(ReaderState state) {
                if (!scanStarted) {
                    if (state instanceof ReaderState.Scanning) scanStarted = true;
                    return;
                }
                if (state instanceof ReaderState.TagFound) {
                    removeScanObserver();
                    fetchItemDetails(((ReaderState.TagFound) state).epc);
                } else if (state instanceof ReaderState.NoTagDetected) {
                    removeScanObserver();
                    uiStateLiveData.postValue(new UiState.Error("No item detected. Try again."));
                } else if (state instanceof ReaderState.ReaderError) {
                    removeScanObserver();
                    uiStateLiveData.postValue(new UiState.Error(((ReaderState.ReaderError) state).message));
                }
            }
        };
        readerManager.getStateLiveData().observeForever(pendingScanObserver);
        readerManager.scanOnce();
    }

    public void confirmReturn(StorageBin selectedBin) {
        if (currentRoute == null) return;
        uiStateLiveData.postValue(new UiState.Processing());

        final ReturnRoute route = currentRoute;
        TransactionRequest req = route.requiresBin
                ? TransactionRequest.forReturn(route.itemRecord.rfid, selectedBin.rfid)
                : TransactionRequest.forReturnToStore(route.itemRecord.rfid);

        RetailApi.getInstance(settingsRepo.getBaseUrl())
                .submitTransaction(req, new RetailApi.ApiCallback<com.example.dualscreenpos.data.model.TransactionResult>() {
                    @Override
                    public void onSuccess(com.example.dualscreenpos.data.model.TransactionResult result) {
                        String itemName = route.skuDetail != null ? route.skuDetail.name : "";
                        uiStateLiveData.postValue(new UiState.Success(itemName));
                    }
                    @Override
                    public void onFailure(String error) {
                        uiStateLiveData.postValue(new UiState.Error(error));
                    }
                });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void fetchItemDetails(String epc) {
        itemRepo.fetchItemAndSku(epc, new RetailApi.ApiCallback<ReturnRoute>() {
            @Override
            public void onSuccess(ReturnRoute route) {
                if ("BLOCKED".equals(route.returnType)) {
                    uiStateLiveData.postValue(new UiState.Error(route.blockReason));
                } else {
                    currentRoute = route;
                    uiStateLiveData.postValue(new UiState.ItemFound(route));
                }
            }
            @Override
            public void onFailure(String error) {
                uiStateLiveData.postValue(new UiState.Error(error));
            }
        });
    }

    private void removeScanObserver() {
        if (pendingScanObserver != null) {
            readerManager.getStateLiveData().removeObserver(pendingScanObserver);
            pendingScanObserver = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        removeScanObserver();
    }
}
