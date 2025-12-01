package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockMovementReportActivity extends BaseActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalReceived, tvTotalSold, tvTotalAdjustments;
    private Button btnExportPDF, btnExportCSV;

    private StockMovementAdapter adapter;
    private List<StockMovementReport> reportList;

    private DatabaseReference productRef, salesRef, adjustmentRef;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;

    private int grandTotalReceived = 0;
    private int grandTotalSold = 0;
    private int grandTotalAdjusted = 0;

    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Movement Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        loadData();
    }

    private void initializeViews() {
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalReceived = findViewById(R.id.tvTotalReceived);
        tvTotalSold = findViewById(R.id.tvTotalSold);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);

        exportUtil = new ReportExportUtil(this);
        csvGenerator = new CSVGenerator();

        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false);
        }

        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        reportList = new ArrayList<>();
        adapter = new StockMovementAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        btnExportPDF.setOnClickListener(v -> exportToPDF());
        btnExportCSV.setOnClickListener(v -> exportToCSV());
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        grandTotalReceived = 0;
        grandTotalSold = 0;
        grandTotalAdjusted = 0;

        Map<String, StockMovementReport> reportMap = new HashMap<>();

        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if (p != null && p.isActive()) {
                        StockMovementReport report = new StockMovementReport(
                                p.getProductId(),
                                p.getProductName(),
                                p.getCategoryName(),
                                p.getQuantity(),
                                0,
                                0,
                                0,
                                p.getQuantity(),
                                System.currentTimeMillis()
                        );
                        if (p.getProductId() != null) {
                            reportMap.put(p.getProductId(), report);
                        }
                    }
                }
                loadSales(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StockMovementReportActivity.this, "Error loading products: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSales(Map<String, StockMovementReport> reportMap) {
        salesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Sales s = ds.getValue(Sales.class);
                    if (s != null && s.getProductId() != null && reportMap.containsKey(s.getProductId())) {
                        StockMovementReport report = reportMap.get(s.getProductId());
                        int qty = s.getQuantity();
                        report.addSold(qty);
                        grandTotalSold += qty;
                    }
                }
                loadAdjustments(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void loadAdjustments(Map<String, StockMovementReport> reportMap) {
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null && adj.getProductId() != null && reportMap.containsKey(adj.getProductId())) {
                        StockMovementReport report = reportMap.get(adj.getProductId());
                        if ("Add Stock".equals(adj.getAdjustmentType())) {
                            report.addReceived(adj.getQuantityAdjusted());
                            grandTotalReceived += adj.getQuantityAdjusted();
                        } else {
                            report.addAdjusted(adj.getQuantityAdjusted());
                            grandTotalAdjusted += adj.getQuantityAdjusted();
                        }
                    }
                }
                finalizeReport(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void finalizeReport(Map<String, StockMovementReport> reportMap) {
        reportList.clear();
        for (StockMovementReport report : reportMap.values()) {
            report.calculateOpening();
            reportList.add(report);
        }

        progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();

        tvTotalReceived.setText(grandTotalReceived + " units");
        tvTotalSold.setText(grandTotalSold + " units");
        tvTotalAdjustments.setText(grandTotalAdjusted + " units");

        if (reportList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
        } else {
            tvNoData.setVisibility(View.GONE);
        }
    }

    private void exportToPDF() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_PDF);
            File file = new File(exportDir, fileName);

            if (pdfGenerator != null) {
                pdfGenerator.generateStockMovementReportPDF(file, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(file.getAbsolutePath());
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage());
        }
    }

    private void exportToCSV() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_CSV);
            File file = new File(exportDir, fileName);

            if (csvGenerator != null) {
                csvGenerator.generateStockMovementReportCSV(file, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(file.getAbsolutePath());
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}