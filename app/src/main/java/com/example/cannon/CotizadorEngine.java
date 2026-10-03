package com.example.cannon;

import java.util.Locale;

/**
 * Motor de cálculo desacoplado de la interfaz gráfica.
 * Optimizado para ejecutar cálculos de precios y descuentos al instante en cualquier CPU.
 */
public class CotizadorEngine {

    public static class Resultado {
        private final double precioBase;
        private final double porcentajeDescuento;
        private final double precioFinal;
        private final double montoAhorro;

        public Resultado(double precioBase, double porcentajeDescuento, double precioFinal, double montoAhorro) {
            this.precioBase = precioBase;
            this.porcentajeDescuento = porcentajeDescuento;
            this.precioFinal = precioFinal;
            this.montoAhorro = montoAhorro;
        }

        public double getPrecioBase() { return precioBase; }
        public double getPorcentajeDescuento() { return porcentajeDescuento; }
        public double getPrecioFinal() { return precioFinal; }
        public double getMontoAhorro() { return montoAhorro; }
    }

    public static Resultado calcular(double precioBase, double descuento) {
        if (precioBase <= 0) {
            return new Resultado(0, 0, 0, 0);
        }

        double descNormalizado = Math.max(0.0, Math.min(100.0, descuento));
        double factor = 1.0 - (descNormalizado / 100.0);
        double pFinal = Math.round(precioBase * factor);
        double ahorro = precioBase - pFinal;

        return new Resultado(precioBase, descNormalizado, pFinal, ahorro);
    }

    public static String formatearMoneda(double monto) {
        return String.format(Locale.getDefault(), "$%,.0f", monto);
    }
}
