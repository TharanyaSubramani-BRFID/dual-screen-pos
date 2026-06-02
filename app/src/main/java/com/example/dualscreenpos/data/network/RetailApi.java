package com.example.dualscreenpos.data.network;

import android.os.Handler;
import android.os.Looper;

import com.example.dualscreenpos.data.model.BulkUploadRequest;
import com.example.dualscreenpos.data.model.CheckoutRequest;
import com.example.dualscreenpos.data.model.CheckoutResponse;
import com.example.dualscreenpos.data.model.ItemRecord;
import com.example.dualscreenpos.data.model.SkuDetail;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.model.TransactionRequest;
import com.example.dualscreenpos.data.model.TransactionResult;
import com.example.dualscreenpos.data.model.VerifyRfidsResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RetailApi {

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static RetailApi instance;

    private final String baseUrl;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private RetailApi(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public static RetailApi getInstance(String baseUrl) {
        if (instance == null || !instance.baseUrl.equals(
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)) {
            instance = new RetailApi(baseUrl);
        }
        return instance;
    }

    public void getItemByRfid(String rfid, ApiCallback<ItemRecord> cb) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items/rfid/" + rfid)
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("NETWORK_ERROR:" + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.code() == 404) {
                    deliver(() -> cb.onFailure("NOT_FOUND"));
                    return;
                }
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Server error " + response.code() + ": " + parseErrorMessage(body)));
                    return;
                }
                try {
                    ItemRecord item = GSON.fromJson(body, ItemRecord.class);
                    deliver(() -> cb.onSuccess(item));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void getSkuById(int skuId, ApiCallback<SkuDetail> cb) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/skus/get/" + skuId)
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Server error " + response.code() + ": " + parseErrorMessage(body)));
                    return;
                }
                try {
                    SkuDetail sku = GSON.fromJson(body, SkuDetail.class);
                    deliver(() -> cb.onSuccess(sku));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void getAllBins(ApiCallback<List<StorageBin>> cb) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/storage_bins/get_all?skip=0&limit=100")
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Server error " + response.code() + ": " + parseErrorMessage(body)));
                    return;
                }
                try {
                    Type listType = new TypeToken<List<StorageBin>>() {}.getType();
                    List<StorageBin> bins = GSON.fromJson(body, listType);
                    deliver(() -> cb.onSuccess(bins));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void verifyRfids(List<String> rfids, ApiCallback<VerifyRfidsResponse> cb) {
        String json = GSON.toJson(new RfidListBody(rfids));
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/transactions/return/verify-rfids")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Server error " + response.code() + ": " + parseErrorMessage(responseBody)));
                    return;
                }
                try {
                    VerifyRfidsResponse result = GSON.fromJson(responseBody, VerifyRfidsResponse.class);
                    deliver(() -> cb.onSuccess(result));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void submitTransaction(TransactionRequest req, ApiCallback<TransactionResult> cb) {
        String json = GSON.toJson(req);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/transactions/add")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Transaction failed: " + parseErrorMessage(responseBody)));
                    return;
                }
                deliver(() -> cb.onSuccess(new TransactionResult(true, "Success")));
            }
        });
    }

    public void getSkus(ApiCallback<java.util.List<com.example.dualscreenpos.data.model.SkuDetail>> cb) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/skus/get_all")
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Server error " + response.code()));
                    return;
                }
                try {
                    java.lang.reflect.Type t = new TypeToken<java.util.List<
                        com.example.dualscreenpos.data.model.SkuDetail>>() {}.getType();
                    java.util.List<com.example.dualscreenpos.data.model.SkuDetail> skus =
                        GSON.fromJson(body, t);
                    deliver(() -> cb.onSuccess(skus));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    public void getItemCountForSku(int skuId, ApiCallback<Integer> cb) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items/?sku_id=" + skuId + "&status=IN_STORE&skip=0&limit=500")
                .get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onSuccess(0));
                    return;
                }
                try {
                    com.google.gson.JsonArray arr = GSON.fromJson(body, com.google.gson.JsonArray.class);
                    deliver(() -> cb.onSuccess(arr != null ? arr.size() : 0));
                } catch (Exception e) {
                    deliver(() -> cb.onSuccess(0));
                }
            }
        });
    }

    public void bulkUploadItems(BulkUploadRequest req, ApiCallback<TransactionResult> cb) {
        String json = GSON.toJson(req);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/items/bulk_upload")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure(parseErrorMessage(responseBody)));
                    return;
                }
                deliver(() -> cb.onSuccess(new TransactionResult(true, "OK")));
            }
        });
    }

    public void submitCheckout(CheckoutRequest req, ApiCallback<CheckoutResponse> cb) {
        String json = GSON.toJson(req);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/checkouts/add")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(() -> cb.onFailure("Network error: " + e.getMessage()));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    deliver(() -> cb.onFailure("Checkout failed: " + parseErrorMessage(responseBody)));
                    return;
                }
                try {
                    CheckoutResponse result = GSON.fromJson(responseBody, CheckoutResponse.class);
                    deliver(() -> cb.onSuccess(result));
                } catch (Exception e) {
                    deliver(() -> cb.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    private void deliver(Runnable r) {
        mainHandler.post(r);
    }

    private String parseErrorMessage(String body) {
        try {
            com.google.gson.JsonObject obj = GSON.fromJson(body, com.google.gson.JsonObject.class);
            if (obj.has("detail")) return obj.get("detail").getAsString();
            if (obj.has("message")) return obj.get("message").getAsString();
        } catch (Exception ignored) {}
        return body.isEmpty() ? "Unknown error" : body;
    }

    private static class RfidListBody {
        List<String> rfids;
        RfidListBody(List<String> rfids) { this.rfids = rfids; }
    }
}
