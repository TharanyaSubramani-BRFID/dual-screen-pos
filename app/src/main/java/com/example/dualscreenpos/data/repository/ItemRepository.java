package com.example.dualscreenpos.data.repository;

import com.example.dualscreenpos.data.model.ItemRecord;
import com.example.dualscreenpos.data.model.ReturnRoute;
import com.example.dualscreenpos.data.model.SkuDetail;
import com.example.dualscreenpos.data.network.RetailApi;

public class ItemRepository {

    private static ItemRepository instance;

    private ItemRepository() {}

    public static ItemRepository getInstance() {
        if (instance == null) {
            instance = new ItemRepository();
        }
        return instance;
    }

    public void fetchItemAndSku(String rfid, RetailApi.ApiCallback<ReturnRoute> cb) {
        String baseUrl = SettingsRepository.getInstance().getBaseUrl();
        RetailApi api = RetailApi.getInstance(baseUrl);

        api.getItemByRfid(rfid, new RetailApi.ApiCallback<ItemRecord>() {
            @Override
            public void onSuccess(ItemRecord item) {
                api.getSkuById(item.skuId, new RetailApi.ApiCallback<SkuDetail>() {
                    @Override
                    public void onSuccess(SkuDetail sku) {
                        cb.onSuccess(buildRoute(item, sku));
                    }
                    @Override
                    public void onFailure(String error) {
                        cb.onFailure("SKU lookup failed: " + error);
                    }
                });
            }
            @Override
            public void onFailure(String error) {
                cb.onFailure("Item lookup failed: " + error);
            }
        });
    }

    private ReturnRoute buildRoute(ItemRecord item, SkuDetail sku) {
        ReturnRoute route = new ReturnRoute();
        route.itemRecord = item;
        route.skuDetail = sku;

        switch (item.status) {
            case "DISPATCHED":
            case "IN_STORE":
                route.returnType = "RETURN";
                route.requiresBin = true;
                break;
            case "SOLD":
                route.returnType = "RETURN_TO_STORE";
                route.requiresBin = false;
                break;
            case "IN_WAREHOUSE":
                route.returnType = "BLOCKED";
                route.blockReason = "Item already returned — in warehouse";
                break;
            default:
                route.returnType = "BLOCKED";
                route.blockReason = "Cannot return this item";
                break;
        }
        return route;
    }
}
