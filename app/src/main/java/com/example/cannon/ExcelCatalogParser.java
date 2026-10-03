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

            // 3. Buscar la primera hoja de datos (sheet1.xml / Resumen General)
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
        String filaPlaza = "Única";
        double filaPrecio = 0.0;
        double filaDescuento = 0.0;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if ("row".equalsIgnoreCase(tagName)) {
                    filaProducto = "";
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

                    // Columna B: Producto
                    if ("B".equalsIgnoreCase(col)) {
                        filaProducto = valorFinal;
                    }
                    // Columna C: Plaza / Medida
                    else if ("C".equalsIgnoreCase(col)) {
                        filaPlaza = valorFinal.isEmpty() ? "Única" : valorFinal;
                    }
                    // Columna D: Precio Normal
                    else if ("D".equalsIgnoreCase(col)) {
                        try {
                            filaPrecio = Double.parseDouble(valorFinal);
                        } catch (NumberFormatException e) {
                            filaPrecio = 0.0;
                        }
                    }
                    // Columna E: Descuento
                    else if ("E".equalsIgnoreCase(col)) {
                        try {
                            double d = Double.parseDouble(valorFinal);
                            filaDescuento = (d > 0.0 && d <= 1.0) ? (d * 100.0) : d;
                        } catch (NumberFormatException e) {
                            filaDescuento = 0.0;
                        }
                    }
                } else if ("row".equalsIgnoreCase(tagName)) {
                    // Si la fila tiene nombre de producto y precio válido, registrar
                    if (!filaProducto.isEmpty() && !"Producto".equalsIgnoreCase(filaProducto) && filaPrecio > 0) {
                        if (!resultado.precios.containsKey(filaProducto)) {
                            resultado.precios.put(filaProducto, new HashMap<>());
                        }
                        resultado.precios.get(filaProducto).put(filaPlaza, filaPrecio);
                        resultado.descuentos.put(filaProducto, filaDescuento);
                    }
                }
            }
            eventType = parser.next();
        }
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
