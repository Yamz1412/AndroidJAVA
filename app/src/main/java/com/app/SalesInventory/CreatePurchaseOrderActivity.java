package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreatePurchaseOrderActivity extends BaseActivity  {

    private Toolbar toolbar;
    private TextInputEditText etSupplierName, etOrderDate, etExpectedDate, etNotes;
    private RecyclerView recyclerViewItems;
    private TextView tvTotalAmount;
    private Button btnAddItem, btnCreatePO;

    private POItemAdapter adapter;
    private List<POItem> poItems;
    private DatabaseReference poRef;
    private Calendar calendar;

    private ProductRepository productRepository;
    private List<Product> availableProducts = new ArrayList<>();
    private List<String> productNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_purchase_order);
        initializeViews();
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create Purchase Order");
        }
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        productRepository = SalesInventoryApplication.getProductRepository();
        loadProductsForSpinner();
        setupRecyclerView();
        setupListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        etSupplierName = findViewById(R.id.etSupplierName);
        etOrderDate = findViewById(R.id.etOrderDate);
        etExpectedDate = findViewById(R.id.etExpectedDate);
        etNotes = findViewById(R.id.etNotes);
        recyclerViewItems = findViewById(R.id.recyclerViewItems);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnCreatePO = findViewById(R.id.btnCreatePO);
        calendar = Calendar.getInstance();
        updateDateLabel(etOrderDate);
    }

    private void loadProductsForSpinner() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                availableProducts.clear();
                productNames.clear();
                availableProducts.addAll(products);
                for (Product p : products) {
                    productNames.add(p.getProductName());
                }
            }
        });
    }

    private void setupRecyclerView() {
        poItems = new ArrayList<>();
        adapter = new POItemAdapter(this, poItems, position -> {
            poItems.remove(position);
            adapter.notifyItemRemoved(position);
            calculateTotal();
        }, this::calculateTotal);
        recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewItems.setAdapter(adapter);
    }

    private void setupListeners() {
        etOrderDate.setOnClickListener(v -> showDatePicker(etOrderDate));
        etExpectedDate.setOnClickListener(v -> showDatePicker(etExpectedDate));
        btnAddItem.setOnClickListener(v -> showAddItemDialog());
        btnCreatePO.setOnClickListener(v -> savePurchaseOrder());
    }

    private void showDatePicker(final EditText editText) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateLabel(editText);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel(EditText editText) {
        String myFormat = "MMM dd, yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.getDefault());
        editText.setText(sdf.format(calendar.getTime()));
    }

    private void showAddItemDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_po_item, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        Spinner spinnerProduct = view.findViewById(R.id.spinnerProduct);
        TextInputEditText etQuantity = view.findViewById(R.id.etQuantity);
        TextInputEditText etUnitPrice = view.findViewById(R.id.etUnitPrice);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, productNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProduct.setAdapter(spinnerAdapter);
        btnAdd.setOnClickListener(v -> {
            String qtyStr = etQuantity.getText() != null ? etQuantity.getText().toString().trim() : "";
            String priceStr = etUnitPrice.getText() != null ? etUnitPrice.getText().toString().trim() : "";
            if (spinnerProduct.getSelectedItem() == null) {
                Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!qtyStr.isEmpty() && !priceStr.isEmpty()) {
                try {
                    int qty = Integer.parseInt(qtyStr);
                    double price = Double.parseDouble(priceStr);
                    int pos = spinnerProduct.getSelectedItemPosition();
                    if (pos < 0 || pos >= availableProducts.size()) {
                        Toast.makeText(this, "Invalid product", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Product p = availableProducts.get(pos);
                    POItem existing = null;
                    for (POItem it : poItems) {
                        if (it.getProductId().equals(p.getProductId())) {
                            existing = it;
                            break;
                        }
                    }
                    if (existing != null) {
                        existing.setQuantity(existing.getQuantity() + qty);
                    } else {
                        poItems.add(new POItem(p.getProductId(), p.getProductName(), qty, price));
                    }
                    adapter.notifyDataSetChanged();
                    calculateTotal();
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void calculateTotal() {
        double total = 0;
        for (POItem item : poItems) {
            total += item.getSubtotal();
        }
        tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%.2f", total));
    }

    private void savePurchaseOrder() {
        String supplier = etSupplierName.getText().toString().trim();

        if (supplier.isEmpty()) {
            etSupplierName.setError("Supplier name required");
            return;
        }

        if (poItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = poRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String poNumber = "PO-" + System.currentTimeMillis() / 1000;

        double total = 0;
        for (POItem item : poItems) total += item.getSubtotal();

        PurchaseOrder po = new PurchaseOrder(
                id,
                poNumber,
                supplier,
                "Pending",
                System.currentTimeMillis(),
                total,
                new ArrayList<>(poItems)
        );

        poRef.child(id).setValue(po)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Purchase Order created", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}