package com.example.cannon;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente liviano para consultar precios, descuentos y disponibilidad en vivo
 * desde la tienda oficial cannonhome.cl mediante su API GraphQL pública (Magento 2).
 */
public class CannonWebClient {

    public static final String GRAPHQL_ENDPOINT = "https://cannonhome.cl/graphql";
    private static final int TIMEOUT_MS = 8000;
    private static final String PRODUCT_FIELDS =
            "items { name sku url_key stock_status price_range { minimum_price { regular_price { value } final_price { value } discount { percent_off } } } }";
    private static final Set<String> STOPWORDS_BUSQUEDA = new HashSet<>(Arrays.asList(
            "plaza", "plazas", "king", "super", "unica",
            "del", "las", "los", "con", "para", "room"
    ));
    private static final String[] TIPOS_PRODUCTO = {
            "funda plumon", "cubrecolchon", "room spray",
            "plumon", "sabana", "quilt", "cubrecama", "almohada",
            "topper", "toalla", "alfombra", "funda", "frazada", "cojin",
            "piecera", "difusor", "spray", "vela", "bata", "pantufla",
            "colchon", "cama", "respaldo", "velador", "manta", "cortina",
            "mantel", "protector"
    };

    public static class WebProductInfo {
        public final String sku;
        public final String nombreWeb;
        public final String urlProducto;
        public final double precioNormalWeb;
        public final double precioFinalWeb;
        public final double porcentajeDescuentoWeb;
        public final boolean coincidenciaExactaSku;

        public WebProductInfo(
                String sku,
                String nombreWeb,
                String urlProducto,
                double precioNormalWeb,
                double precioFinalWeb,
                double porcentajeDescuentoWeb,
                boolean coincidenciaExactaSku) {
            this.sku = sku != null ? sku : "";
            this.nombreWeb = nombreWeb != null ? nombreWeb : "";
            this.urlProducto = urlProducto != null ? urlProducto : "";
            this.precioNormalWeb = precioNormalWeb;
            this.precioFinalWeb = precioFinalWeb;
            this.porcentajeDescuentoWeb = porcentajeDescuentoWeb;
            this.coincidenciaExactaSku = coincidenciaExactaSku;
        }
    }

