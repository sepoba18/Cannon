package com.example.cannon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.ViewHolder> {

    public interface OnProductoClickListener {
        void onCotizar(ProductoItem producto);
    }

    private List<ProductoItem> listaOriginal = new ArrayList<>();
    private List<ProductoItem> listaFiltrada = new ArrayList<>();
    private OnProductoClickListener listener;

    public CatalogAdapter(List<ProductoItem> items, OnProductoClickListener listener) {
        this.listaOriginal = new ArrayList<>(items);
        this.listaFiltrada = new ArrayList<>(items);
        this.listener = listener;
    }

    public void actualizarDatos(List<ProductoItem> nuevosItems) {
        this.listaOriginal = new ArrayList<>(nuevosItems);
        this.listaFiltrada = new ArrayList<>(nuevosItems);
        notifyDataSetChanged();
    }

    public void filtrar(String query) {
        listaFiltrada.clear();
        if (query == null || query.trim().isEmpty()) {
            listaFiltrada.addAll(listaOriginal);
        } else {
            String lower = query.toLowerCase(Locale.getDefault());
            for (ProductoItem item : listaOriginal) {
                if (item.getNombre().toLowerCase(Locale.getDefault()).contains(lower)) {
                    listaFiltrada.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getItemCountFiltrado() {
        return listaFiltrada.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_catalog_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductoItem item = listaFiltrada.get(position);
        holder.tvNombre.setText(item.getNombre());
        holder.tvDescuento.setText(String.format(Locale.getDefault(), "%.0f%% DCTO", item.getDescuento()));
        holder.tvPlazas.setText(item.getPlazasFormateadas());

        holder.btnCotizar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCotizar(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaFiltrada.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvDescuento, tvPlazas;
        MaterialButton btnCotizar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvItemNombre);
            tvDescuento = itemView.findViewById(R.id.tvItemDescuento);
            tvPlazas = itemView.findViewById(R.id.tvItemPlazas);
            btnCotizar = itemView.findViewById(R.id.btnItemCotizar);
        }
    }
}
