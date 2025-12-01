package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class InventoryReportsActivity extends BaseActivity  {

    private Button btnStockValue;
    private Button btnStockMovement;
    private Button btnAdjustmentSummary;
    private Button btnExport;
    private Button btnDeliveryReport;
    private Button btnReceivingReport;

    private DatabaseReference productRef;
    private DatabaseReference salesRef;
    private DatabaseReference adjustmentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_reports);

        TextView tv = findViewById(android.R.id.text1);
        if (tv != null) tv.setText("Inventory Reports");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Inventory Reports");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        btnStockValue = findViewById(R.id.btnStockValue);
        btnStockMovement = findViewById(R.id.btnStockMovement);
        btnAdjustmentSummary = findViewById(R.id.btnAdjustmentSummary);
        btnExport = findViewById(R.id.btnExport);
        btnDeliveryReport = findViewById(R.id.btnDeliveryReport);
        btnReceivingReport = findViewById(R.id.btnReceivingReport);

        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
    }

    private void setupClickListeners() {
        btnStockValue.setOnClickListener(v ->
                startActivity(new Intent(this, StockValueReportActivity.class)));

        btnStockMovement.setOnClickListener(v ->
                startActivity(new Intent(this, StockMovementReportActivity.class)));

        btnAdjustmentSummary.setOnClickListener(v ->
                startActivity(new Intent(this, AdjustmentSummaryReportActivity.class)));

        btnReceivingReport.setOnClickListener(v ->
                startActivity(new Intent(this, ReceivingReportActivity.class)));

        btnDeliveryReport.setOnClickListener(v ->
                startActivity(new Intent(this, DeliveryReportActivity.class)));

        btnExport.setOnClickListener(v ->
                android.widget.Toast.makeText(this, "Export feature coming soon!",
                        android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}