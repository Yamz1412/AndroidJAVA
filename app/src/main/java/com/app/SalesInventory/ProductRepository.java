package com.app.SalesInventory;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class ProductRepository {
    private static ProductRepository instance;
    private AppDatabase db;
    private ProductDao productDao;
    private MediatorLiveData<List<Product>> allProducts;
    private Application application;
    private AlertRepository alertRepository;
    private List<OnCriticalStockListener> criticalStockListeners = new CopyOnWriteArrayList<>();

    public interface OnCriticalStockListener {
        void onProductCritical(Product product);
    }

    private ProductRepository(Application application) {
        this.application = application;
        db = AppDatabase.getInstance(application);
        productDao = db.productDao();
        allProducts = new MediatorLiveData<>();
        LiveData<List<ProductEntity>> source = productDao.getAllProductsLive();
        allProducts.addSource(source, entities -> {
            List<Product> list = new ArrayList<>();
            if (entities != null) {
                for (ProductEntity e : entities) {
                    Product p = mapEntityToProduct(e);
                    list.add(p);
                }
            }
            allProducts.setValue(list);
        });
        alertRepository = AlertRepository.getInstance(application);
        SyncScheduler.schedulePeriodicSync(application.getApplicationContext());
    }

    public static synchronized ProductRepository getInstance(Application application) {
        if (instance == null) {
            instance = new ProductRepository(application);
        }
        return instance;
    }

    public void registerCriticalStockListener(OnCriticalStockListener listener) {
        if (listener != null && !criticalStockListeners.contains(listener)) {
            criticalStockListeners.add(listener);
        }
    }

    public void unregisterCriticalStockListener(OnCriticalStockListener listener) {
        criticalStockListeners.remove(listener);
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void fetchAllProductsAsync(OnProductsFetchedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ProductEntity> entities = productDao.getPendingProductsSync();
            List<Product> products = new ArrayList<>();
            if (entities != null) {
                for (ProductEntity e : entities) {
                    products.add(mapEntityToProduct(e));
                }
            }
            listener.onProductsFetched(products);
        });
    }

    public void getProductsByCategory(String category, OnProductsFetchedListener listener) {
        LiveData<List<ProductEntity>> source = productDao.getAllProductsLive();
        Observer<List<ProductEntity>> obs = new Observer<List<ProductEntity>>() {
            @Override
            public void onChanged(List<ProductEntity> entities) {
                source.removeObserver(this);
                List<Product> results = new ArrayList<>();
                if (entities != null) {
                    for (ProductEntity e : entities) {
                        Product p = mapEntityToProduct(e);
                        if (category == null || category.isEmpty()) {
                            results.add(p);
                        } else {
                            String catName = p.getCategoryName() == null ? "" : p.getCategoryName();
                            if (catName.equalsIgnoreCase(category)) results.add(p);
                        }
                    }
                }
                listener.onProductsFetched(results);
            }
        };
        source.observeForever(obs);
    }

    public void addProduct(Product product, OnProductAddedListener listener) {
        addProduct(product, null, listener);
    }

    public void addProduct(Product product, String imagePath, OnProductAddedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                listener.onError("User not approved");
                return;
            }
            long now = System.currentTimeMillis();
            ProductEntity e = mapProductToEntity(product);
            e.dateAdded = now;
            e.lastUpdated = now;
            e.syncState = "PENDING";
            e.imagePath = imagePath;
            e.imageUrl = null;
            long localId = productDao.insert(e);
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductAdded("local:" + localId);
        });
    }

    public void updateProduct(Product product, OnProductUpdatedListener listener) {
        updateProduct(product, null, listener);
    }

    public void updateProduct(Product product, String imagePath, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                listener.onError("User not approved");
                return;
            }
            ProductEntity existing = null;
            if (product.getProductId() != null && !product.getProductId().isEmpty()) {
                existing = productDao.getByProductIdSync(product.getProductId());
            }
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.productName = product.getProductName();
                existing.categoryId = product.getCategoryId();
                existing.categoryName = product.getCategoryName();
                existing.description = product.getDescription();
                existing.costPrice = product.getCostPrice();
                existing.sellingPrice = product.getSellingPrice();
                existing.quantity = product.getQuantity();
                existing.reorderLevel = product.getReorderLevel();
                existing.criticalLevel = product.getCriticalLevel();
                existing.ceilingLevel = product.getCeilingLevel();
                existing.unit = product.getUnit();
                existing.lastUpdated = now;
                existing.syncState = "PENDING";
                if (imagePath != null) {
                    existing.imagePath = imagePath;
                    existing.imageUrl = null;
                }
                productDao.update(existing);
            } else {
                ProductEntity e = mapProductToEntity(product);
                e.lastUpdated = now;
                e.syncState = "PENDING";
                e.imagePath = imagePath;
                e.imageUrl = null;
                productDao.insert(e);
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductUpdated();
        });
    }

    public void deleteProduct(String productId, OnProductDeletedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserAdmin()) {
                listener.onError("Unauthorized");
                return;
            }
            ProductEntity existing = productDao.getByProductIdSync(productId);
            if (existing != null) {
                if (existing.productId != null && !existing.productId.isEmpty()) {
                    productDao.setSyncInfo(existing.localId, existing.productId, "DELETE_PENDING");
                } else {
                    productDao.deleteByLocalId(existing.localId);
                }
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductDeleted();
        });
    }

    public void updateProductQuantity(String productId, int newQuantity, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity existing = productDao.getByProductIdSync(productId);
            if (existing != null) {
                int oldQuantity = existing.quantity;
                existing.quantity = newQuantity;
                existing.lastUpdated = System.currentTimeMillis();
                existing.syncState = "PENDING";
                productDao.update(existing);
                SyncScheduler.enqueueImmediateSync(application.getApplicationContext());

                boolean wasCritical = existing.criticalLevel > 0 && oldQuantity <= existing.criticalLevel;
                boolean isNowCritical = existing.criticalLevel > 0 && newQuantity <= existing.criticalLevel;
                boolean isNowLowOnly = !isNowCritical && existing.reorderLevel > 0 && newQuantity <= existing.reorderLevel;
                boolean recoveredFromCritical = wasCritical && newQuantity > existing.criticalLevel;

                if (recoveredFromCritical) {
                    CriticalStockNotifier.getInstance().clearForProduct(existing.productId);
                }

                if (isNowCritical) {
                    createCriticalStockAlert(existing);
                    notifyCriticalStockListeners(existing);
                } else if (isNowLowOnly) {
                    createLowStockAlert(existing);
                }

                listener.onProductUpdated();
            } else {
                listener.onError("Product not found locally");
            }
        });
    }

    private void notifyCriticalStockListeners(ProductEntity e) {
        Product p = mapEntityToProduct(e);
        for (OnCriticalStockListener l : criticalStockListeners) {
            l.onProductCritical(p);
        }
    }

    private void createLowStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        Alert alert = new Alert();
        alert.setProductId(e.productId);
        alert.setType("LOW_STOCK");
        String name = e.productName == null ? "" : e.productName;
        alert.setMessage("Low stock for " + name + " (Qty: " + e.quantity + ")");
        alert.setRead(false);
        alert.setTimestamp(System.currentTimeMillis());
        alertRepository.addAlert(alert, new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
        });
    }

    private void createCriticalStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        Alert alert = new Alert();
        alert.setProductId(e.productId);
        alert.setType("CRITICAL_STOCK");
        String name = e.productName == null ? "" : e.productName;
        alert.setMessage("Critical stock for " + name + " (Qty: " + e.quantity + ")");
        alert.setRead(false);
        alert.setTimestamp(System.currentTimeMillis());
        alertRepository.addAlert(alert, new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
        });
    }

    public void getProductById(String productId, OnProductFetchedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity e = productDao.getByProductIdSync(productId);
            if (e != null) {
                listener.onProductFetched(mapEntityToProduct(e));
            } else {
                listener.onError("Product not found");
            }
        });
    }

    public void upsertFromRemote(Product p) {
        if (p == null || p.getProductId() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity existing = productDao.getByProductIdSync(p.getProductId());
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.productName = p.getProductName();
                existing.categoryId = p.getCategoryId();
                existing.categoryName = p.getCategoryName();
                existing.description = p.getDescription();
                existing.costPrice = p.getCostPrice();
                existing.sellingPrice = p.getSellingPrice();
                existing.quantity = p.getQuantity();
                existing.reorderLevel = p.getReorderLevel();
                existing.criticalLevel = p.getCriticalLevel();
                existing.ceilingLevel = p.getCeilingLevel();
                existing.unit = p.getUnit();
                existing.barcode = p.getBarcode();
                existing.supplier = p.getSupplier();
                existing.dateAdded = p.getDateAdded();
                existing.addedBy = p.getAddedBy();
                existing.isActive = p.isActive();
                existing.imageUrl = p.getImageUrl();
                existing.lastUpdated = now;
                existing.syncState = "SYNCED";
                productDao.update(existing);
            } else {
                ProductEntity e = new ProductEntity();
                e.productId = p.getProductId();
                e.productName = p.getProductName();
                e.categoryId = p.getCategoryId();
                e.categoryName = p.getCategoryName();
                e.description = p.getDescription();
                e.costPrice = p.getCostPrice();
                e.sellingPrice = p.getSellingPrice();
                e.quantity = p.getQuantity();
                e.reorderLevel = p.getReorderLevel();
                e.criticalLevel = p.getCriticalLevel();
                e.ceilingLevel = p.getCeilingLevel();
                e.unit = p.getUnit();
                e.barcode = p.getBarcode();
                e.supplier = p.getSupplier();
                e.dateAdded = p.getDateAdded();
                e.addedBy = p.getAddedBy();
                e.isActive = p.isActive();
                e.imageUrl = p.getImageUrl();
                e.lastUpdated = now;
                e.syncState = "SYNCED";
                productDao.insert(e);
            }
        });
    }

    private ProductEntity mapProductToEntity(Product p) {
        return new ProductEntity(
                p.getProductName(),
                p.getCategoryId(),
                p.getCategoryName(),
                p.getDescription(),
                p.getCostPrice(),
                p.getSellingPrice(),
                p.getQuantity(),
                p.getReorderLevel(),
                p.getCriticalLevel(),
                p.getCeilingLevel(),
                p.getUnit(),
                p.getBarcode(),
                p.getSupplier(),
                p.getDateAdded(),
                p.getAddedBy(),
                p.isActive(),
                p.getDateAdded(),
                "PENDING",
                p.getImagePath(),
                p.getImageUrl()
        );
    }

    private Product mapEntityToProduct(ProductEntity e) {
        Product p = new Product();
        p.setLocalId(e.localId);
        p.setProductId(e.productId);
        p.setProductName(e.productName);
        p.setCategoryId(e.categoryId);
        p.setCategoryName(e.categoryName);
        p.setDescription(e.description);
        p.setCostPrice(e.costPrice);
        p.setSellingPrice(e.sellingPrice);
        p.setQuantity(e.quantity);
        p.setReorderLevel(e.reorderLevel);
        p.setCriticalLevel(e.criticalLevel);
        p.setCeilingLevel(e.ceilingLevel);
        p.setUnit(e.unit);
        p.setBarcode(e.barcode);
        p.setSupplier(e.supplier);
        p.setDateAdded(e.dateAdded);
        p.setAddedBy(e.addedBy);
        p.setActive(e.isActive);
        p.setImagePath(e.imagePath);
        p.setImageUrl(e.imageUrl);
        return p;
    }

    public void retrySync(long localId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity e = productDao.getByLocalId(localId);
            if (e != null) {
                productDao.setSyncInfo(localId, e.productId, "PENDING");
                SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            }
        });
    }

    public interface OnProductsFetchedListener {
        void onProductsFetched(List<Product> products);
        void onError(String error);
    }

    public interface OnProductFetchedListener {
        void onProductFetched(Product product);
        void onError(String error);
    }

    public interface OnProductAddedListener {
        void onProductAdded(String productId);
        void onError(String error);
    }

    public interface OnProductUpdatedListener {
        void onProductUpdated();
        void onError(String error);
    }

    public interface OnProductDeletedListener {
        void onProductDeleted();
        void onError(String error);
    }
}