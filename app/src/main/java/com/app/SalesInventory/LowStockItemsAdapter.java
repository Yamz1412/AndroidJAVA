package com.app.SalesInventory;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

public class LowStockItemsAdapter extends RecyclerView.Adapter<LowStockItemsAdapter.VH> {

    private final Context ctx;
    private final List<Product> items;
    private final ProductRepository repository;
    private final AuthManager authManager;

    public LowStockItemsAdapter(Context ctx, List<Product> items, ProductRepository repository) {
        this.ctx = ctx;
        this.items = items;
        this.repository = repository != null ? repository : ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_low_stock_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        if (p == null) return;

        holder.name.setText(p.getProductName() != null ? p.getProductName() : "");
        holder.category.setText(p.getCategoryName() != null ? p.getCategoryName() : "");
        int qty = p.getQuantity();
        int reorder = p.getReorderLevel();
        int critical = p.getCriticalLevel();
        holder.stockInfo.setText("Stock: " + qty + " | Reorder: " + reorder + " | Critical: " + critical);
        holder.currentStock.setText(String.valueOf(qty));
        holder.adjustQty.setText("0");
        holder.newStock.setText("New: " + qty);

        String imageUrl = p.getImageUrl();
        String imagePath = p.getImagePath();
        String toLoad = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            toLoad = imageUrl;
        } else if (imagePath != null && !imagePath.isEmpty()) {
            toLoad = imagePath;
        }
        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(ctx)
                    .load(toLoad)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!authManager.isCurrentUserAdmin()) return true;
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete Product")
                    .setMessage("Delete " + p.getProductName() + "?")
                    .setPositiveButton("Delete", (dialog, which) -> repository.deleteProduct(p.getProductId(), new ProductRepository.OnProductDeletedListener() {
                        @Override
                        public void onProductDeleted() {
                        }

                        @Override
                        public void onError(String error) {
                        }
                    }))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        holder.btnIncrease.setOnClickListener(v -> {
            int adj = parseIntSafe(holder.adjustQty.getText().toString()) + 1;
            holder.adjustQty.setText(String.valueOf(adj));
            int newQty = qty + adj;
            holder.newStock.setText("New: " + newQty);
        });

        holder.btnDecrease.setOnClickListener(v -> {
            int adj = parseIntSafe(holder.adjustQty.getText().toString());
            if (adj > 0) adj--;
            holder.adjustQty.setText(String.valueOf(adj));
            int newQty = qty + adj;
            holder.newStock.setText("New: " + newQty);
        });

        holder.newStock.setOnClickListener(v -> {
            int adj = parseIntSafe(holder.adjustQty.getText().toString());
            int newQty = qty + adj;
            if (adj == 0) return;
            repository.updateProductQuantity(p.getProductId(), Math.max(0, newQty), new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    p.setQuantity(Math.max(0, newQty));
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).runOnUiThread(() -> {
                            holder.currentStock.setText(String.valueOf(p.getQuantity()));
                            holder.adjustQty.setText("0");
                            holder.newStock.setText("New: " + p.getQuantity());
                        });
                    }
                }

                @Override
                public void onError(String error) {
                }
            });
        });
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView name;
        TextView category;
        TextView stockInfo;
        TextView currentStock;
        TextView adjustQty;
        TextView newStock;
        ImageButton btnIncrease;
        ImageButton btnDecrease;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            category = itemView.findViewById(R.id.tvCategory);
            stockInfo = itemView.findViewById(R.id.tvStockInfo);
            currentStock = itemView.findViewById(R.id.tvCurrentStock);
            adjustQty = itemView.findViewById(R.id.tvAdjustQty);
            newStock = itemView.findViewById(R.id.tvNewStock);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
        }
    }
}