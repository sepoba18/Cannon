package com.example.cannon;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.ViewHolder> {

    public interface OnProductoClickListener {
        void onCotizar(ProductoItem producto);
        void onEditar(ProductoItem producto);
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
            String lower = query.trim().toLowerCase(Locale.getDefault());
            for (ProductoItem item : listaOriginal) {
                boolean coincideNombre = item.getNombre().toLowerCase(Locale.getDefault()).contains(lower);
                boolean coincideSku = item.getSkusBusqueda().toLowerCase(Locale.getDefault()).contains(lower);
                if (coincideNombre || coincideSku) {
                    listaFiltrada.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getItemCountFiltrado() {
        return listaFiltrada.size();
    }

    public static List<String> ordenarPlazasCannon(List<String> plazas) {
        List<String> ordenBase = List.of("1.0", "1.5", "2.0", "2.5", "3.0", "50 x 70", "50 x 90", "Única");
        plazas.sort((a, b) -> {
            int ia = ordenBase.indexOf(a);
            int ib = ordenBase.indexOf(b);
            if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
            if (ia >= 0) return -1;
            if (ib >= 0) return 1;
            return a.compareToIgnoreCase(b);
        });
        return plazas;
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

        if (item.getDescuento() > 0) {
            holder.tvDescuento.setVisibility(View.VISIBLE);
            holder.tvDescuento.setText(String.format(Locale.getDefault(), "-%.0f%%", item.getDescuento()));
        } else {
            holder.tvDescuento.setVisibility(View.GONE);
        }

        holder.chipGroupPlazas.removeAllViews();
        HashMap<String, Double> plazasMap = item.getPlazas();
        if (plazasMap != null && !plazasMap.isEmpty()) {
            holder.chipGroupPlazas.setVisibility(View.VISIBLE);
            holder.tvPlazas.setVisibility(View.GONE);
            Context ctx = holder.itemView.getContext();
            List<String> ordenadas = ordenarPlazasCannon(new ArrayList<>(plazasMap.keySet()));
            for (String plaza : ordenadas) {
                Double precio = plazasMap.get(plaza);
                if (precio == null || !Double.isFinite(precio)) continue;
                TextView pill = new TextView(ctx);
                pill.setBackgroundResource(R.drawable.bg_plaza_price_pill);
                pill.setText(String.format(Locale.getDefault(), "%s · $%,.0f", plaza, precio));
                pill.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
                pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                pill.setSingleLine(true);
                holder.chipGroupPlazas.addView(pill);
            }
        } else {
            holder.chipGroupPlazas.setVisibility(View.GONE);
            holder.tvPlazas.setVisibility(View.VISIBLE);
            holder.tvPlazas.setText(item.getPlazasFormateadas());
        }

        holder.btnCotizar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCotizar(item);
            }
        });

        holder.btnEditar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditar(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaFiltrada.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvDescuento, tvPlazas;
        ChipGroup chipGroupPlazas;
        MaterialButton btnCotizar, btnEditar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvItemNombre);
            tvDescuento = itemView.findViewById(R.id.tvItemDescuento);
            tvPlazas = itemView.findViewById(R.id.tvItemPlazas);
            chipGroupPlazas = itemView.findViewById(R.id.chipGroupItemPlazas);
            btnCotizar = itemView.findViewById(R.id.btnItemCotizar);
            btnEditar = itemView.findViewById(R.id.btnItemEditar);
        }
    }
}
