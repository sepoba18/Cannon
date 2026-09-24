package com.example.cannon;

import java.util.HashMap;
import java.util.Locale;

public class ProductoItem {
    private String nombre;
    private double descuento;
    private HashMap<String, Double> plazas;

    public ProductoItem(String nombre, double descuento, HashMap<String, Double> plazas) {
        this.nombre = nombre;
        this.descuento = descuento;
        this.plazas = plazas != null ? plazas : new HashMap<>();
    }

    public String getNombre() {
        return nombre;
    }

    public double getDescuento() {
        return descuento;
    }

    public void setDescuento(double descuento) {
        this.descuento = descuento;
    }

    public HashMap<String, Double> getPlazas() {
        return plazas;
    }

    public void agregarPlaza(String plaza, double precio) {
        this.plazas.put(plaza, precio);
    }

    /**
     * Retorna una representación formateada de las plazas y sus precios en pesos chilenos.
     */
    public String getPlazasFormateadas() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String plaza : plazas.keySet()) {
            Double precio = plazas.get(plaza);
            if (count > 0) sb.append("  •  ");
            sb.append(plaza).append(": $").append(String.format(Locale.getDefault(), "%,.0f", precio));
            count++;
        }
        return sb.toString();
    }
}
