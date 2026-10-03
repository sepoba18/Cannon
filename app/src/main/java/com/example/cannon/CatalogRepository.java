package com.example.cannon;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Repositorio central de datos del catálogo Cannon.
 * Administra el caché en memoria RAM y la persistencia local en SharedPreferences.
 */
public class CatalogRepository {

    private static final String PREFS_NAME = "CannonPrefs";
    private static final String KEY_CUSTOM_CATALOG = "custom_catalog";

    private final Context context;
    private final HashMap<String, HashMap<String, Double>> catalogoPrecios = new HashMap<>();
    private final HashMap<String, Double> catalogoDescuentos = new HashMap<>();

    public CatalogRepository(Context context) {
        this.context = context.getApplicationContext();
        cargarDatos();
    }

    private void cargarDatos() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String customJson = sp.getString(KEY_CUSTOM_CATALOG, null);

        if (customJson != null && !customJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(customJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String nombre = obj.getString("nombre");
                    double dcto = obj.getDouble("descuento");
                    JSONObject plazasObj = obj.getJSONObject("plazas");

                    HashMap<String, Double> map = new HashMap<>();
                    Iterator<String> keys = plazasObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        map.put(key, plazasObj.getDouble(key));
                    }
                    catalogoPrecios.put(nombre, map);
                    catalogoDescuentos.put(nombre, dcto);
                }
                return;
            } catch (Exception ignored) {}
        }

        cargarCatalogoBase();
    }

    private void cargarCatalogoBase() {
        String[][] datosRaw = {
            {"QUILT Maria - 1.5", "72990", "30"}, {"QUILT Maria - 2.0", "92990", "30"}, {"QUILT Maria - 2.5", "102990", "30"}, {"QUILT Maria - 3.0", "112990", "30"},
            {"SAB 1000 H RS - 2.0", "229990", "20"}, {"SAB 1000 H RS - 2.5", "249990", "20"}, {"SAB 1000 H RS - 3.0", "269990", "20"},
            {"Fda P. OXF - 1.5", "69990", "50"}, {"Fda P. OXF - 2.0", "79990", "50"}, {"Fda P. OXF - 2.5", "89990", "50"},
            {"Fda P. LAUREN - 1.5", "64990", "50"}, {"Fda P. LAUREN - 2.0", "84990", "50"}, {"Fda P. LAUREN - 2.5", "94990", "50"}, {"Fda P. LAUREN - 3.0", "104990", "50"},
            {"Fda P. 180 HILOS - 1.5", "69990", "50"}, {"Fda P. 180 HILOS - 2.0", "89990", "50"}, {"Fda P. 180 HILOS - 2.5", "99990", "50"}, {"Fda P. 180 HILOS - 3.0", "109990", "50"},
            {"Fda P. JACQUARD - 2.0", "98990", "50"}, {"Fda P. JACQUARD - 2.5", "108990", "50"}, {"Fda P. JACQUARD - 3.0", "118990", "50"},
            {"Fda P. BORDADA - 1.5", "69990", "50"}, {"Fda P. BORDADA - 2.0", "89990", "50"}, {"Fda P. BORDADA - 2.5", "99990", "50"}, {"Fda P. BORDADA - 3.0", "109990", "50"},
            {"Fda P. LINO - 1.5", "94990", "50"}, {"Fda P. LINO - 2.0", "124990", "50"}, {"Fda P. LINO - 2.5", "134990", "50"},
            {"Fda P. ALMA - 1.5", "42990", "50"}, {"Fda P. ALMA - 2.0", "49990", "50"}, {"Fda P. ALMA - 2.5", "54990", "50"}, {"Fda P. ALMA - 3.0", "59990", "50"},
            {"Fda P. Verona - 1.5", "34990", "50"}, {"Fda P. Verona - 2.0", "39990", "50"}, {"Fda P. Verona - 2.5", "42990", "50"},
            {"VIVO - 1.0", "19990", "40"}, {"VIVO - 1.5", "22990", "40"}, {"VIVO - 2.0", "26990", "40"}, {"VIVO - 2.5", "32990", "40"}, {"VIVO - 3.0", "34990", "40"},
            {"TENCEL - 1.5", "44990", "40"}, {"TENCEL - 2.0", "54990", "40"}, {"TENCEL - 2.5", "64990", "40"}, {"TENCEL - 3.0", "74990", "40"},
            {"TOPPER - 1.5", "89990", "30"}, {"TOPPER - 2.0", "99990", "30"}, {"TOPPER - 2.5", "119990", "30"}, {"TOPPER - 3.0", "129990", "30"},
            {"TOPPER PLUM - 1.5", "149990", "30"}, {"TOPPER PLUM - 2.0", "199990", "30"}, {"TOPPER PLUM - 2.5", "209990", "30"}, {"TOPPER PLUM - 3.0", "219990", "30"},
            {"ALMOHADAS Visco CRISTAL", "62990", "30"}, {"ALMOHADAS Visco ZEN", "57990", "31"}, {"ALMOHADAS Visco GREEN", "59990", "40"},
            {"ALMOHADAS Visco ERGO PILOW", "34990", "30"}, {"ALMOHADAS Visco GEL ROYAL", "79990", "40"}, {"ALMOHADAS Visco Gel Cannon", "49990", "40"},
            {"ALMOHADAS Visco Dream", "26990", "41"},
            {"COPPER - 2.0", "44990", "33"}, {"COPPER - 2.5", "54990", "30"},
            {"SAB ESTMP 200H - 1.5", "44990", "20"}, {"SAB ESTMP 200H - 2.0", "56990", "20"}, {"SAB ESTMP 200H - 2.5", "64990", "20"}, {"SAB ESTMP 200H - 3.0", "69990", "20"},
            {"SAB LISA 200H 100% - 1.0", "44990", "50"}, {"SAB LISA 200H 100% - 1.5", "46990", "50"}, {"SAB LISA 200H 100% - 2.0", "59990", "50"}, {"SAB LISA 200H 100% - 2.5", "69990", "50"}, {"SAB LISA 200H 100% - 3.0", "74990", "50"},
            {"SAB RS 200H Bordadas - 1.5", "36990", "50"}, {"SAB RS 200H Bordadas - 2.0", "46990", "50"}, {"SAB RS 200H Bordadas - 2.5", "56990", "50"},
            {"SAB RS 200H ESTAMP - 1.5", "59990", "40"}, {"SAB RS 200H ESTAMP - 2.0", "69990", "40"}, {"SAB RS 200H ESTAMP - 2.5", "79990", "40"}, {"SAB RS 200H ESTAMP - 3.0", "89990", "40"},
            {"SAB 300H RS - 1.5", "69990", "40"}, {"SAB 300H RS - 2.0", "79990", "40"}, {"SAB 300H RS - 2.5", "89990", "40"}, {"SAB 300H RS - 3.0", "99990", "40"},
            {"SAB 300H CN - 1.5", "74990", "40"}, {"SAB 300H CN - 2.0", "86990", "40"}, {"SAB 300H CN - 2.5", "96990", "40"}, {"SAB 300H CN - 3.0", "106990", "40"},
            {"SAB 500 H CN - 1.5", "109990", "20"}, {"SAB 500 H CN - 2.0", "129990", "20"}, {"SAB 500 H CN - 2.5", "149990", "20"}, {"SAB 500 H CN - 3.0", "169990", "20"},
            {"SAB 600 H RS - 2.0", "149990", "20"}, {"SAB 600 H RS - 2.5", "159990", "20"}, {"SAB 600 H RS - 3.0", "179990", "20"},
            {"SAB 800 H RS - 2.0", "199990", "20"}, {"SAB 800 H RS - 2.5", "229990", "20"}, {"SAB 800 H RS - 3.0", "249990", "20"},
            {"PLUMON ESTAMP - 1.5", "59990", "20"}, {"PLUMON ESTAMP - 2.0", "69990", "20"}, {"PLUMON ESTAMP - 2.5", "79990", "20"}, {"PLUMON ESTAMP - 3.0", "84990", "20"},
            {"PLUMON ESTAMP (2) - 1.5", "42990", "40"}, {"PLUMON ESTAMP (2) - 2.0", "52990", "40"}, {"PLUMON ESTAMP (2) - 2.5", "56990", "40"}, {"PLUMON ESTAMP (2) - 3.0", "64990", "40"},
            {"Quilt Estampado - 1.5", "39990", "20"}, {"Quilt Estampado - 2.0", "49990", "20"}, {"Quilt Estampado - 2.5", "59990", "20"}, {"Quilt Estampado - 3.0", "69990", "20"},
            {"Quilt Liso Moss - 1.5", "39990", "20"}, {"Quilt Liso Moss - 2.0", "49990", "20"}, {"Quilt Liso Moss - 2.5", "99990", "20"}, {"Quilt Liso Moss - 3.0", "109990", "20"},
            {"QUILT VELVET STWSH - 1.5", "149990", "50"}, {"QUILT VELVET STWSH - 2.0", "159990", "50"}, {"QUILT VELVET STWSH - 2.5", "169990", "50"}, {"QUILT VELVET STWSH - 3.0", "179990", "50"},
            {"QUILT MATELADO - 1.5", "39990", "40"}, {"QUILT MATELADO - 2.0", "49990", "40"}, {"QUILT MATELADO - 2.5", "59990", "40"}, {"QUILT MATELADO - 3.0", "69990", "40"},
            {"Q. EC LEA - MAISON - 1.5", "39990", "50"}, {"Q. EC LEA - MAISON - 2.0", "49990", "50"}, {"Q. EC LEA - MAISON - 2.5", "59990", "50"}, {"Q. EC LEA - MAISON - 3.0", "69990", "50"},
            {"QUILT RS GARDEN - 1.5", "89990", "50"}, {"QUILT RS GARDEN - 2.0", "109990", "50"}, {"QUILT RS GARDEN - 2.5", "119990", "50"}, {"QUILT RS GARDEN - 3.0", "129990", "50"},
            {"QUILT NUIT RS - 1.5", "79990", "50"}, {"QUILT NUIT RS - 2.0", "99990", "50"}, {"QUILT NUIT RS - 2.5", "109990", "50"}, {"QUILT NUIT RS - 3.0", "119990", "50"},
            {"Q. RS VENT WILLOW - 1.5", "96990", "50"}, {"Q. RS VENT WILLOW - 2.0", "122990", "50"}, {"Q. RS VENT WILLOW - 2.5", "132990", "50"}, {"Q. RS VENT WILLOW - 3.0", "142990", "50"}
        };

        for (String[] d : datosRaw) {
            String fullNombre = d[0];
            double precio = Double.parseDouble(d[1]);
            double desc = Double.parseDouble(d[2]);

            String nombreBase = fullNombre;
            String plaza = "Única";

            if (fullNombre.contains(" - ")) {
                String[] partes = fullNombre.split(" - ");
                nombreBase = partes[0].trim();
                plaza = partes[1].trim();
            }

            if (!catalogoPrecios.containsKey(nombreBase)) {
                catalogoPrecios.put(nombreBase, new HashMap<>());
            }
            catalogoPrecios.get(nombreBase).put(plaza, precio);
            catalogoDescuentos.put(nombreBase, desc);
        }
    }

    public synchronized void guardarEnCache() {
        try {
            JSONArray arr = new JSONArray();
            for (String prod : catalogoPrecios.keySet()) {
                JSONObject obj = new JSONObject();
                obj.put("nombre", prod);
                obj.put("descuento", catalogoDescuentos.getOrDefault(prod, 0.0));

                JSONObject plazasObj = new JSONObject();
                HashMap<String, Double> mapPlazas = catalogoPrecios.get(prod);
                if (mapPlazas != null) {
                    for (String pl : mapPlazas.keySet()) {
                        plazasObj.put(pl, mapPlazas.get(pl));
                    }
                }
                obj.put("plazas", plazasObj);
                arr.put(obj);
            }

            SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_CUSTOM_CATALOG, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized List<ProductoItem> obtenerListaItems() {
        List<ProductoItem> lista = new ArrayList<>();
        List<String> nombres = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(nombres);

        for (String nombre : nombres) {
            double desc = catalogoDescuentos.getOrDefault(nombre, 0.0);
            HashMap<String, Double> plazas = catalogoPrecios.get(nombre);
            lista.add(new ProductoItem(nombre, desc, plazas));
        }
        return lista;
    }

    public synchronized List<String> obtenerNombresPorCategoria(String categoria) {
        List<String> filtrados = new ArrayList<>();
        List<String> todos = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(todos);

        for (String p : todos) {
            if ("Todos".equalsIgnoreCase(categoria) || p.toUpperCase().contains(categoria.toUpperCase())) {
                filtrados.add(p);
            }
        }
        return filtrados;
    }

    public synchronized boolean existeProducto(String nombre) {
        return catalogoPrecios.containsKey(nombre);
    }

    public synchronized HashMap<String, Double> obtenerPlazas(String nombre) {
        return catalogoPrecios.get(nombre);
    }

    public synchronized double obtenerDescuento(String nombre) {
        return catalogoDescuentos.getOrDefault(nombre, 0.0);
    }

    public synchronized void guardarOEditarProducto(String nombreViejo, String nombreNuevo, String plaza, double precio, double descuento) {
        boolean esCambioNombre = nombreViejo != null && !nombreViejo.trim().isEmpty() && !nombreViejo.equals(nombreNuevo);

        if (esCambioNombre) {
            HashMap<String, Double> mapViejo = catalogoPrecios.remove(nombreViejo);
            catalogoDescuentos.remove(nombreViejo);
            if (mapViejo == null) mapViejo = new HashMap<>();
            mapViejo.put(plaza, precio);
            catalogoPrecios.put(nombreNuevo, mapViejo);
            catalogoDescuentos.put(nombreNuevo, descuento);
        } else {
            if (!catalogoPrecios.containsKey(nombreNuevo)) {
                catalogoPrecios.put(nombreNuevo, new HashMap<>());
            }
            catalogoPrecios.get(nombreNuevo).put(plaza, precio);
            catalogoDescuentos.put(nombreNuevo, descuento);
        }

        guardarEnCache();
    }

    public synchronized void eliminarProducto(String nombre) {
        catalogoPrecios.remove(nombre);
        catalogoDescuentos.remove(nombre);
        guardarEnCache();
    }

    public synchronized void actualizarCatalogoCompleto(HashMap<String, HashMap<String, Double>> nuevosPrecios, HashMap<String, Double> nuevosDescuentos) {
        catalogoPrecios.clear();
        catalogoPrecios.putAll(nuevosPrecios);
        catalogoDescuentos.clear();
        catalogoDescuentos.putAll(nuevosDescuentos);
        guardarEnCache();
    }

    public int totalProductos() {
        return catalogoPrecios.size();
    }
}
