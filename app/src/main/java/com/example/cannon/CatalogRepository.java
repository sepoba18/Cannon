package com.example.cannon;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Repositorio central de datos del catálogo Cannon.
 * Administra el caché en memoria RAM y la persistencia local en SharedPreferences.
 */
public class CatalogRepository {

    public static class PlazaEntrada {
        public final String plaza;
        public final double precio;
        public final String sku;
        public final Double descuento;

        public PlazaEntrada(String plaza, double precio, String sku) {
            this(plaza, precio, sku, null);
        }

        public PlazaEntrada(String plaza, double precio, String sku, Double descuento) {
            this.plaza = (plaza != null && !plaza.trim().isEmpty()) ? plaza.trim() : "Única";
            this.precio = precio;
            this.sku = sku != null ? sku.trim().toUpperCase(Locale.ROOT) : "";
            this.descuento = descuento;
        }
    }

    private static class ManualProductData {
        double descuento;
        final HashMap<String, Double> plazas = new HashMap<>();
        final HashMap<String, Double> descuentosPorPlaza = new HashMap<>();
    }

    private static final String PREFS_NAME = "CannonPrefs";
    private static final String KEY_CUSTOM_CATALOG = "custom_catalog";
    private static final String KEY_CUSTOM_CATALOG_BACKUP = "custom_catalog_backup";
    private static final String KEY_CUSTOM_SKUS = "custom_skus";
    private static final String KEY_MANUAL_DISCOUNTS = "manual_discounts";
    private static final String KEY_MANUAL_PRODUCTS = "manual_products";
    private static final String KEY_MANUAL_SKUS = "manual_skus";
    private static final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private final Context context;
    private final HashMap<String, HashMap<String, Double>> catalogoPrecios = new HashMap<>();
    private final HashMap<String, Double> catalogoDescuentos = new HashMap<>();
    private final HashMap<String, HashMap<String, Double>> catalogoDescuentosPorPlaza = new HashMap<>();
    private final HashMap<String, ExcelCatalogParser.SkuInfo> mapaSkus = new HashMap<>();
    private final HashMap<String, String> mapaProductoPlazaASku = new HashMap<>();
    private final HashMap<String, String> mapaProductoASkus = new HashMap<>();
    private final HashMap<String, Double> descuentosManuales = new HashMap<>();
    private final HashMap<String, ManualProductData> productosManuales = new HashMap<>();
    private final HashMap<String, ExcelCatalogParser.SkuInfo> skusManuales = new HashMap<>();

    public CatalogRepository(Context context) {
        this.context = context.getApplicationContext();
        cargarDatos();
    }

    CatalogRepository() {
        this.context = null;
        cargarCatalogoBase();
        reconstruirIndicesSku();
    }

    private static void logError(String msg, Throwable t) {
        try {
            android.util.Log.e("CatalogRepository", msg, t);
        } catch (Throwable ignored) {}
    }

    private static boolean parsearCatalogoJson(
            String jsonStr,
            HashMap<String, HashMap<String, Double>> outPrecios,
            HashMap<String, Double> outDescuentos,
            HashMap<String, HashMap<String, Double>> outDescuentosPorPlaza) {
        if (jsonStr == null || jsonStr.isEmpty()) return false;
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String nombre = obj.optString("nombre", "").trim();
                if (nombre.isEmpty()) continue;
                double rawDcto = obj.optDouble("descuento", 0.0);
                double dcto = Double.isFinite(rawDcto) ? Math.max(0.0, Math.min(100.0, rawDcto)) : 0.0;
                JSONObject plazasObj = obj.optJSONObject("plazas");
                if (plazasObj == null) continue;

                HashMap<String, Double> map = new HashMap<>();
                Iterator<String> keys = plazasObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    double precio = plazasObj.optDouble(key, -1.0);
                    if (Double.isFinite(precio) && precio > 0.0) {
                        map.put(key, precio);
                    }
                }
                if (map.isEmpty()) continue;

                outPrecios.put(nombre, map);
                outDescuentos.put(nombre, dcto);

