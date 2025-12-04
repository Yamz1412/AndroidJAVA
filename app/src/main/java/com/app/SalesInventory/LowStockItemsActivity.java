package com.app.SalesInventory;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LowStockItemsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private LowStockItemsAdapter adapter;
    private List<Product> lowStockList = new ArrayList<>();
    private ProductRepository productRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_low_stock_items);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Low Stock Items");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewLowStock);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        productRepository = ProductRepository.getInstance((Application) getApplicationContext());
        adapter = new LowStockItemsAdapter(this, lowStockList, productRepository);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadLowStockItems();
    }

    private void loadLowStockItems() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        productRepository.getAllProducts().observe(this, products -> {
            lowStockList.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p == null || !p.isActive()) continue;
                    int qty = p.getQuantity();
                    int reorder = p.getReorderLevel();
                    int critical = p.getCriticalLevel();
                    boolean isCritical = critical > 0 && qty <= critical;
                    boolean isLow = !isCritical && reorder > 0 && qty <= reorder;
                    if (isCritical || isLow) {
                        lowStockList.add(p);
                    }
                }
            }

            progressBar.setVisibility(View.GONE);
            if (lowStockList.isEmpty()) {
                tvNoData.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvNoData.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}