package com.app.SalesInventory;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditProduct extends BaseActivity {
    private EditText productNameET, costPriceET, sellingPriceET, quantityET, unitET, minStockET, expiryDateET;
    private Spinner categorySpinner;
    private Button updateBtn, cancelBtn;
    private ImageButton btnEditPhoto;
    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;
    private String selectedImagePath;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Calendar calendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthManager authManager = AuthManager.getInstance();
        if (!authManager.isCurrentUserAdmin()) {
            Toast.makeText(this, "Only admins can edit products", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_edit_product);
        productRepository = SalesInventoryApplication.getProductRepository();
        productNameET = findViewById(R.id.productNameET);
        costPriceET = findViewById(R.id.costPriceET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        unitET = findViewById(R.id.unitET);
        minStockET = findViewById(R.id.minStockET);
        expiryDateET = findViewById(R.id.expiryDateET);
        categorySpinner = findViewById(R.id.categorySpinner);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        setupImagePickers();
        expiryDateET.setOnClickListener(v -> showDatePicker());
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            productId = extras.getString("productId");
            if (productId != null) {
                loadProductData();
            }
        }
        updateBtn.setOnClickListener(v -> updateProduct());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
    }

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedImagePath = uri.toString();
                            btnEditPhoto.setImageURI(uri);
                        }
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, "Permission required to select image", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openImagePicker();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showDatePicker() {
        long initial = currentProduct != null ? currentProduct.getExpiryDate() : 0L;
        if (initial > 0) {
            calendar.setTimeInMillis(initial);
        } else {
            calendar.setTimeInMillis(System.currentTimeMillis());
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            Date d = calendar.getTime();
            expiryDateET.setText(expiryFormat.format(d));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadProductData() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                currentProduct = product;
                populateFields();
            }
            @Override
            public void onError(String error) {
            }
        });
    }

    private void populateFields() {
        if (currentProduct != null) {
            productNameET.setText(currentProduct.getProductName());
            costPriceET.setText(String.valueOf(currentProduct.getCostPrice()));
            sellingPriceET.setText(String.valueOf(currentProduct.getSellingPrice()));
            quantityET.setText(String.valueOf(currentProduct.getQuantity()));
            unitET.setText(currentProduct.getUnit());
            minStockET.setText(String.valueOf(currentProduct.getReorderLevel()));
            if (currentProduct.getExpiryDate() > 0) {
                expiryDateET.setText(expiryFormat.format(new Date(currentProduct.getExpiryDate())));
            }
            if (currentProduct.getImagePath() != null && !currentProduct.getImagePath().isEmpty()) {
                selectedImagePath = currentProduct.getImagePath();
                btnEditPhoto.setImageURI(Uri.parse(selectedImagePath));
            }
        }
    }

    private void updateProduct() {
        if (!validateInputs() || currentProduct == null) {
            return;
        }
        updateBtn.setEnabled(false        );
        updateBtn.setText("Updating...");
        try {
            currentProduct.setProductName(productNameET.getText().toString().trim());
            currentProduct.setCategoryName(categorySpinner.getSelectedItem() != null ? categorySpinner.getSelectedItem().toString() : currentProduct.getCategoryName());
            currentProduct.setCostPrice(Double.parseDouble(costPriceET.getText().toString().trim()));
            currentProduct.setSellingPrice(Double.parseDouble(sellingPriceET.getText().toString().trim()));
            currentProduct.setQuantity(Integer.parseInt(quantityET.getText().toString().trim()));
            currentProduct.setUnit(unitET.getText().toString().trim());
            currentProduct.setReorderLevel(Integer.parseInt(minStockET.getText().toString().trim()));
            String expiryStr = expiryDateET.getText().toString().trim();
            long expiry = 0L;
            if (!expiryStr.isEmpty()) {
                try {
                    Date d = expiryFormat.parse(expiryStr);
                    if (d != null) expiry = d.getTime();
                } catch (ParseException ignored) {
                }
            }
            currentProduct.setExpiryDate(expiry);
            if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
                currentProduct.setImagePath(selectedImagePath);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            updateBtn.setEnabled(true);
            updateBtn.setText("Update Product");
            return;
        }
        productRepository.updateProduct(currentProduct, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                Toast.makeText(EditProduct.this, "Product updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                updateBtn.setEnabled(true);
                updateBtn.setText("Update Product");
            }
        });
    }

    private boolean validateInputs() {
        if (productNameET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (costPriceET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Cost price is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (sellingPriceET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Selling price is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            double costPrice = Double.parseDouble(costPriceET.getText().toString().trim());
            double sellingPrice = Double.parseDouble(sellingPriceET.getText().toString().trim());
            if (costPrice < 0 || sellingPrice < 0) {
                Toast.makeText(this, "Prices must be positive", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (sellingPrice < costPrice) {
                Toast.makeText(this, "Selling price must be greater than cost price", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}