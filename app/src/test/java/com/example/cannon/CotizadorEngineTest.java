package com.example.cannon;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pruebas unitarias para CotizadorEngine.
 */
public class CotizadorEngineTest {

    @Test
    public void calculoCotizadorEngine_esCorrecto() {
        CotizadorEngine.Resultado res = CotizadorEngine.calcular(100000.0, 30.0);
        assertEquals(70000.0, res.getPrecioFinal(), 0.001);
        assertEquals(30000.0, res.getMontoAhorro(), 0.001);
    }

    @Test
    public void calculoCotizadorEngine_sinDescuento() {
        CotizadorEngine.Resultado res = CotizadorEngine.calcular(50000.0, 0.0);
        assertEquals(50000.0, res.getPrecioFinal(), 0.001);
        assertEquals(0.0, res.getMontoAhorro(), 0.001);
    }

    @Test
    public void calculoCotizadorEngine_descuentoTotal() {
        CotizadorEngine.Resultado res = CotizadorEngine.calcular(80000.0, 100.0);
        assertEquals(0.0, res.getPrecioFinal(), 0.001);
        assertEquals(80000.0, res.getMontoAhorro(), 0.001);
    }
}
