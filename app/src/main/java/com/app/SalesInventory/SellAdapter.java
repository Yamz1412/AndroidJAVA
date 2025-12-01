package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SellAdapter extends RecyclerView.Adapter<SellAdapter.VH> {
    private Context context;
    private List<Product> products = new ArrayList<>();

    public SellAdapter(Context context, List<Product> products) {
        this.context = context;
        if (products != null) {
            this.products = new ArrayList<>(products);
        }
    }

    public void updateProducts(List<Product> newProducts) {
        products.clear();
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        notifyDataSetChanged();
    }

    public Product getItem(int position) {
        return products.get(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.productsell, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = products.get(position);
        if (p == null) return;

        String imageUrl = p.getImageUrl();
        String imagePath = p.getImagePath();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else if (imagePath != null && !imagePath.isEmpty()) {
            File f = new File(imagePath);
            Glide.with(context)
                    .load(f.exists() ? f : null)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.nameTV.setText(p.getProductName());
        holder.codeTV.setText(p.getProductId() != null ? p.getProductId() : "");
        holder.amountTV.setText(String.valueOf(p.getQuantity()));
        holder.priceTV.setText("₱ " + String.format("%.2f", p.getSellingPrice()));
        if (p.getBarcode() != null && !p.getBarcode().isEmpty()) {
            holder.lotTV.setText(p.getBarcode());
            holder.lotTV.setVisibility(View.VISIBLE);
        } else {
            holder.lotTV.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView nameTV, codeTV, amountTV, priceTV, lotTV;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImageSell);
            nameTV = itemView.findViewById(R.id.NameTVS11);
            codeTV = itemView.findViewById(R.id.CodeTVS11);
            amountTV = itemView.findViewById(R.id.AmountTVS11);
            priceTV = itemView.findViewById(R.id.SellPriceTVS11);
            lotTV = itemView.findViewById(R.id.LotTVS11);
        }
    }
}