    public interface WebCallback {
        void onIniciando();
        void onResultado(WebProductInfo info);
        void onNoEncontrado();
        void onError(String detalle);
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void consultarProducto(String sku, String nombreProducto, String plaza, WebCallback callback) {
        final String skuLimpio = sku != null ? sku.trim().toUpperCase(Locale.ROOT) : "";
        final String nombreLimpio = nombreProducto != null ? nombreProducto.trim() : "";
        final String plazaLimpia = plaza != null ? plaza.trim() : "";

        if (skuLimpio.isEmpty() && nombreLimpio.isEmpty()) {
            if (callback != null) {
                callback.onNoEncontrado();
            }
            return;
        }

        if (callback != null) {
            callback.onIniciando();
        }

        executor.execute(() -> {
            try {
                WebProductInfo encontrado = null;

                // Paso 1: Si tenemos SKU, consultar primero por SKU exacto en vivo
                if (!skuLimpio.isEmpty()) {
                    String querySku = "{ products(filter: { sku: { eq: \"" + escaparGraphql(skuLimpio) + "\" } }) { " + PRODUCT_FIELDS + " } }";
                    String respuestaSku = ejecutarPostGraphql(querySku);
                    encontrado = parsearRespuestaGraphql(respuestaSku, skuLimpio, plazaLimpia);
                }

                // Paso 2: Si no se encontró por SKU (o no había SKU), buscar por nombre + plaza
                String terminoConPlaza = "";
                if (encontrado == null && !nombreLimpio.isEmpty()) {
                    terminoConPlaza = construirTerminoBusqueda(nombreLimpio, plazaLimpia);
                    if (!terminoConPlaza.isEmpty()) {
                        encontrado = consultarPorTerminoNombre(terminoConPlaza, nombreLimpio, plazaLimpia);
                    }
                }

                // Paso 3: Fallback secundario por nombre sin plaza (priorizando la plaza preferida en el puntaje)
                if (encontrado == null && !nombreLimpio.isEmpty()) {
                    String terminoSinPlaza = construirTerminoBusqueda(nombreLimpio, "");
                    if (!terminoSinPlaza.isEmpty() && !terminoSinPlaza.equalsIgnoreCase(terminoConPlaza)) {
                        encontrado = consultarPorTerminoNombre(terminoSinPlaza, nombreLimpio, plazaLimpia);
                    }
                }

                if (encontrado != null) {
                    final WebProductInfo resultadoFinal = encontrado;
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onResultado(resultadoFinal);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onNoEncontrado();
                        }
                    });
                }
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Sin conexión con cannonhome.cl";
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(msg);
                    }
                });
            }
        });
    }

    private WebProductInfo consultarPorTerminoNombre(String termino, String nombreEsperado, String plazaPreferida) throws Exception {
        String queryNombre = "{ products(search: \"" + escaparGraphql(termino) + "\", sort: { relevance: DESC }, pageSize: 15) { " + PRODUCT_FIELDS + " } }";
        String respuestaNombre = ejecutarPostGraphql(queryNombre);
        return parsearRespuestaGraphqlConNombre(respuestaNombre, "", nombreEsperado, plazaPreferida);
    }

    private String ejecutarPostGraphql(String graphqlQuery) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GRAPHQL_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            conn.setRequestProperty("Pragma", "no-cache");
            conn.setRequestProperty("User-Agent", "CannonCotizadorApp/2.3 (Android)");

            JSONObject payload = new JSONObject();
            payload.put("query", graphqlQuery);
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                throw new IllegalStateException("Respuesta vacía HTTP " + code);
            }

            StringBuilder sb = new StringBuilder();
            int maxChars = 5 * 1024 * 1024;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    if (sb.length() + n > maxChars) {
                        throw new IllegalStateException("Respuesta GraphQL excede el tamaño máximo permitido");
                    }
                    sb.append(buf, 0, n);
                }
            }

            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parsea la respuesta JSON de GraphQL de cannonhome.cl de forma pura (testeable en JUnit).
     * Cuando {@code skuEsperado} no está vacío, exige coincidencia exacta de SKU.
     */
    public static WebProductInfo parsearRespuestaGraphql(String json, String skuEsperado, String plazaPreferida) {
        return parsearRespuestaGraphqlConNombre(json, skuEsperado, "", plazaPreferida);
    }

    public static WebProductInfo parsearRespuestaGraphqlConNombre(
            String json, String skuEsperado, String nombreEsperado, String plazaPreferida) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject products = data.optJSONObject("products");
            if (products == null) return null;
            JSONArray items = products.optJSONArray("items");
            if (items == null || items.length() == 0) return null;

            String skuTarget = skuEsperado != null ? skuEsperado.trim().toUpperCase(Locale.ROOT) : "";
            boolean validarNombre = skuTarget.isEmpty() && nombreEsperado != null && !nombreEsperado.trim().isEmpty();
            String locNorm = validarNombre
                    ? quitarAcentos(construirTerminoBusqueda(nombreEsperado, "")).toLowerCase(Locale.ROOT)
                    : "";
            String tipoDetectado = validarNombre ? detectarTipoProducto(locNorm) : null;
            JSONObject mejorItem = null;
            int mejorPuntaje = Integer.MIN_VALUE;

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;

                String itemSku = item.optString("sku", "").trim().toUpperCase(Locale.ROOT);
                String itemName = item.optString("name", "");

                // Regla estricta: si se busca un SKU concreto, jamás aceptar un SKU diferente
                if (!skuTarget.isEmpty() && !skuTarget.equals(itemSku)) {
                    continue;
                }

                // Regla estricta por nombre (solo cuando no hay SKU): exigir tipo + palabras clave del modelo
                int tokenMatches = 0;
                if (validarNombre) {
                    String webNorm = quitarAcentos(itemName).toLowerCase(Locale.ROOT);
                    if (locNorm.isEmpty() || webNorm.isEmpty()) continue;
                    if (tipoDetectado != null && !coincideTipoProducto(tipoDetectado, webNorm)) {
                        continue;
                    }
                    int[] conteo = contarCoincidenciasTokens(locNorm, webNorm, tipoDetectado);
                    if (tipoDetectado == null && conteo[0] == 0) {
                        continue;
                    }
                    if (conteo[1] < Math.min(2, conteo[0])) {
                        continue;
                    }
                    tokenMatches = conteo[1];
                }

                int puntaje = 0;
                if (!skuTarget.isEmpty() && skuTarget.equals(itemSku)) {
                    puntaje += 1000;
                }
                if (coincidePlazaEnNombre(itemName, plazaPreferida)) {
                    puntaje += 50;
                }
                if (validarNombre) {
                    puntaje += 20 * tokenMatches;
                }
                if ("IN_STOCK".equalsIgnoreCase(item.optString("stock_status", ""))) {
                    puntaje += 15;
                }
                puntaje += Math.max(0, 15 - i);

                if (puntaje > mejorPuntaje) {
                    mejorPuntaje = puntaje;
                    mejorItem = item;
                }
            }

            if (mejorItem == null) return null;

            String sku = mejorItem.optString("sku", "").trim();
            String nombre = mejorItem.optString("name", "").trim();
            String urlKey = mejorItem.optString("url_key", "").trim();
            String urlProducto;
            if (!urlKey.isEmpty()) {
                urlProducto = "https://cannonhome.cl/" + urlKey + ".html";
            } else {
                String q = !sku.isEmpty() ? sku : nombre;
                urlProducto = "https://cannonhome.cl/catalogsearch/result/?q="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8.name());
            }

            JSONObject priceRange = mejorItem.optJSONObject("price_range");
            JSONObject minPrice = priceRange != null ? priceRange.optJSONObject("minimum_price") : null;
            if (minPrice == null) return null;

            JSONObject regularObj = minPrice.optJSONObject("regular_price");
            JSONObject finalObj = minPrice.optJSONObject("final_price");
            JSONObject discountObj = minPrice.optJSONObject("discount");

            double regularPrice = regularObj != null ? regularObj.optDouble("value", 0.0) : 0.0;
            double finalPrice = finalObj != null ? finalObj.optDouble("value", 0.0) : 0.0;
            if (regularPrice <= 0 && finalPrice > 0) {
                regularPrice = finalPrice;
            }
            if (finalPrice <= 0 && regularPrice > 0) {
                finalPrice = regularPrice;
            }
            if (regularPrice <= 0 && finalPrice <= 0) {
                return null;
            }

            double percentOff = discountObj != null ? discountObj.optDouble("percent_off", 0.0) : 0.0;
            if (percentOff <= 0.0 && regularPrice > finalPrice) {
                percentOff = ((regularPrice - finalPrice) / regularPrice) * 100.0;
            }

            boolean exactaSku = !skuTarget.isEmpty() && skuTarget.equalsIgnoreCase(sku);
            return new WebProductInfo(sku, nombre, urlProducto, regularPrice, finalPrice, percentOff, exactaSku);
        } catch (Exception e) {
            return null;
        }
    }

    static String construirTerminoBusqueda(String nombreProducto, String plaza) {
        if (nombreProducto == null) return "";
        String limpio = nombreProducto
                .replaceAll("(?i)\\b(\\d{3,4})\\s*H\\b", "$1 Hilos")
                .replaceAll("(?i)\\b100%(?:\\s*Alg\\b)?", " ")
                .replaceAll("(?i)\\b(CN|RS|AF|EC|BCF|WW|STWSH)\\b", " ")
                .replaceAll("(?i)\\b[AWSVOI]\\d{2}\\b", " ")
                .replaceAll("(?i)\\bAlm\\b\\.?", "Almohada")
                .replaceAll("(?i)\\bSab\\b\\.?", "Sabana")
                .replaceAll("(?i)\\bFda\\s*P\\b\\.?", "Funda Plumon")
                .replaceAll("(?i)\\bQ\\b\\.?", "Quilt")
                .replaceAll("(?i)\\b(ESTMP|ESTAMP)\\b\\.?", "Estampado")
                .replaceAll("(?i)\\bPLUM\\b\\.?", "Pluma")
                .replaceAll("[^\\p{L}\\p{N}\\s.]", " ")
                .replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String plazaNormalizada = normalizarPlazaParaBusqueda(plaza);
        if (!plazaNormalizada.isEmpty() && !limpio.toLowerCase(Locale.ROOT).contains(plazaNormalizada.toLowerCase(Locale.ROOT))) {
            limpio = (limpio + " " + plazaNormalizada).trim();
        }
        return limpio;
    }

    private static String normalizarPlazaParaBusqueda(String plaza) {
        if (plaza == null) return "";
        String p = plaza.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() || p.equals("única") || p.equals("unica")) return "";
        if (p.equals("1.0") || p.equals("1")) return "1 plaza";
        if (p.equals("1.5")) return "1.5 plazas";
        if (p.equals("2.0") || p.equals("2")) return "2 plazas";
        if (p.equals("2.5")) return "king";
        if (p.equals("3.0") || p.equals("3")) return "super king";
        return plaza.trim();
    }

    private static boolean coincidePlazaEnNombre(String nombreWeb, String plazaPreferida) {
        if (nombreWeb == null || plazaPreferida == null || plazaPreferida.trim().isEmpty()) {
            return false;
        }
        String n = quitarAcentos(nombreWeb).toLowerCase(Locale.ROOT);
        String p = quitarAcentos(plazaPreferida).toLowerCase(Locale.ROOT).trim();

        if (n.contains(p)) return true;
        if (p.equals("1.0") || p.equals("1")) return n.contains("1 plaza");
        if (p.equals("1.5")) return n.contains("1.5") || n.contains("1,5");
        if (p.equals("2.0") || p.equals("2")) return n.contains("2 plaza");
        if (p.equals("2.5") || p.equals("king")) return n.contains("king") && !n.contains("super king");
        if (p.equals("3.0") || p.equals("super king")) return n.contains("super king");
        return false;
    }

    static boolean esCoincidenciaValidaPorNombre(String nombreLocal, String nombreWeb) {
        String loc = quitarAcentos(construirTerminoBusqueda(nombreLocal, "")).toLowerCase(Locale.ROOT);
        String web = quitarAcentos(nombreWeb != null ? nombreWeb : "").toLowerCase(Locale.ROOT);
        if (loc.isEmpty() || web.isEmpty()) return false;

        String tipoDetectado = detectarTipoProducto(loc);
        if (tipoDetectado != null && !coincideTipoProducto(tipoDetectado, web)) {
            return false;
        }

        int[] conteo = contarCoincidenciasTokens(loc, web, tipoDetectado);
        if (tipoDetectado == null && conteo[0] == 0) {
            return false;
        }
        return conteo[1] >= Math.min(2, conteo[0]);
    }

    private static String detectarTipoProducto(String loc) {
        if (loc.contains("cubre colchon")) {
            return "cubrecolchon";
        }
        for (String t : TIPOS_PRODUCTO) {
            if (loc.contains(t)) {
                return t;
            }
        }
        return null;
    }

    private static boolean coincideTipoProducto(String tipoDetectado, String web) {
        if ("funda plumon".equals(tipoDetectado)) {
            return web.contains("funda") && web.contains("plumon");
        }
        if ("cubrecolchon".equals(tipoDetectado)) {
            return web.contains("cubrecolchon") || web.contains("cubre colchon");
        }
        if ("quilt".equals(tipoDetectado) || "cubrecama".equals(tipoDetectado)) {
            return web.contains("quilt") || web.contains("cubrecama");
        }
        if ("room spray".equals(tipoDetectado)) {
            return web.contains("spray");
        }
        return web.contains(tipoDetectado);
    }

    private static int[] contarCoincidenciasTokens(String loc, String web, String tipoDetectado) {
        String[] palabrasTipo = tipoDetectado != null ? tipoDetectado.split("\\s+") : new String[0];
        String[] tokens = loc.split("\\s+");
        int significativos = 0;
        int coincidencias = 0;
        for (String tok : tokens) {
            if (tok.length() < 3 || STOPWORDS_BUSQUEDA.contains(tok)) {
                continue;
            }
            if (esTokenDeTipo(tok, tipoDetectado, palabrasTipo)) {
                continue;
            }
            significativos++;
            if (web.contains(tok) || (tok.length() >= 5 && tok.endsWith("s")
                    && web.contains(tok.substring(0, tok.length() - 1)))) {
                coincidencias++;
            }
        }
        return new int[]{significativos, coincidencias};
    }

    private static boolean esTokenDeTipo(String tok, String tipoDetectado, String[] palabrasTipo) {
        if ("cubrecolchon".equals(tipoDetectado) && ("cubre".equals(tok) || "colchon".equals(tok))) {
            return true;
        }
        for (String t : palabrasTipo) {
            if (tok.equals(t) || tok.equals(t + "s") || tok.equals(t + "es")) {
                return true;
            }
        }
        return false;
    }

    private static String quitarAcentos(String texto) {
        String norm = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String escaparGraphql(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
