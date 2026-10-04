package com.example.cannon;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lector nativo ultraliviano de planillas Microsoft Excel (.xlsx).
 * Utiliza los parsers nativos de Android (ZipFile + XmlPullParser) sin dependencias pesadas.
 */
public class ExcelCatalogParser {

    public static class ResultadoExcel {
        public final HashMap<String, HashMap<String, Double>> precios = new HashMap<>();
        public final HashMap<String, Double> descuentos = new HashMap<>();
        public final HashMap<String, String> categorias = new HashMap<>();
    }

    public static ResultadoExcel parsear(Context context, InputStream inputStream) throws Exception {
        // 1. Guardar temporalmente en la memoria caché para acceso aleatorio a las entradas ZIP
        File tempFile = new File(context.getCacheDir(), "catalogo_temp.xlsx");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        ResultadoExcel resultado = new ResultadoExcel();

        try (ZipFile zipFile = new ZipFile(tempFile)) {
            // 2. Leer sharedStrings.xml si existe (textos compartidos estándar de Excel)
            List<String> sharedStrings = new ArrayList<>();
            ZipEntry sharedStringsEntry = zipFile.getEntry("xl/sharedStrings.xml");
            if (sharedStringsEntry != null) {
                try (InputStream is = zipFile.getInputStream(sharedStringsEntry)) {
                    sharedStrings = leerSharedStrings(is);
                }
            }

            // 3. Buscar la primera hoja de datos (sheet1.xml / Catálogo)
            ZipEntry sheetEntry = zipFile.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) {
                throw new IllegalStateException("No se encontró la hoja de cálculo en el archivo Excel");
            }

            try (InputStream is = zipFile.getInputStream(sheetEntry)) {
                leerFilasSheet(is, sharedStrings, resultado);
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }

        return resultado;
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
                if ("t".equalsIgnoreCase(tagName)) {
                    insideT = true;
                    currentText.setLength(0);
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

    private static void leerFilasSheet(InputStream is, List<String> sharedStrings, ResultadoExcel resultado) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        int eventType = parser.getEventType();
        String currentCellRef = "";
        String currentCellType = "";
        StringBuilder cellValue = new StringBuilder();
        boolean insideV = false;
        boolean insideT = false;

        String filaProducto = "";
        String filaCategoria = "";
        String filaPlaza = "Única";
        double filaPrecio = 0.0;
        double filaDescuento = 0.0;
        int currentFilaNum = 0;

        // Mapeo dinámico de columnas (por defecto: A=Producto, B=Categoría, C=Plaza, D=Precio, E=Descuento)
        String colProducto = "A";
        String colCategoria = "B";
        String colPlaza = "C";
        String colPrecio = "D";
        String colDescuento = "E";

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

                    filaProducto = "";
                    filaCategoria = "";
                    filaPlaza = "Única";
                    filaPrecio = 0.0;
                    filaDescuento = 0.0;
                } else if ("c".equalsIgnoreCase(tagName)) {
                    currentCellRef = parser.getAttributeValue(null, "r");
                    currentCellType = parser.getAttributeValue(null, "t");
                    if (currentCellType == null) currentCellType = "n"; // por defecto numérico
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
                        if (headerUpper.contains("PRODUCTO") || headerUpper.contains("ARTICULO") || headerUpper.contains("NOMBRE")) {
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
                        if (col.equalsIgnoreCase(colProducto)) {
                            filaProducto = valorFinal;
                        } else if (col.equalsIgnoreCase(colCategoria)) {
                            filaCategoria = valorFinal;
                        } else if (col.equalsIgnoreCase(colPlaza)) {
                            filaPlaza = valorFinal.isEmpty() ? "Única" : valorFinal;
                        } else if (col.equalsIgnoreCase(colPrecio)) {
                            try {
                                filaPrecio = Double.parseDouble(limpiarNumero(valorFinal));
                            } catch (NumberFormatException e) {
                                filaPrecio = 0.0;
                            }
                        } else if (col.equalsIgnoreCase(colDescuento)) {
                            try {
                                double d = Double.parseDouble(limpiarNumero(valorFinal));
                                filaDescuento = (d > 0.0 && d <= 1.0) ? (d * 100.0) : d;
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
                        resultado.descuentos.put(filaProducto, filaDescuento);
                        if (!filaCategoria.isEmpty()) {
                            resultado.categorias.put(filaProducto, filaCategoria);
                        }
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private static String limpiarNumero(String texto) {
        if (texto == null) return "0";
        String limpio = texto.replace("$", "").replace("%", "").replace(" ", "").trim();
        if (limpio.contains(".") && !limpio.contains(",")) {
            int dotIdx = limpio.indexOf('.');
            if (limpio.length() - dotIdx == 4) {
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
