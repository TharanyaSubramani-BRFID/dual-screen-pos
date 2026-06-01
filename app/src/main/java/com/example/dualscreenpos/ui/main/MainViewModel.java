package com.example.dualscreenpos.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.dualscreenpos.data.model.BulkUploadRequest;
import com.example.dualscreenpos.data.model.CheckoutRequest;
import com.example.dualscreenpos.data.model.CheckoutResponse;
import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.model.TransactionRequest;
import com.example.dualscreenpos.data.model.TransactionResult;
import com.example.dualscreenpos.data.network.RetailApi;
import com.example.dualscreenpos.data.repository.ItemRepository;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.rfid.ReaderState;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends ViewModel {

    // ── UiState sealed hierarchy ──────────────────────────────────────────────

    public static abstract class UiState {
        UiState() {}

        public static class Idle extends UiState {}

        // Scanner active — shows ScanningFragment
        public static class CartScanning extends UiState {
            public final List<ReturnRoute> cart;
            public CartScanning(List<ReturnRoute> cart) { this.cart = cart; }
        }

        // Cart has items, scanner idle — shows OperationsFragment
        public static class CartReady extends UiState {
            public final List<ReturnRoute> cart;
            public CartReady(List<ReturnRoute> cart) { this.cart = cart; }
        }

        public static class Processing extends UiState {}

        public static class Success extends UiState {
            public final String itemName;
            public final String type; // "CHECKOUT" | "RETURN_TO_STORE" | "RETURN_TO_WAREHOUSE"
            public Success(String itemName, String type) {
                this.itemName = itemName;
                this.type = type != null ? type : "CHECKOUT";
            }
        }

        public static class Error extends UiState {
            public final String message;
            public Error(String message) { this.message = message; }
        }

        public static class BlockedItem extends UiState {
            public final String title;
            public final String message;
            public BlockedItem(String title, String message) {
                this.title = title;
                this.message = message;
            }
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final RfidCardReaderManager readerManager;
    private final ItemRepository itemRepo;
    private final SettingsRepository settingsRepo;

    private final MutableLiveData<UiState> uiStateLiveData = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<List<StorageBin>> binsLiveData = new MutableLiveData<>(new ArrayList<>());

    private final List<ReturnRoute> cart = new ArrayList<>();
    private Observer<ReaderState> pendingScanObserver = null;

    public MainViewModel() {
        readerManager = RfidCardReaderManager.getInstance();
        itemRepo = ItemRepository.getInstance();
        settingsRepo = SettingsRepository.getInstance();

        String ip = settingsRepo.getReaderIp();
        if (ip != null && !ip.isEmpty()) {
            readerManager.connect(ip);
        }
    }

    // ── LiveData accessors ────────────────────────────────────────────────────

    public LiveData<UiState> getUiStateLiveData() { return uiStateLiveData; }
    public LiveData<ReaderState> getReaderStateLiveData() { return readerManager.getStateLiveData(); }
    public LiveData<List<StorageBin>> getBinsLiveData() { return binsLiveData; }

    // ── Public actions ────────────────────────────────────────────────────────

    public void postUiState(UiState state) { uiStateLiveData.postValue(state); }

    public void startCartCheckout() {
        cart.clear();
        uiStateLiveData.postValue(new UiState.CartScanning(new ArrayList<>()));
        if (!settingsRepo.isMockMode()) scanNow();
    }

    public void addMoreScan() {
        uiStateLiveData.postValue(new UiState.CartScanning(new ArrayList<>(cart)));
        if (!settingsRepo.isMockMode()) scanNow();
    }

    public void lookupEpcForCart(String epc) { fetchAndAddToCart(epc); }

    public void resumeCart() {
        if (!cart.isEmpty()) {
            uiStateLiveData.postValue(new UiState.CartReady(new ArrayList<>(cart)));
        } else {
            uiStateLiveData.postValue(new UiState.Idle());
        }
    }

    // Remove the most-recently-added item with this SKU id from the cart
    public void removeOneCartItemBySku(int skuId) {
        for (int i = cart.size() - 1; i >= 0; i--) {
            ReturnRoute r = cart.get(i);
            if (r.skuDetail != null && r.skuDetail.id == skuId) {
                cart.remove(i);
                uiStateLiveData.postValue(new UiState.CartReady(new ArrayList<>(cart)));
                return;
            }
        }
    }

    // Called by Cancel buttons — wipes cart and returns to idle
    public void cancelAndClearCart() {
        cart.clear();
        uiStateLiveData.postValue(new UiState.Idle());
    }

    public void fetchBinsIfNeeded() {
        List<StorageBin> current = binsLiveData.getValue();
        if (current != null && !current.isEmpty()) return;
        forceFetchBins();
    }

    public void forceFetchBins() {
        RetailApi.getInstance(settingsRepo.getBaseUrl())
                .getAllBins(new RetailApi.ApiCallback<List<StorageBin>>() {
                    @Override public void onSuccess(List<StorageBin> bins) {
                        binsLiveData.postValue(bins != null ? bins : new ArrayList<>());
                    }
                    @Override public void onFailure(String error) { /* best-effort */ }
                });
    }

    public List<String> getAvailableRacks() {
        List<String> racks = new ArrayList<>();
        List<StorageBin> bins = binsLiveData.getValue();
        if (bins == null) return racks;
        for (StorageBin b : bins) {
            if (b.rackId != null && !b.rackId.isEmpty() && !racks.contains(b.rackId)) {
                racks.add(b.rackId);
            }
        }
        return racks;
    }

    public List<StorageBin> getBinsForRack(String rackId) {
        List<StorageBin> result = new ArrayList<>();
        List<StorageBin> bins = binsLiveData.getValue();
        if (bins == null) return result;
        for (StorageBin b : bins) {
            if (rackId != null && rackId.equals(b.rackId)) result.add(b);
        }
        return result;
    }

    public void scanNow() {
        removeScanObserver();
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
                    fetchAndAddToCart(((ReaderState.TagFound) state).epc);
                } else if (state instanceof ReaderState.NoTagDetected) {
                    removeScanObserver();
                    if (!cart.isEmpty()) {
                        uiStateLiveData.postValue(new UiState.CartReady(new ArrayList<>(cart)));
                    } else {
                        uiStateLiveData.postValue(new UiState.Error("No item detected. Try again."));
                    }
                } else if (state instanceof ReaderState.ReaderError) {
                    removeScanObserver();
                    uiStateLiveData.postValue(new UiState.Error(((ReaderState.ReaderError) state).message));
                }
            }
        };
        readerManager.getStateLiveData().observeForever(pendingScanObserver);
        readerManager.scanOnce();
    }

    // ── Checkout ──────────────────────────────────────────────────────────────

    public void confirmCheckoutItems(List<ReturnRoute> items) {
        if (items.isEmpty()) return;
        uiStateLiveData.postValue(new UiState.Processing());

        List<CheckoutRequest.CheckoutItemRequest> reqItems = new ArrayList<>();
        double subtotal = 0, gstTotal = 0;
        for (ReturnRoute r : items) {
            if (r.itemRecord == null || r.skuDetail == null) continue;
            double gstAmt  = r.skuDetail.salePrice * r.skuDetail.gstPercent / 100.0;
            double lineTot = r.skuDetail.salePrice + gstAmt;
            CheckoutRequest.CheckoutItemRequest it = new CheckoutRequest.CheckoutItemRequest();
            it.rfid       = r.itemRecord.rfid;
            it.skuId      = r.skuDetail.id;
            it.unitPrice  = r.skuDetail.salePrice;
            it.gstPercent = r.skuDetail.gstPercent;
            it.lineTotal  = lineTot;
            reqItems.add(it);
            subtotal += r.skuDetail.salePrice;
            gstTotal += gstAmt;
        }
        CheckoutRequest req = new CheckoutRequest();
        req.items      = reqItems;
        req.subtotal   = subtotal;
        req.gstTotal   = gstTotal;
        req.grandTotal = subtotal + gstTotal;
        req.paymentMode = "MOCK";

        int count = items.size();
        final String label = count + " item" + (count > 1 ? "s" : "") + " checked out";

        RetailApi.getInstance(settingsRepo.getBaseUrl())
                .submitCheckout(req, new RetailApi.ApiCallback<CheckoutResponse>() {
                    @Override public void onSuccess(CheckoutResponse result) {
                        cart.clear();
                        uiStateLiveData.postValue(new UiState.Success(label, "CHECKOUT"));
                    }
                    @Override public void onFailure(String error) {
                        uiStateLiveData.postValue(new UiState.Error(error));
                    }
                });
    }

    // ── Return to Store ───────────────────────────────────────────────────────

    public void confirmReturnToStore(List<ReturnRoute> items, String reason) {
        if (items.isEmpty()) return;
        uiStateLiveData.postValue(new UiState.Processing());
        List<String> rfids = epcsOf(items);

        BulkUploadRequest uploadReq = new BulkUploadRequest();
        uploadReq.rfids = rfids;
        uploadReq.track = "RETURN_TO_STORE";

        int count = items.size();
        final String label = count + " item" + (count > 1 ? "s" : "") + " returned to store";

        RetailApi api = RetailApi.getInstance(settingsRepo.getBaseUrl());
        api.bulkUploadItems(uploadReq, new RetailApi.ApiCallback<TransactionResult>() {
            @Override public void onSuccess(TransactionResult r) {
                api.submitTransaction(
                        TransactionRequest.forReturn(rfids, "", "RETURN_TO_STORE", reason),
                        new RetailApi.ApiCallback<TransactionResult>() {
                            @Override public void onSuccess(TransactionResult r2) {
                                cart.clear();
                                uiStateLiveData.postValue(new UiState.Success(label, "RETURN_TO_STORE"));
                            }
                            @Override public void onFailure(String err) {
                                uiStateLiveData.postValue(new UiState.Error(err));
                            }
                        });
            }
            @Override public void onFailure(String err) {
                uiStateLiveData.postValue(new UiState.Error(err));
            }
        });
    }

    // ── Return to Warehouse ───────────────────────────────────────────────────

    public void confirmReturnToWarehouse(List<ReturnRoute> items, String reason,
                                         String binRfid, String rackId) {
        if (items.isEmpty()) return;
        uiStateLiveData.postValue(new UiState.Processing());

        List<String> allRfids  = epcsOf(items);
        List<String> soldRfids = new ArrayList<>();
        for (ReturnRoute r : items) {
            if (r.itemRecord != null && "SOLD".equals(r.itemRecord.status))
                soldRfids.add(r.itemRecord.rfid);
        }

        int count = items.size();
        final String label = count + " item" + (count > 1 ? "s" : "") + " returned to warehouse";
        RetailApi api = RetailApi.getInstance(settingsRepo.getBaseUrl());

        // Final step: one STORE_TO_WAREHOUSE bulk_upload + one transaction for ALL tags
        Runnable moveAllToWarehouse = () -> {
            BulkUploadRequest req = new BulkUploadRequest();
            req.rfids = allRfids;
            req.rackId = rackId != null ? rackId : "";
            req.storageBinRfid = binRfid != null ? binRfid : "";
            req.track = "STORE_TO_WAREHOUSE";

            api.bulkUploadItems(req, new RetailApi.ApiCallback<TransactionResult>() {
                @Override public void onSuccess(TransactionResult r) {
                    // Single transaction record with ALL tags → one entry in history
                    api.submitTransaction(
                            TransactionRequest.forReturn(allRfids, binRfid, "STORE_TO_WAREHOUSE", reason),
                            new RetailApi.ApiCallback<TransactionResult>() {
                                @Override public void onSuccess(TransactionResult r2) {
                                    cart.clear();
                                    binsLiveData.postValue(new ArrayList<>());
                                    uiStateLiveData.postValue(new UiState.Success(label, "RETURN_TO_WAREHOUSE"));
                                }
                                @Override public void onFailure(String err) {
                                    cart.clear();
                                    binsLiveData.postValue(new ArrayList<>());
                                    uiStateLiveData.postValue(new UiState.Success(label, "RETURN_TO_WAREHOUSE"));
                                }
                            });
                }
                @Override public void onFailure(String err) {
                    uiStateLiveData.postValue(new UiState.Error(err));
                }
            });
        };

        if (soldRfids.isEmpty()) {
            // No SOLD items — go straight to warehouse
            moveAllToWarehouse.run();
            return;
        }

        // SOLD items need RETURN_TO_STORE first (backend rule: SOLD → IN_STORE → IN_WAREHOUSE)
        // No transaction record here — only the final STORE_TO_WAREHOUSE transaction is created
        BulkUploadRequest soldToStore = new BulkUploadRequest();
        soldToStore.rfids = soldRfids;
        soldToStore.track = "RETURN_TO_STORE";

        api.bulkUploadItems(soldToStore, new RetailApi.ApiCallback<TransactionResult>() {
            @Override public void onSuccess(TransactionResult r) {
                // Now all items are IN_STORE — move everything to warehouse as one operation
                moveAllToWarehouse.run();
            }
            @Override public void onFailure(String err) {
                uiStateLiveData.postValue(new UiState.Error(err));
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void fetchAndAddToCart(String epc) {
        // Duplicate check
        for (ReturnRoute r : cart) {
            if (r.itemRecord != null && r.itemRecord.rfid.equals(epc)) {
                uiStateLiveData.postValue(new UiState.CartReady(new ArrayList<>(cart)));
                return;
            }
        }
        itemRepo.fetchItemAndSku(epc, new RetailApi.ApiCallback<ReturnRoute>() {
            @Override
            public void onSuccess(ReturnRoute route) {
                if ("BLOCKED".equals(route.returnType)) {
                    uiStateLiveData.postValue(new UiState.BlockedItem(
                            "Item Not Available", route.blockReason));
                } else {
                    cart.add(route);
                    uiStateLiveData.postValue(new UiState.CartReady(new ArrayList<>(cart)));
                }
            }
            @Override
            public void onFailure(String error) {
                if ("NOT_FOUND".equals(error)) {
                    uiStateLiveData.postValue(new UiState.BlockedItem(
                            "Item Not Found",
                            "This EPC is not registered in the system."));
                } else {
                    uiStateLiveData.postValue(new UiState.Error(error));
                }
            }
        });
    }

    private List<String> epcsOf(List<ReturnRoute> items) {
        List<String> rfids = new ArrayList<>();
        for (ReturnRoute r : items) if (r.itemRecord != null) rfids.add(r.itemRecord.rfid);
        return rfids;
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
