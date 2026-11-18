package com.example.cannon;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lector nativo ultraliviano de planillas Microsoft Excel (.xlsx).
 * Utiliza los parsers nativos de Android (ZipFile + XmlPullParser) sin dependencias pesadas.
 */
public class ExcelCatalogParser {

    private static final long MAX_XLSX_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_XML_ENTRY_BYTES = 25L * 1024L * 1024L;

    private static class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long totalRead = 0L;

        BoundedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                totalRead++;
                if (totalRead > maxBytes) {
                    throw new IOException("La entrada XML excede el tamaño máximo permitido");
                }
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                totalRead += n;
                if (totalRead > maxBytes) {
                    throw new IOException("La entrada XML excede el tamaño máximo permitido");
                }
            }
            return n;
        }
    }

    public static class SkuInfo {
        public final String producto;
        public final String plaza;

        public SkuInfo(String producto, String plaza) {
            this.producto = producto;
            this.plaza = plaza;
        }
    }

    public static class ResultadoExcel {
        public final HashMap<String, HashMap<String, Double>> precios = new HashMap<>();
        public final HashMap<String, Double> descuentos = new HashMap<>();
        public final HashMap<String, HashMap<String, Double>> descuentosPorPlaza = new HashMap<>();
        public final HashMap<String, String> categorias = new HashMap<>();
        public final HashMap<String, SkuInfo> skuMap = new HashMap<>();
    }

    public static ResultadoExcel parsear(Context context, InputStream inputStream) throws Exception {
        // 1. Guardar temporalmente en la memoria caché para acceso aleatorio a las entradas ZIP con tope de tamaño
        File tempFile = File.createTempFile("catalogo_", ".xlsx", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            long totalBytes = 0L;
            while ((read = inputStream.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > MAX_XLSX_BYTES) {
                    throw new IOException("El archivo Excel excede el tamaño máximo permitido (20 MB)");
                }
                fos.write(buffer, 0, read);
            }
        }

        ResultadoExcel resultado = new ResultadoExcel();

        try (ZipFile zipFile = new ZipFile(tempFile)) {
            // 2. Leer sharedStrings.xml y styles.xml si existen
            List<String> sharedStrings = new ArrayList<>();
            ZipEntry sharedStringsEntry = zipFile.getEntry("xl/sharedStrings.xml");
            if (sharedStringsEntry != null) {
                try (InputStream is = new BoundedInputStream(zipFile.getInputStream(sharedStringsEntry), MAX_XML_ENTRY_BYTES)) {
                    sharedStrings = leerSharedStrings(is);
                }
            }

            Set<Integer> estilosPorcentaje = new HashSet<>();
            ZipEntry stylesEntry = zipFile.getEntry("xl/styles.xml");
            if (stylesEntry != null) {
                try (InputStream is = new BoundedInputStream(zipFile.getInputStream(stylesEntry), MAX_XML_ENTRY_BYTES)) {
                    estilosPorcentaje = leerEstilosPorcentaje(is);
                }
            }

            // 3. Buscar la primera hoja de datos (sheet1.xml / Catálogo)
            ZipEntry sheetEntry = zipFile.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) {
                java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("xl/worksheets/sheet") && entry.getName().endsWith(".xml")) {
                        sheetEntry = entry;
                        break;
                    }
                }
            }
            if (sheetEntry == null) {
                throw new IllegalStateException("No se encontró la hoja de cálculo en el archivo Excel");
            }

            try (InputStream is = new BoundedInputStream(zipFile.getInputStream(sheetEntry), MAX_XML_ENTRY_BYTES)) {
                leerFilasSheet(is, sharedStrings, estilosPorcentaje, resultado);
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }

        return resultado;
    }

    private static Set<Integer> leerEstilosPorcentaje(InputStream is) throws Exception {
        Set<Integer> customPercentFmtIds = new HashSet<>();
        customPercentFmtIds.add(9);
        customPercentFmtIds.add(10);
        Set<Integer> percentStyleIndices = new HashSet<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        boolean insideCellXfs = false;
        int currentXfIndex = 0;
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if ("numFmt".equalsIgnoreCase(tag)) {
                    String idStr = parser.getAttributeValue(null, "numFmtId");
                    String fmtCode = parser.getAttributeValue(null, "formatCode");
                    if (idStr != null && fmtCode != null && contienePorcentajeFueraComillas(fmtCode)) {
                        try {
                            customPercentFmtIds.add(Integer.parseInt(idStr));
                        } catch (NumberFormatException ignored) {}
                    }
                } else if ("cellXfs".equalsIgnoreCase(tag)) {
                    insideCellXfs = true;
                    currentXfIndex = 0;
                } else if (insideCellXfs && "xf".equalsIgnoreCase(tag)) {
                    String numFmtIdStr = parser.getAttributeValue(null, "numFmtId");
                    if (numFmtIdStr != null) {
                        try {
                            int numFmtId = Integer.parseInt(numFmtIdStr);
                            if (customPercentFmtIds.contains(numFmtId)) {
                                percentStyleIndices.add(currentXfIndex);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    currentXfIndex++;
                }
            } else if (eventType == XmlPullParser.END_TAG && "cellXfs".equalsIgnoreCase(tag)) {
                insideCellXfs = false;
            }
            eventType = parser.next();
        }
        return percentStyleIndices;
    }

    private static boolean contienePorcentajeFueraComillas(String formatCode) {
        boolean enComillas = false;
        for (int i = 0; i < formatCode.length(); i++) {
            char c = formatCode.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '"') {
                enComillas = !enComillas;
            } else if (c == '%' && !enComillas) {
                return true;
            }
        }
        return false;
    }

    private static List<String> leerSharedStrings(InputStream is) throws Exception {
        List<String> list = new ArrayList<>();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        int eventType = parser.getEventType();
        StringBuilder currentText = new StringBuilder();
        boolean insideT = false;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if ("si".equalsIgnoreCase(tagName)) {
                    currentText.setLength(0);
                } else if ("t".equalsIgnoreCase(tagName)) {
                    insideT = true;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (insideT) {
                    currentText.append(parser.getText());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("t".equalsIgnoreCase(tagName)) {
                    insideT = false;
                } else if ("si".equalsIgnoreCase(tagName)) {
                    list.add(currentText.toString());
                    currentText.setLength(0);
                }
            }
            eventType = parser.next();
        }
        return list;
    }

    private static void leerFilasSheet(
            InputStream is,
            List<String> sharedStrings,
            Set<Integer> estilosPorcentaje,
            ResultadoExcel resultado) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        int eventType = parser.getEventType();
        String currentCellRef = "";
        String currentCellType = "";
        int currentCellStyleIdx = -1;
        StringBuilder cellValue = new StringBuilder();
        boolean insideV = false;
        boolean insideT = false;

        String filaSku = "";
        String filaProducto = "";
        String filaCategoria = "";
        String filaPlaza = "Única";
        double filaPrecio = 0.0;
        double filaDescuento = 0.0;
        int currentFilaNum = 0;

        // Mapeo dinámico de columnas (detectado en fila 1 o fallback por defecto)
        String colSku = "";
        String colProducto = "";
        String colCategoria = "";
        String colPlaza = "";
        String colPrecio = "";
        String colDescuento = "";

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if ("row".equalsIgnoreCase(tagName)) {
                    String rAttr = parser.getAttributeValue(null, "r");
                    if (rAttr != null) {
                        try {
                            currentFilaNum = Integer.parseInt(rAttr);
                        } catch (NumberFormatException ignored) {
                            currentFilaNum++;
                        }
                    } else {
                        currentFilaNum++;
                    }

                    if (currentFilaNum > 1 && colProducto.isEmpty()) {
                        colProducto = "B";
                        colCategoria = "C";
                        colPlaza = "D";
                        colPrecio = "E";
                        colDescuento = "F";
                    }

                    filaSku = "";
                    filaProducto = "";
                    filaCategoria = "";
                    filaPlaza = "Única";
                    filaPrecio = 0.0;
                    filaDescuento = 0.0;
                } else if ("c".equalsIgnoreCase(tagName)) {
                    currentCellRef = parser.getAttributeValue(null, "r");
                    currentCellType = parser.getAttributeValue(null, "t");
                    if (currentCellType == null) currentCellType = "n"; // por defecto numérico
                    String styleAttr = parser.getAttributeValue(null, "s");
                    currentCellStyleIdx = -1;
                    if (styleAttr != null && !styleAttr.isEmpty()) {
                        try {
                            currentCellStyleIdx = Integer.parseInt(styleAttr);
                        } catch (NumberFormatException ignored) {}
                    }
                    cellValue.setLength(0);
                } else if ("v".equalsIgnoreCase(tagName)) {
                    insideV = true;
                } else if ("t".equalsIgnoreCase(tagName)) {
                    insideT = true;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (insideV || insideT) {
                    cellValue.append(parser.getText());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("v".equalsIgnoreCase(tagName)) {
                    insideV = false;
                } else if ("t".equalsIgnoreCase(tagName)) {
                    insideT = false;
                } else if ("c".equalsIgnoreCase(tagName)) {
                    String col = extraerLetraColumna(currentCellRef);
                    String valorFinal = cellValue.toString().trim();
                    boolean esCeldaTexto = !"n".equalsIgnoreCase(currentCellType);

                    if ("s".equalsIgnoreCase(currentCellType)) {
                        try {
                            int idx = Integer.parseInt(valorFinal);
                            if (idx >= 0 && idx < sharedStrings.size()) {
                                valorFinal = sharedStrings.get(idx);
                            }
                        } catch (NumberFormatException ignored) {}
                    }

                    // Fila 1: Detectar automáticamente el orden de las columnas por el nombre del encabezado
                    if (currentFilaNum == 1) {
                        String headerUpper = valorFinal.toUpperCase();
                        if (headerUpper.contains("SKU") || headerUpper.contains("CODIGO") || headerUpper.contains("CÓDIGO")) {
                            colSku = col;
                        } else if (headerUpper.contains("PRODUCTO") || headerUpper.contains("ARTICULO") || headerUpper.contains("NOMBRE")) {
                            colProducto = col;
                        } else if (headerUpper.contains("CATEGOR")) {
                            colCategoria = col;
                        } else if (headerUpper.contains("PLAZA") || headerUpper.contains("MEDIDA")) {
                            colPlaza = col;
                        } else if (headerUpper.contains("PRECIO") || headerUpper.contains("NORMAL")) {
                            colPrecio = col;
                        } else if (headerUpper.contains("DESCUENTO") || headerUpper.contains("DCTO") || headerUpper.contains("%")) {
                            colDescuento = col;
                        }
                    } else {
                        // Filas de datos (2 en adelante)
                        if (!colSku.isEmpty() && col.equalsIgnoreCase(colSku)) {
                            filaSku = valorFinal;
                        } else if (!colProducto.isEmpty() && col.equalsIgnoreCase(colProducto)) {
                            filaProducto = valorFinal;
                        } else if (!colCategoria.isEmpty() && col.equalsIgnoreCase(colCategoria)) {
                            filaCategoria = valorFinal;
                        } else if (!colPlaza.isEmpty() && col.equalsIgnoreCase(colPlaza)) {
                            filaPlaza = valorFinal.isEmpty() ? "Única" : valorFinal;
                        } else if (!colPrecio.isEmpty() && col.equalsIgnoreCase(colPrecio)) {
                            try {
                                double p = Double.parseDouble(limpiarNumero(valorFinal, esCeldaTexto));
                                filaPrecio = (Double.isFinite(p) && p > 0.0) ? p : 0.0;
                            } catch (NumberFormatException e) {
                                filaPrecio = 0.0;
                            }
                        } else if (!colDescuento.isEmpty() && col.equalsIgnoreCase(colDescuento)) {
                            try {
                                boolean tieneSimboloPct = valorFinal.contains("%");
                                boolean esEstiloPct = !tieneSimboloPct && !esCeldaTexto && currentCellStyleIdx >= 0 && estilosPorcentaje.contains(currentCellStyleIdx);
                                double d = Double.parseDouble(limpiarNumero(valorFinal, false));
                                if (Double.isFinite(d)) {
                                    if (tieneSimboloPct) {
                                        filaDescuento = d;
                                    } else if (esEstiloPct) {
                                        filaDescuento = d * 100.0;
                                    } else {
                                        filaDescuento = (d > 0.0 && d < 1.0) ? (d * 100.0) : d;
                                    }
                                    filaDescuento = Math.max(0.0, Math.min(100.0, filaDescuento));
                                } else {
                                    filaDescuento = 0.0;
                                }
                            } catch (NumberFormatException e) {
                                filaDescuento = 0.0;
                            }
                        }
                    }
                } else if ("row".equalsIgnoreCase(tagName)) {
                    // Si no es la fila 1 y tiene nombre de producto y precio válido, registrar
                    if (currentFilaNum > 1 && !filaProducto.isEmpty() && !"Producto".equalsIgnoreCase(filaProducto) && filaPrecio > 0) {
                        if (!resultado.precios.containsKey(filaProducto)) {
                            resultado.precios.put(filaProducto, new HashMap<>());
                        }
                        resultado.precios.get(filaProducto).put(filaPlaza, filaPrecio);
                        if (!resultado.descuentosPorPlaza.containsKey(filaProducto)) {
                            resultado.descuentosPorPlaza.put(filaProducto, new HashMap<>());
                        }
                        resultado.descuentosPorPlaza.get(filaProducto).put(filaPlaza, filaDescuento);
                        if (!resultado.descuentos.containsKey(filaProducto) || "1.5".equalsIgnoreCase(filaPlaza)) {
                            resultado.descuentos.put(filaProducto, filaDescuento);
                        }
                        if (!filaCategoria.isEmpty()) {
                            resultado.categorias.put(filaProducto, filaCategoria);
                        }
                        if (!filaSku.isEmpty()) {
                            resultado.skuMap.put(filaSku.trim().toUpperCase(), new SkuInfo(filaProducto, filaPlaza));
                        }
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private static String limpiarNumero(String texto, boolean permitirMilesPunto) {
        if (texto == null) return "0";
        String limpio = texto.replace("$", "").replace("%", "").replace(" ", "").trim();
        if (limpio.isEmpty()) return "0";

        if (limpio.contains(".") && limpio.contains(",")) {
            int primerPunto = limpio.indexOf('.');
            int primerComa = limpio.indexOf(',');
            if (primerPunto < primerComa) {
                limpio = limpio.replace(".", "").replace(",", ".");
            } else {
                limpio = limpio.replace(",", "");
            }
        } else if (limpio.contains(",")) {
            int comaIdx = limpio.indexOf(',');
            if (permitirMilesPunto && limpio.length() - comaIdx == 4 && !limpio.substring(comaIdx + 1).contains(",")) {
                limpio = limpio.replace(",", "");
            } else {
                limpio = limpio.replace(",", ".");
            }
        } else if (limpio.contains(".")) {
            int primerPunto = limpio.indexOf('.');
            int ultimoPunto = limpio.lastIndexOf('.');
            if (primerPunto != ultimoPunto) {
                limpio = limpio.replace(".", "");
            } else if (permitirMilesPunto && limpio.length() - primerPunto == 4) {
                limpio = limpio.replace(".", "");
            }
        }
        return limpio;
    }

    private static String extraerLetraColumna(String cellRef) {
        if (cellRef == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : cellRef.toCharArray()) {
            if (Character.isLetter(ch)) {
                sb.append(ch);
            } else {
                break;
            }
        }
        return sb.toString().toUpperCase();
    }
}
