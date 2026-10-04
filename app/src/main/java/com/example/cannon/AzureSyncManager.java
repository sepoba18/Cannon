package com.example.cannon;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Módulo independiente para gestión de sincronización en la nube con Microsoft Azure Blob Storage.
 */
public class AzureSyncManager {

    private static final String PREFS_NAME = "CannonPrefs";
    private static final String KEY_AZURE_URL = "azure_sync_url";
    private static final String KEY_LAST_SYNC = "azure_last_sync";

    public interface SyncCallback {
        void onIniciando();
        void onSuccess(HashMap<String, HashMap<String, Double>> precios, HashMap<String, Double> descuentos, String horaSync);
        void onError(String mensajeError, boolean esErrorConexion);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final String DEFAULT_AZURE_URL = "https://sebacannon2.blob.core.windows.net/catalogo/Plantilla_Catalogo_Cannon_12.xlsx";

    public AzureSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getUrlConfigurada() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = sp.getString(KEY_AZURE_URL, "");
        if (url.isEmpty() || url.endsWith("/Plantilla_Catalogo_Cannon.xlsx")) {
            return DEFAULT_AZURE_URL;
        }
        return url;
    }

    public void guardarUrl(String url) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_AZURE_URL, url != null ? url.trim() : "").apply();
    }

    public String getUltimaSincronizacion() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LAST_SYNC, "");
    }

    public boolean tieneUrlConfigurada() {
        return !getUrlConfigurada().isEmpty();
    }

    public void sincronizar(SyncCallback callback) {
        String urlNube = getUrlConfigurada();
        if (urlNube.isEmpty()) {
            if (callback != null) {
                callback.onError("No hay URL de Azure configurada.", false);
            }
            return;
        }

        if (callback != null) {
            callback.onIniciando();
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlNube);
                int redirects = 0;
                int responseCode = -1;

                while (redirects < 6) {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("Accept", "*/*");

                    responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                        responseCode == 307 || responseCode == 308) {
                        String location = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (location != null && !location.isEmpty()) {
                            url = new URL(location);
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                String contentType = conn.getContentType() != null ? conn.getContentType().toLowerCase(Locale.ROOT) : "";
                boolean esExcel = urlNube.toLowerCase(Locale.ROOT).contains(".xlsx") || 
                                  url.toString().toLowerCase(Locale.ROOT).contains(".xlsx") ||
                                  contentType.contains("spreadsheet") || 
                                  contentType.contains("excel") ||
                                  contentType.contains("octet-stream");

                if (responseCode == 200) {
                    HashMap<String, HashMap<String, Double>> nuevosPrecios = new HashMap<>();
                    HashMap<String, Double> nuevosDescuentos = new HashMap<>();

                    if (esExcel) {
                        ExcelCatalogParser.ResultadoExcel resExcel = ExcelCatalogParser.parsear(context, conn.getInputStream());
                        nuevosPrecios.putAll(resExcel.precios);
                        nuevosDescuentos.putAll(resExcel.descuentos);
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }

                        JSONArray arr = new JSONArray(response.toString());
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String nombre = obj.getString("nombre");
                            double dcto = obj.optDouble("descuento", 0.0);
                            JSONObject plazasObj = obj.getJSONObject("plazas");

                            HashMap<String, Double> mapPlazas = new HashMap<>();
                            Iterator<String> keys = plazasObj.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                mapPlazas.put(k, plazasObj.getDouble(k));
                            }
                            nuevosPrecios.put(nombre, mapPlazas);
                            nuevosDescuentos.put(nombre, dcto);
                        }
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String horaSync = "Sincronizado hoy " + sdf.format(new Date());

                    SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    sp.edit().putString(KEY_LAST_SYNC, horaSync).apply();

                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(nuevosPrecios, nuevosDescuentos, horaSync);
                        }
                    });
                } else {
                    final int errCode = responseCode;
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onError("Error de respuesta HTTP: " + errCode, false);
                        }
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("No se pudo conectar a la nube Azure.", true);
                    }
                });
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