                JSONObject dctoPlazasObj = obj.optJSONObject("descuentos_plazas");
                if (dctoPlazasObj != null) {
                    HashMap<String, Double> mapDcto = new HashMap<>();
                    Iterator<String> dKeys = dctoPlazasObj.keys();
                    while (dKeys.hasNext()) {
                        String dk = dKeys.next();
                        double dp = dctoPlazasObj.optDouble(dk, dcto);
                        mapDcto.put(dk, Double.isFinite(dp) ? Math.max(0.0, Math.min(100.0, dp)) : dcto);
                    }
                    outDescuentosPorPlaza.put(nombre, mapDcto);
                }
            }
            return !outPrecios.isEmpty();
        } catch (Exception e) {
            logError("Error parseando catálogo JSON", e);
            return false;
        }
    }

    private void cargarDatos() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String customJson = sp.getString(KEY_CUSTOM_CATALOG, null);
        String backupJson = sp.getString(KEY_CUSTOM_CATALOG_BACKUP, null);

        HashMap<String, HashMap<String, Double>> tmpPrecios = new HashMap<>();
        HashMap<String, Double> tmpDescuentos = new HashMap<>();
        HashMap<String, HashMap<String, Double>> tmpDescuentosPorPlaza = new HashMap<>();

        boolean cargado = parsearCatalogoJson(customJson, tmpPrecios, tmpDescuentos, tmpDescuentosPorPlaza);
        if (!cargado && backupJson != null && !backupJson.equals(customJson)) {
            tmpPrecios.clear();
            tmpDescuentos.clear();
            tmpDescuentosPorPlaza.clear();
            cargado = parsearCatalogoJson(backupJson, tmpPrecios, tmpDescuentos, tmpDescuentosPorPlaza);
        }

        if (cargado) {
            catalogoPrecios.putAll(tmpPrecios);
            catalogoDescuentos.putAll(tmpDescuentos);
            catalogoDescuentosPorPlaza.putAll(tmpDescuentosPorPlaza);
        } else {
            cargarCatalogoBase();
        }

        // Cargar SKUs guardados en caché
        String skusJson = sp.getString(KEY_CUSTOM_SKUS, null);
        if (skusJson != null && !skusJson.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(skusJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String sku = keys.next();
                    JSONObject item = obj.optJSONObject(sku);
                    if (item == null) continue;
                    String prod = item.optString("producto", "");
                    String pl = item.optString("plaza", "Única");
                    if (!prod.isEmpty()) {
                        mapaSkus.put(sku, new ExcelCatalogParser.SkuInfo(prod, pl));
                    }
                }
            } catch (Exception e) {
                logError("Error cargando SKUs en caché", e);
            }
        }
        // Cargar productos agregados/editados manualmente (sobreviven a la sincronización XLSX)
        String manualProdsJson = sp.getString(KEY_MANUAL_PRODUCTS, null);
        if (manualProdsJson != null && !manualProdsJson.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(manualProdsJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String prod = keys.next();
                    JSONObject prodObj = obj.optJSONObject(prod);
                    if (prodObj == null) continue;
                    ManualProductData data = new ManualProductData();
                    data.descuento = prodObj.optDouble("descuento", 0.0);
                    JSONObject plazasObj = prodObj.optJSONObject("plazas");
                    if (plazasObj != null) {
                        Iterator<String> pKeys = plazasObj.keys();
                        while (pKeys.hasNext()) {
                            String pl = pKeys.next();
                            double pVal = plazasObj.optDouble(pl, -1.0);
                            if (Double.isFinite(pVal) && pVal > 0.0) {
                                data.plazas.put(pl, pVal);
                            }
                        }
                    }
                    JSONObject dctoPlazasObj = prodObj.optJSONObject("descuentos_plazas");
                    if (dctoPlazasObj != null) {
                        Iterator<String> dpKeys = dctoPlazasObj.keys();
                        while (dpKeys.hasNext()) {
                            String pl = dpKeys.next();
                            double dp = dctoPlazasObj.optDouble(pl, data.descuento);
                            if (Double.isFinite(dp)) {
                                data.descuentosPorPlaza.put(pl, Math.max(0.0, Math.min(100.0, dp)));
                            }
                        }
                    }
                    if (!data.plazas.isEmpty()) {
                        productosManuales.put(prod, data);
                    }
                }
            } catch (Exception e) {
                logError("Error cargando productos manuales", e);
            }
        }

        // Cargar SKUs agregados/editados manualmente (sobreviven a la sincronización XLSX)
        String manualSkusJson = sp.getString(KEY_MANUAL_SKUS, null);
        if (manualSkusJson != null && !manualSkusJson.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(manualSkusJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String sku = keys.next();
                    JSONObject item = obj.optJSONObject(sku);
                    if (item == null) continue;
                    skusManuales.put(sku, new ExcelCatalogParser.SkuInfo(
                            item.getString("producto"),
                            item.getString("plaza")
                    ));
                }
            } catch (Exception e) {
                logError("Error cargando SKUs manuales", e);
            }
        }

        reaplicarProductosManuales();
        reaplicarSkusManuales();
        reconstruirIndicesSku();

        // Cargar descuentos editados manualmente (prevalecen sobre el XLSX)
        String manualesJson = sp.getString(KEY_MANUAL_DISCOUNTS, null);
        if (manualesJson != null && !manualesJson.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(manualesJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String clave = keys.next();
                    double val = obj.optDouble(clave, Double.NaN);
                    if (Double.isFinite(val)) {
                        descuentosManuales.put(clave, Math.max(0.0, Math.min(100.0, val)));
                    }
                }
            } catch (Exception e) {
                logError("Error cargando descuentos manuales", e);
            }
        }
        reaplicarDescuentosManuales();
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
        if (context == null) return;
        // Snapshot en memoria (<1ms bajo synchronized)
        final HashMap<String, HashMap<String, Double>> snapshotPrecios = new HashMap<>();
        for (String prod : catalogoPrecios.keySet()) {
            HashMap<String, Double> mapPlazas = catalogoPrecios.get(prod);
            if (mapPlazas != null) {
                snapshotPrecios.put(prod, new HashMap<>(mapPlazas));
            }
        }
        final HashMap<String, Double> snapshotDescuentos = new HashMap<>(catalogoDescuentos);
        final HashMap<String, HashMap<String, Double>> snapshotDescuentosPorPlaza = new HashMap<>();
        for (String prod : catalogoDescuentosPorPlaza.keySet()) {
            HashMap<String, Double> mapDcto = catalogoDescuentosPorPlaza.get(prod);
            if (mapDcto != null) {
                snapshotDescuentosPorPlaza.put(prod, new HashMap<>(mapDcto));
            }
        }
        final HashMap<String, ExcelCatalogParser.SkuInfo> snapshotSkus = new HashMap<>(mapaSkus);
        final HashMap<String, Double> snapshotManuales = new HashMap<>(descuentosManuales);
        final HashMap<String, ManualProductData> snapshotManualProducts = new HashMap<>();
        for (String prod : productosManuales.keySet()) {
            ManualProductData src = productosManuales.get(prod);
            if (src != null) {
                ManualProductData copy = new ManualProductData();
                copy.descuento = src.descuento;
                copy.plazas.putAll(src.plazas);
                copy.descuentosPorPlaza.putAll(src.descuentosPorPlaza);
                snapshotManualProducts.put(prod, copy);
            }
        }
        final HashMap<String, ExcelCatalogParser.SkuInfo> snapshotManualSkus = new HashMap<>(skusManuales);

        // Serialización JSON y guardado en disco en hilo de fondo para evitar ANR
        ioExecutor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (String prod : snapshotPrecios.keySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("nombre", prod);
                    obj.put("descuento", snapshotDescuentos.getOrDefault(prod, 0.0));

                    JSONObject plazasObj = new JSONObject();
                    HashMap<String, Double> mapPlazas = snapshotPrecios.get(prod);
                    if (mapPlazas != null) {
                        for (String pl : mapPlazas.keySet()) {
                            plazasObj.put(pl, mapPlazas.get(pl));
                        }
                    }
                    obj.put("plazas", plazasObj);

                    HashMap<String, Double> mapDcto = snapshotDescuentosPorPlaza.get(prod);
                    if (mapDcto != null && !mapDcto.isEmpty()) {
                        JSONObject dctoPlazasObj = new JSONObject();
                        for (String pl : mapDcto.keySet()) {
                            dctoPlazasObj.put(pl, mapDcto.get(pl));
                        }
                        obj.put("descuentos_plazas", dctoPlazasObj);
                    }
                    arr.put(obj);
                }

                // Guardar SKUs en caché
                JSONObject skusObj = new JSONObject();
                for (String sku : snapshotSkus.keySet()) {
                    ExcelCatalogParser.SkuInfo info = snapshotSkus.get(sku);
                    if (info != null) {
                        JSONObject item = new JSONObject();
                        item.put("producto", info.producto);
                        item.put("plaza", info.plaza);
                        skusObj.put(sku, item);
                    }
                }

                JSONObject manualesObj = new JSONObject();
                for (String clave : snapshotManuales.keySet()) {
                    Double val = snapshotManuales.get(clave);
                    if (val != null) {
                        manualesObj.put(clave, val);
                    }
                }

                JSONObject manualProdsObj = new JSONObject();
                for (String prod : snapshotManualProducts.keySet()) {
                    ManualProductData data = snapshotManualProducts.get(prod);
                    if (data == null) continue;
                    JSONObject prodObj = new JSONObject();
                    prodObj.put("descuento", data.descuento);
                    JSONObject pObj = new JSONObject();
                    for (String pl : data.plazas.keySet()) {
                        pObj.put(pl, data.plazas.get(pl));
                    }
                    prodObj.put("plazas", pObj);
                    JSONObject dpObj = new JSONObject();
                    for (String pl : data.descuentosPorPlaza.keySet()) {
                        dpObj.put(pl, data.descuentosPorPlaza.get(pl));
                    }
                    prodObj.put("descuentos_plazas", dpObj);
                    manualProdsObj.put(prod, prodObj);
                }

                JSONObject manualSkusObj = new JSONObject();
                for (String sku : snapshotManualSkus.keySet()) {
                    ExcelCatalogParser.SkuInfo info = snapshotManualSkus.get(sku);
                    if (info != null) {
                        JSONObject item = new JSONObject();
                        item.put("producto", info.producto);
                        item.put("plaza", info.plaza);
                        manualSkusObj.put(sku, item);
                    }
                }

                SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit()
                        .putString(KEY_CUSTOM_SKUS, skusObj.toString())
                        .putString(KEY_MANUAL_DISCOUNTS, manualesObj.toString())
                        .putString(KEY_MANUAL_PRODUCTS, manualProdsObj.toString())
                        .putString(KEY_MANUAL_SKUS, manualSkusObj.toString());
                if (arr.length() > 0) {
                    String catalogJson = arr.toString();
                    editor.putString(KEY_CUSTOM_CATALOG, catalogJson)
                            .putString(KEY_CUSTOM_CATALOG_BACKUP, catalogJson);
                }
                editor.apply();
            } catch (Exception e) {
                logError("Error guardando caché del catálogo", e);
            }
        });
    }

    public synchronized List<ProductoItem> obtenerListaItems() {
        List<ProductoItem> lista = new ArrayList<>();
        List<String> nombres = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(nombres);

        for (String nombre : nombres) {
            double desc = catalogoDescuentos.getOrDefault(nombre, 0.0);
            HashMap<String, Double> plazas = catalogoPrecios.get(nombre);
            ProductoItem item = new ProductoItem(nombre, desc, plazas);
            item.setSkusBusqueda(mapaProductoASkus.getOrDefault(nombre, ""));
            lista.add(item);
        }
        return lista;
    }

    public synchronized List<String> obtenerNombresPorCategoria(String categoria) {
        List<String> filtrados = new ArrayList<>();
        List<String> todos = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(todos);
        String catSinAcentos = quitarAcentos(categoria).toUpperCase(Locale.ROOT);

        for (String p : todos) {
            if ("TODOS".equals(catSinAcentos) || quitarAcentos(p).toUpperCase(Locale.ROOT).contains(catSinAcentos)) {
                filtrados.add(p);
            }
        }
        return filtrados;
    }

    private static String quitarAcentos(String s) {
        if (s == null) return "";
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public synchronized boolean existeProducto(String nombre) {
        return catalogoPrecios.containsKey(nombre);
    }

    public synchronized HashMap<String, Double> obtenerPlazas(String nombre) {
        HashMap<String, Double> plazas = catalogoPrecios.get(nombre);
        return plazas != null ? new HashMap<>(plazas) : new HashMap<>();
    }

    public synchronized double obtenerDescuento(String nombre) {
        return catalogoDescuentos.getOrDefault(nombre, 0.0);
    }

    public synchronized double obtenerDescuento(String nombre, String plaza) {
        if (nombre == null || nombre.isEmpty()) return 0.0;
        if (plaza != null && !plaza.isEmpty()) {
            Double manual = descuentosManuales.get(claveProductoPlaza(nombre, plaza));
            if (manual != null) return manual;

            HashMap<String, Double> mapDcto = catalogoDescuentosPorPlaza.get(nombre);
            if (mapDcto != null && mapDcto.containsKey(plaza)) {
                return mapDcto.get(plaza);
            }
        }
        return obtenerDescuento(nombre);
    }

    public synchronized boolean tieneDescuentoManual(String nombre, String plaza) {
        if (nombre == null || plaza == null) return false;
        return descuentosManuales.containsKey(claveProductoPlaza(nombre, plaza));
    }

    public synchronized void guardarDescuentoManual(String producto, String plaza, double descuento) {
        if (producto == null || producto.trim().isEmpty()) return;
        String plazaFinal = (plaza == null || plaza.trim().isEmpty()) ? "Única" : plaza.trim();
        double dctoNorm = Math.max(0.0, Math.min(100.0, descuento));

        descuentosManuales.put(claveProductoPlaza(producto, plazaFinal), dctoNorm);
        if (!catalogoDescuentosPorPlaza.containsKey(producto)) {
            catalogoDescuentosPorPlaza.put(producto, new HashMap<>());
        }
        catalogoDescuentosPorPlaza.get(producto).put(plazaFinal, dctoNorm);
        catalogoDescuentos.put(producto, dctoNorm);
        guardarEnCache();
    }

    public synchronized void guardarOEditarProducto(String nombreViejo, String nombreNuevo, String plaza, double precio, double descuento) {
        if (nombreNuevo == null || nombreNuevo.trim().isEmpty()) return;
        String nombreLimpio = nombreNuevo.trim();
        String plazaFinal = (plaza != null && !plaza.trim().isEmpty()) ? plaza.trim() : "Única";
        double dctoNorm = Math.max(0.0, Math.min(100.0, descuento));
        boolean esCambioNombre = nombreViejo != null && !nombreViejo.trim().isEmpty() && !nombreViejo.equals(nombreLimpio);

        if (esCambioNombre) {
            HashMap<String, Double> mapViejo = catalogoPrecios.remove(nombreViejo);
            catalogoDescuentos.remove(nombreViejo);
            HashMap<String, Double> mapDctoViejo = catalogoDescuentosPorPlaza.remove(nombreViejo);
            productosManuales.remove(nombreViejo);
            renombrarClavesSecundarias(nombreViejo, nombreLimpio);
            if (mapViejo == null) mapViejo = new HashMap<>();
            mapViejo.put(plazaFinal, precio);
            catalogoPrecios.put(nombreLimpio, mapViejo);
            catalogoDescuentos.put(nombreLimpio, dctoNorm);
            if (mapDctoViejo == null) mapDctoViejo = new HashMap<>();
            mapDctoViejo.put(plazaFinal, dctoNorm);
            catalogoDescuentosPorPlaza.put(nombreLimpio, mapDctoViejo);
        } else {
            if (!catalogoPrecios.containsKey(nombreLimpio)) {
                catalogoPrecios.put(nombreLimpio, new HashMap<>());
            }
            catalogoPrecios.get(nombreLimpio).put(plazaFinal, precio);
            catalogoDescuentos.put(nombreLimpio, dctoNorm);
            if (!catalogoDescuentosPorPlaza.containsKey(nombreLimpio)) {
                catalogoDescuentosPorPlaza.put(nombreLimpio, new HashMap<>());
            }
            catalogoDescuentosPorPlaza.get(nombreLimpio).put(plazaFinal, dctoNorm);
        }

        ManualProductData manual = productosManuales.get(nombreLimpio);
        if (manual == null) {
            manual = new ManualProductData();
            productosManuales.put(nombreLimpio, manual);
        }
        manual.descuento = dctoNorm;
        manual.plazas.put(plazaFinal, precio);
        manual.descuentosPorPlaza.put(plazaFinal, dctoNorm);

        descuentosManuales.put(claveProductoPlaza(nombreLimpio, plazaFinal), dctoNorm);
        reconstruirIndicesSku();
        guardarEnCache();
    }

    public synchronized void guardarProductoCompleto(
            String nombreViejo,
            String nombreNuevo,
            double descuentoGeneral,
            List<PlazaEntrada> entradas) {
        if (nombreNuevo == null || nombreNuevo.trim().isEmpty() || entradas == null || entradas.isEmpty()) {
            return;
        }
        String nombreLimpio = nombreNuevo.trim();
        double dctoNorm = Math.max(0.0, Math.min(100.0, descuentoGeneral));
        boolean esCambioNombre = nombreViejo != null && !nombreViejo.trim().isEmpty() && !nombreViejo.equals(nombreLimpio);

        if (esCambioNombre) {
            catalogoPrecios.remove(nombreViejo);
            catalogoDescuentos.remove(nombreViejo);
            catalogoDescuentosPorPlaza.remove(nombreViejo);
            productosManuales.remove(nombreViejo);
            limpiarDescuentosManualesDeProducto(nombreViejo);
        }

        HashMap<String, Double> nuevosPreciosProd = new HashMap<>();
        HashMap<String, Double> nuevosDctosProd = new HashMap<>();
        ManualProductData manual = new ManualProductData();
        manual.descuento = dctoNorm;

        Set<String> plazasNuevas = new HashSet<>();
        for (PlazaEntrada entrada : entradas) {
            if (entrada == null) continue;
            String pl = entrada.plaza;
            double dctoPlaza = (entrada.descuento != null && Double.isFinite(entrada.descuento))
                    ? Math.max(0.0, Math.min(100.0, entrada.descuento))
                    : dctoNorm;
            plazasNuevas.add(pl);
            nuevosPreciosProd.put(pl, entrada.precio);
            nuevosDctosProd.put(pl, dctoPlaza);
            manual.plazas.put(pl, entrada.precio);
            manual.descuentosPorPlaza.put(pl, dctoPlaza);
            descuentosManuales.put(claveProductoPlaza(nombreLimpio, pl), dctoPlaza);
        }

        catalogoPrecios.put(nombreLimpio, nuevosPreciosProd);
        catalogoDescuentos.put(nombreLimpio, dctoNorm);
        catalogoDescuentosPorPlaza.put(nombreLimpio, nuevosDctosProd);
        recalcularDescuentoGeneralProducto(nombreLimpio);
        manual.descuento = catalogoDescuentos.getOrDefault(nombreLimpio, dctoNorm);
        productosManuales.put(nombreLimpio, manual);

        // Actualizar SKUs asociados a nombreViejo / nombreLimpio
        eliminarSkusObsoletosDeProducto(nombreViejo, nombreLimpio, plazasNuevas, entradas);
        for (PlazaEntrada entrada : entradas) {
            if (entrada != null && !entrada.sku.isEmpty()) {
                ExcelCatalogParser.SkuInfo info = new ExcelCatalogParser.SkuInfo(nombreLimpio, entrada.plaza);
                mapaSkus.put(entrada.sku, info);
                skusManuales.put(entrada.sku, info);
            }
        }

        reconstruirIndicesSku();
        guardarEnCache();
    }

    public synchronized void eliminarProducto(String nombre) {
        if (nombre == null) return;
        catalogoPrecios.remove(nombre);
        catalogoDescuentos.remove(nombre);
        catalogoDescuentosPorPlaza.remove(nombre);
        productosManuales.remove(nombre);
        limpiarDescuentosManualesDeProducto(nombre);
        eliminarTodosSkusDeProducto(nombre);
        reconstruirIndicesSku();
        guardarEnCache();
    }

    public synchronized void actualizarCatalogoCompleto(
            HashMap<String, HashMap<String, Double>> nuevosPrecios,
            HashMap<String, Double> nuevosDescuentos,
            HashMap<String, HashMap<String, Double>> nuevosDescuentosPorPlaza,
            HashMap<String, ExcelCatalogParser.SkuInfo> nuevosSkus) {
        catalogoPrecios.clear();
        catalogoPrecios.putAll(nuevosPrecios);
        catalogoDescuentos.clear();
        catalogoDescuentos.putAll(nuevosDescuentos);
        catalogoDescuentosPorPlaza.clear();
        if (nuevosDescuentosPorPlaza != null) {
            for (String prod : nuevosDescuentosPorPlaza.keySet()) {
                HashMap<String, Double> map = nuevosDescuentosPorPlaza.get(prod);
                if (map != null) {
                    catalogoDescuentosPorPlaza.put(prod, new HashMap<>(map));
                }
            }
        }
        if (nuevosSkus != null) {
            mapaSkus.clear();
            mapaSkus.putAll(nuevosSkus);
        }
        reaplicarProductosManuales();
        reaplicarSkusManuales();
        reconstruirIndicesSku();
        reaplicarDescuentosManuales();
        guardarEnCache();
    }

    public synchronized void actualizarCatalogoCompleto(
            HashMap<String, HashMap<String, Double>> nuevosPrecios,
            HashMap<String, Double> nuevosDescuentos,
            HashMap<String, ExcelCatalogParser.SkuInfo> nuevosSkus) {
        actualizarCatalogoCompleto(nuevosPrecios, nuevosDescuentos, null, nuevosSkus);
    }

    public synchronized void actualizarCatalogoCompleto(
            HashMap<String, HashMap<String, Double>> nuevosPrecios,
            HashMap<String, Double> nuevosDescuentos) {
        actualizarCatalogoCompleto(nuevosPrecios, nuevosDescuentos, null, null);
    }

    public synchronized ExcelCatalogParser.SkuInfo buscarPorSku(String sku) {
        if (sku == null || sku.trim().isEmpty()) return null;
        String limpio = sku.trim().toUpperCase(Locale.ROOT);
        return mapaSkus.get(limpio);
    }

    public synchronized String obtenerSku(String nombre, String plaza) {
        return obtenerSkuExacto(nombre, plaza);
    }

    public synchronized String obtenerSkuExacto(String nombre, String plaza) {
        if (nombre == null || nombre.trim().isEmpty() || plaza == null || plaza.trim().isEmpty()) {
            return "";
        }
        String exacto = mapaProductoPlazaASku.get(claveProductoPlaza(nombre, plaza));
        return exacto != null ? exacto : "";
    }

    public synchronized void registrarSkuManual(String sku, String nombre, String plaza) {
        if (sku == null || sku.trim().isEmpty() || nombre == null || nombre.trim().isEmpty()) return;
        String skuLimpio = sku.trim().toUpperCase(Locale.ROOT);
        String plazaLimpia = (plaza == null || plaza.trim().isEmpty()) ? "Única" : plaza.trim();
        ExcelCatalogParser.SkuInfo info = new ExcelCatalogParser.SkuInfo(nombre.trim(), plazaLimpia);
        mapaSkus.put(skuLimpio, info);
        skusManuales.put(skuLimpio, info);
        reconstruirIndicesSku();
        guardarEnCache();
    }

    private void renombrarClavesSecundarias(String nombreViejo, String nombreNuevo) {
        HashMap<String, Double> nuevasManuales = new HashMap<>();
        Iterator<String> it = descuentosManuales.keySet().iterator();
        String prefijo = nombreViejo + "||";
        while (it.hasNext()) {
            String clave = it.next();
            if (clave.startsWith(prefijo)) {
                Double val = descuentosManuales.get(clave);
                String pl = clave.substring(prefijo.length());
                if (val != null) {
                    nuevasManuales.put(claveProductoPlaza(nombreNuevo, pl), val);
                }
                it.remove();
            }
        }
        descuentosManuales.putAll(nuevasManuales);

        for (String sku : new ArrayList<>(mapaSkus.keySet())) {
            ExcelCatalogParser.SkuInfo info = mapaSkus.get(sku);
            if (info != null && info.producto.equals(nombreViejo)) {
                mapaSkus.put(sku, new ExcelCatalogParser.SkuInfo(nombreNuevo, info.plaza));
            }
        }
        for (String sku : new ArrayList<>(skusManuales.keySet())) {
            ExcelCatalogParser.SkuInfo info = skusManuales.get(sku);
            if (info != null && info.producto.equals(nombreViejo)) {
                skusManuales.put(sku, new ExcelCatalogParser.SkuInfo(nombreNuevo, info.plaza));
            }
        }
    }

    private void limpiarDescuentosManualesDeProducto(String producto) {
        if (producto == null) return;
        String prefijo = producto + "||";
        Iterator<String> it = descuentosManuales.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefijo)) {
                it.remove();
            }
        }
    }

    private void eliminarTodosSkusDeProducto(String producto) {
        if (producto == null) return;
        eliminarDeMapaPorProducto(mapaSkus, producto);
        eliminarDeMapaPorProducto(skusManuales, producto);
    }

    private static void eliminarDeMapaPorProducto(HashMap<String, ExcelCatalogParser.SkuInfo> mapa, String producto) {
        Iterator<String> it = mapa.keySet().iterator();
        while (it.hasNext()) {
            ExcelCatalogParser.SkuInfo info = mapa.get(it.next());
            if (info != null && info.producto.equals(producto)) {
                it.remove();
            }
        }
    }

    private void eliminarSkusObsoletosDeProducto(
            String nombreViejo,
            String nombreNuevo,
            Set<String> plazasNuevas,
            List<PlazaEntrada> entradas) {
        HashMap<String, String> skuPorPlazaNueva = new HashMap<>();
        for (PlazaEntrada entrada : entradas) {
            if (entrada != null) {
                skuPorPlazaNueva.put(entrada.plaza, entrada.sku);
            }
        }
        limpiarSkusObsoletosEnMapa(mapaSkus, nombreViejo, nombreNuevo, plazasNuevas, skuPorPlazaNueva);
        limpiarSkusObsoletosEnMapa(skusManuales, nombreViejo, nombreNuevo, plazasNuevas, skuPorPlazaNueva);
    }

    private static void limpiarSkusObsoletosEnMapa(
            HashMap<String, ExcelCatalogParser.SkuInfo> mapa,
            String nombreViejo,
            String nombreNuevo,
            Set<String> plazasNuevas,
            HashMap<String, String> skuPorPlazaNueva) {
        Iterator<String> it = mapa.keySet().iterator();
        while (it.hasNext()) {
            String sku = it.next();
            ExcelCatalogParser.SkuInfo info = mapa.get(sku);
            if (info == null) continue;
            boolean esProducto = info.producto.equals(nombreNuevo)
                    || (nombreViejo != null && info.producto.equals(nombreViejo));
            if (!esProducto) continue;
            if (!plazasNuevas.contains(info.plaza)) {
                it.remove();
                continue;
            }
            String skuObjetivo = skuPorPlazaNueva.get(info.plaza);
            if (skuObjetivo == null || !skuObjetivo.equalsIgnoreCase(sku)) {
                it.remove();
            }
        }
    }

    private void reaplicarProductosManuales() {
        for (String prod : productosManuales.keySet()) {
            ManualProductData manual = productosManuales.get(prod);
            if (manual == null || manual.plazas.isEmpty()) continue;

            HashMap<String, Double> targetPlazas = catalogoPrecios.get(prod);
            if (targetPlazas == null) {
                targetPlazas = new HashMap<>();
                catalogoPrecios.put(prod, targetPlazas);
            }
            targetPlazas.putAll(manual.plazas);
            catalogoDescuentos.put(prod, manual.descuento);

            HashMap<String, Double> targetDctos = catalogoDescuentosPorPlaza.get(prod);
            if (targetDctos == null) {
                targetDctos = new HashMap<>();
                catalogoDescuentosPorPlaza.put(prod, targetDctos);
            }
            targetDctos.putAll(manual.descuentosPorPlaza);
        }
    }

    private void reaplicarSkusManuales() {
        for (String sku : skusManuales.keySet()) {
            ExcelCatalogParser.SkuInfo info = skusManuales.get(sku);
            if (info != null) {
                mapaSkus.put(sku, info);
            }
        }
    }

    private void reconstruirIndicesSku() {
        mapaProductoPlazaASku.clear();
        mapaProductoASkus.clear();
        HashMap<String, StringBuilder> builders = new HashMap<>();
        for (String sku : mapaSkus.keySet()) {
            ExcelCatalogParser.SkuInfo info = mapaSkus.get(sku);
            if (info == null) continue;
            mapaProductoPlazaASku.put(claveProductoPlaza(info.producto, info.plaza), sku);
            StringBuilder sb = builders.get(info.producto);
            if (sb == null) {
                sb = new StringBuilder();
                builders.put(info.producto, sb);
            } else {
                sb.append(" ");
            }
            sb.append(sku);
        }
        for (String prod : builders.keySet()) {
            mapaProductoASkus.put(prod, builders.get(prod).toString());
        }
    }

    private void reaplicarDescuentosManuales() {
        Set<String> prodsAfectados = new HashSet<>();
        for (String clave : descuentosManuales.keySet()) {
            Double dcto = descuentosManuales.get(clave);
            if (dcto == null) continue;
            int sep = clave.indexOf("||");
            if (sep <= 0) continue;
            String prod = clave.substring(0, sep);
            String plaza = clave.substring(sep + 2);
            if (!catalogoPrecios.containsKey(prod)) continue;

            if (!catalogoDescuentosPorPlaza.containsKey(prod)) {
                catalogoDescuentosPorPlaza.put(prod, new HashMap<>());
            }
            catalogoDescuentosPorPlaza.get(prod).put(plaza, dcto);
            prodsAfectados.add(prod);
        }
        for (String prod : prodsAfectados) {
            recalcularDescuentoGeneralProducto(prod);
        }
    }

    private void recalcularDescuentoGeneralProducto(String prod) {
        HashMap<String, Double> map = catalogoDescuentosPorPlaza.get(prod);
        if (map == null || map.isEmpty()) return;
        String[] prioridad = {"1.5", "2.0", "Única", "1.0", "2.5", "3.0"};
        for (String pl : prioridad) {
            Double v = map.get(pl);
            if (v != null && Double.isFinite(v)) {
                catalogoDescuentos.put(prod, v);
                return;
            }
        }
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            Double v = map.get(k);
            if (v != null && Double.isFinite(v)) {
                catalogoDescuentos.put(prod, v);
                return;
            }
        }
    }

    private static String claveProductoPlaza(String producto, String plaza) {
        String p = producto != null ? producto.trim() : "";
        String pl = plaza != null ? plaza.trim() : "";
        return p + "||" + pl;
    }

    public synchronized int totalSkus() {
        return mapaSkus.size();
    }

    public int totalProductos() {
        return catalogoPrecios.size();
    }
}
