package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class ProductRemoteSyncer {
    private static final String TAG = "ProductRemoteSyncer";

    private final ProductRepository productRepository;
    private final FirestoreManager firestoreManager;
    private final FirebaseFirestore db;

    public ProductRemoteSyncer(Application application) {
        this.productRepository = ProductRepository.getInstance(application);
        this.firestoreManager = FirestoreManager.getInstance();
        this.db = firestoreManager.getDb();
    }

    public void syncAllProducts(@Nullable Runnable onFinished) {
        String path = firestoreManager.getUserProductsPath();
        db.collection(path)
                .get()
                .addOnSuccessListener(this::handleSnapshot)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to download products", e);
                    if (onFinished != null) onFinished.run();
                })
                .addOnCompleteListener(task -> {
                    if (onFinished != null) onFinished.run();
                });
    }

    private void handleSnapshot(QuerySnapshot snapshot) {
        if (snapshot == null) return;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Product p = mapDocToProduct(doc);
            if (p != null) {
                productRepository.upsertFromRemote(p);
            }
        }
    }

    private Product mapDocToProduct(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;

        Product p = new Product();
        p.setProductId(doc.getId());
        p.setProductName(getString(doc, "productName"));
        p.setCategoryId(getString(doc, "categoryId"));
        p.setCategoryName(getString(doc, "categoryName"));
        p.setDescription(getString(doc, "description"));
        p.setCostPrice(getDouble(doc, "costPrice"));
        p.setSellingPrice(getDouble(doc, "sellingPrice"));
        p.setQuantity(getInt(doc, "quantity"));
        p.setReorderLevel(getInt(doc, "reorderLevel"));
        p.setCriticalLevel(getInt(doc, "criticalLevel"));
        p.setCeilingLevel(getInt(doc, "ceilingLevel"));
        p.setUnit(getString(doc, "unit"));
        p.setBarcode(getString(doc, "barcode"));
        p.setSupplier(getString(doc, "supplier"));
        p.setDateAdded(getLong(doc, "dateAdded"));
        p.setAddedBy(getString(doc, "addedBy"));
        p.setActive(getBoolean(doc, "isActive", true));
        p.setExpiryDate(getLong(doc, "expiryDate"));
        p.setProductType(getString(doc, "productType"));
        p.setImageUrl(getString(doc, "imageUrl"));
        p.setImagePath(null);
        return p;
    }

    private String getString(DocumentSnapshot doc, String field) {
        String v = doc.getString(field);
        return v == null ? "" : v;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Number n = doc.getDouble(field);
        if (n == null) {
            Long l = doc.getLong(field);
            return l == null ? 0.0 : l.doubleValue();
        }
        return n.doubleValue();
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Long l = doc.getLong(field);
        return l == null ? 0 : l.intValue();
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Long l = doc.getLong(field);
        return l == null ? 0L : l;
    }

    private boolean getBoolean(DocumentSnapshot doc, String field, boolean def) {
        Boolean b = doc.getBoolean(field);
        return b == null ? def : b;
    }
}