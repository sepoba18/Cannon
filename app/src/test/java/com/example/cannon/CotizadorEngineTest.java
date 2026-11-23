package com.example.cannon;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    @Test
    public void parseoGraphqlCannon_exactoPorSku() {
        String jsonReal = "{\"data\":{\"products\":{\"items\":[{"
                + "\"name\":\"Cubrecama 2 Plazas Luisa Marengo\","
                + "\"sku\":\"CUCNL01G20A27\","
                + "\"url_key\":\"cubrecama-2-plazas-luisa-marengo\","
                + "\"price_range\":{\"minimum_price\":{"
                + "\"regular_price\":{\"value\":78990},"
                + "\"final_price\":{\"value\":55290},"
                + "\"discount\":{\"percent_off\":30}"
                + "}}}]}}}";

        CannonWebClient.WebProductInfo info = CannonWebClient.parsearRespuestaGraphql(jsonReal, "CUCNL01G20A27", "2.0");
        assertNotNull(info);
        assertTrue(info.coincidenciaExactaSku);
        assertEquals("CUCNL01G20A27", info.sku);
        assertEquals("Cubrecama 2 Plazas Luisa Marengo", info.nombreWeb);
        assertEquals("https://cannonhome.cl/cubrecama-2-plazas-luisa-marengo.html", info.urlProducto);
        assertEquals(78990.0, info.precioNormalWeb, 0.001);
        assertEquals(55290.0, info.precioFinalWeb, 0.001);
        assertEquals(30.0, info.porcentajeDescuentoWeb, 0.001);
    }

    @Test
    public void parseoGraphqlCannon_eligeMejorPlazaEnBusqueda() {
        String jsonBusqueda = "{\"data\":{\"products\":{\"items\":["
                + "{\"name\":\"Frazada Flannel 1.5 Plazas Paris Marengo\",\"sku\":\"FRAFE10G15W26\",\"url_key\":\"frazada-15\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":19990},\"final_price\":{\"value\":5990},\"discount\":{\"percent_off\":70.04}}}},"
                + "{\"name\":\"Frazada Flannel King Paris Marengo\",\"sku\":\"FRAFE10G25W26\",\"url_key\":\"frazada-king\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":24990},\"final_price\":{\"value\":7490},\"discount\":{\"percent_off\":70.03}}}}"
                + "]}}}";

        CannonWebClient.WebProductInfo info = CannonWebClient.parsearRespuestaGraphql(jsonBusqueda, "", "2.5");
        assertNotNull(info);
        assertFalse(info.coincidenciaExactaSku);
        assertEquals("FRAFE10G25W26", info.sku);
        assertEquals(7490.0, info.precioFinalWeb, 0.001);
    }

    @Test
    public void parseoGraphqlCannon_vacioRetornaNull() {
        assertNull(CannonWebClient.parsearRespuestaGraphql("{\"data\":{\"products\":{\"items\":[]}}}", "NOEXISTE", "2.0"));
        assertNull(CannonWebClient.parsearRespuestaGraphql("", "NOEXISTE", "2.0"));
    }

    @Test
    public void parseoGraphqlCannon_rechazaSkuDistinto() {
        String jsonOtroProducto = "{\"data\":{\"products\":{\"items\":[{"
                + "\"name\":\"Cubrecolchón 1.5 Plazas Impermeable\","
                + "\"sku\":\"CCIMP15A00\","
                + "\"url_key\":\"cubrecolchon-impermeable\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":39990},\"final_price\":{\"value\":19990},\"discount\":{\"percent_off\":50}}}"
                + "}]}}}";

        assertNull(CannonWebClient.parsearRespuestaGraphql(jsonOtroProducto, "PSECL02E15W26", "1.5"));
    }

    @Test
    public void parseoGraphqlCannon_rechazaNombreDeOtroProducto() {
        String jsonOtroProducto = "{\"data\":{\"products\":{\"items\":[{"
                + "\"name\":\"Cubrecolchón 1.5 Plazas Impermeable\","
                + "\"sku\":\"CCIMP15A00\","
                + "\"url_key\":\"cubrecolchon-impermeable\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":39990},\"final_price\":{\"value\":19990},\"discount\":{\"percent_off\":50}}}"
                + "}]}}}";

        assertNull(CannonWebClient.parsearRespuestaGraphqlConNombre(
                jsonOtroProducto, "", "Plumon Velvet Patag EC Stripe Beige", "1.5"));
    }

    @Test
    public void terminoBusqueda_limpiaAbreviaturasYNormalizaPlaza() {
        String termino = CannonWebClient.construirTerminoBusqueda("Cubrecama CN 100%Alg Jacquard Luisa Marengo", "2.0");
        assertTrue(termino.contains("Luisa Marengo"));
        assertTrue(termino.toLowerCase().contains("2 plazas"));
    }

    @Test
    public void repositorio_guardarProductoMultiPlazaConSkuYSobreviveSync() {
        CatalogRepository repo = new CatalogRepository();
        List<CatalogRepository.PlazaEntrada> entradas = Arrays.asList(
                new CatalogRepository.PlazaEntrada("1.5", 69990, "PLU15VELVET"),
                new CatalogRepository.PlazaEntrada("2.0", 79990, "plu20velvet"),
                new CatalogRepository.PlazaEntrada("King", 89990, "")
        );

        repo.guardarProductoCompleto(null, "Plumón Velvet Pagat", 30.0, entradas);

        assertTrue(repo.existeProducto("Plumón Velvet Pagat"));
        assertEquals(3, repo.obtenerPlazas("Plumón Velvet Pagat").size());
        assertEquals(69990.0, repo.obtenerPlazas("Plumón Velvet Pagat").get("1.5"), 0.001);
        assertEquals(79990.0, repo.obtenerPlazas("Plumón Velvet Pagat").get("2.0"), 0.001);
        assertEquals(89990.0, repo.obtenerPlazas("Plumón Velvet Pagat").get("King"), 0.001);
        assertEquals(30.0, repo.obtenerDescuento("Plumón Velvet Pagat", "2.0"), 0.001);

        ExcelCatalogParser.SkuInfo info15 = repo.buscarPorSku("PLU15VELVET");
        assertNotNull(info15);
        assertEquals("Plumón Velvet Pagat", info15.producto);
        assertEquals("1.5", info15.plaza);

        ExcelCatalogParser.SkuInfo info20 = repo.buscarPorSku("PLU20VELVET");
        assertNotNull(info20);
        assertEquals("Plumón Velvet Pagat", info20.producto);
        assertEquals("2.0", info20.plaza);
        assertEquals("", repo.obtenerSkuExacto("Plumón Velvet Pagat", "King"));

        // Simular sincronización completa desde Azure XLSX sin ese producto
        HashMap<String, HashMap<String, Double>> xlsxPrecios = new HashMap<>();
        HashMap<String, Double> plazasXlsx = new HashMap<>();
        plazasXlsx.put("2.0", 50000.0);
        xlsxPrecios.put("Producto XLSX", plazasXlsx);
        HashMap<String, Double> xlsxDctos = new HashMap<>();
        xlsxDctos.put("Producto XLSX", 20.0);
        repo.actualizarCatalogoCompleto(xlsxPrecios, xlsxDctos, new HashMap<>(), new HashMap<>());

        // El producto agregado manualmente y sus SKUs sobreviven a la sincronización
        assertTrue(repo.existeProducto("Producto XLSX"));
        assertTrue(repo.existeProducto("Plumón Velvet Pagat"));
        assertEquals(79990.0, repo.obtenerPlazas("Plumón Velvet Pagat").get("2.0"), 0.001);
        assertNotNull(repo.buscarPorSku("PLU15VELVET"));
        assertNotNull(repo.buscarPorSku("PLU20VELVET"));
    }

    @Test
    public void repositorio_editarProductoYQuitarPlazaLimpiaSkuObsoleto() {
        CatalogRepository repo = new CatalogRepository();
        repo.guardarProductoCompleto(null, "Sábana Prueba", 20.0, Arrays.asList(
                new CatalogRepository.PlazaEntrada("1.5", 35990, "SAB15"),
                new CatalogRepository.PlazaEntrada("2.0", 45990, "SAB20")
        ));

        // Editar quitando 1.5 y renombrando producto
        repo.guardarProductoCompleto("Sábana Prueba", "Sábana Editada", 25.0, Arrays.asList(
                new CatalogRepository.PlazaEntrada("2.0", 48990, "SAB20NEW")
        ));

        assertFalse(repo.existeProducto("Sábana Prueba"));
        assertTrue(repo.existeProducto("Sábana Editada"));
        assertNull(repo.buscarPorSku("SAB15"));
        assertNull(repo.buscarPorSku("SAB20"));
        ExcelCatalogParser.SkuInfo nuevo = repo.buscarPorSku("SAB20NEW");
        assertNotNull(nuevo);
        assertEquals("Sábana Editada", nuevo.producto);
        assertEquals("2.0", nuevo.plaza);
        assertEquals(48990.0, repo.obtenerPlazas("Sábana Editada").get("2.0"), 0.001);
    }

    @Test
    public void repositorio_almohadasYCojinesConMedidasAManoYFiltradoSinAcentos() {
        CatalogRepository repo = new CatalogRepository();
        repo.guardarProductoCompleto(null, "Almohada Soft", 30.0, Arrays.asList(
                new CatalogRepository.PlazaEntrada("50 x 70", 24990, "ALM5070"),
                new CatalogRepository.PlazaEntrada("50 x 90", 29990, "ALM5090")
        ));
        repo.guardarProductoCompleto(null, "Cojín Velvet Decorativo", 20.0, Arrays.asList(
                new CatalogRepository.PlazaEntrada("50 x 50", 14990, "COJ5050"),
                new CatalogRepository.PlazaEntrada("30 x 50", 12990, "COJ3050"),
                new CatalogRepository.PlazaEntrada("40 x 60", 16990, "COJ4060")
        ));

        assertEquals(2, repo.obtenerPlazas("Almohada Soft").size());
        assertEquals(3, repo.obtenerPlazas("Cojín Velvet Decorativo").size());
        assertEquals("50 x 70", repo.buscarPorSku("ALM5070").plaza);
        assertEquals("40 x 60", repo.buscarPorSku("COJ4060").plaza);

        List<String> filtradosCojin = repo.obtenerNombresPorCategoria("COJIN");
        assertTrue(filtradosCojin.contains("Cojín Velvet Decorativo"));
    }

    @Test
    public void terminoBusqueda_expandeHilosYAbreviaturasYQuitaTemporada() {
        assertEquals("Sabana 300 Hilos 2 plazas",
                CannonWebClient.construirTerminoBusqueda("SAB 300H CN", "2.0"));
        assertEquals("QUILT VELVET 2 plazas",
                CannonWebClient.construirTerminoBusqueda("QUILT VELVET STWSH", "2.0"));
        assertEquals("Room Spray 450ml Night Orchid",
                CannonWebClient.construirTerminoBusqueda("Room Spray EC 450ml Night Orchid A26", "Única"));
        assertEquals("TOPPER Pluma 1.5 plazas",
                CannonWebClient.construirTerminoBusqueda("TOPPER PLUM", "1.5"));
    }

    @Test
    public void parseoGraphqlCannon_aceptaEquivalenciaQuiltCubrecamaYPriorizaStockYPlaza() {
        String jsonBusqueda = "{\"data\":{\"products\":{\"items\":["
                + "{\"name\":\"Cubrecama 1.5 Plazas Maria Gris\",\"sku\":\"CURSL03G15A27\",\"url_key\":\"cubrecama-15\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":72990},\"final_price\":{\"value\":51090},\"discount\":{\"percent_off\":30}}}},"
                + "{\"name\":\"Cubrecama 2 Plazas Maria Taupe\",\"sku\":\"CURSL03F20A27\",\"url_key\":\"cubrecama-20-taupe\",\"stock_status\":\"OUT_OF_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":92990},\"final_price\":{\"value\":65090},\"discount\":{\"percent_off\":30}}}},"
                + "{\"name\":\"Cubrecama 2 Plazas Maria Gris\",\"sku\":\"CURSL03G20A27\",\"url_key\":\"cubrecama-20-gris\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":92990},\"final_price\":{\"value\":65090},\"discount\":{\"percent_off\":30}}}}"
                + "]}}}";

        CannonWebClient.WebProductInfo info = CannonWebClient.parsearRespuestaGraphqlConNombre(
                jsonBusqueda, "", "QUILT Maria", "2.0");
        assertNotNull(info);
        assertFalse(info.coincidenciaExactaSku);
        assertEquals("CURSL03G20A27", info.sku);
        assertEquals("Cubrecama 2 Plazas Maria Gris", info.nombreWeb);
        assertEquals(65090.0, info.precioFinalWeb, 0.001);
    }

    @Test
    public void parseoGraphqlCannon_validaFundaPlumonYRechazaPlumonDistinto() {
        String jsonBusqueda = "{\"data\":{\"products\":{\"items\":["
                + "{\"name\":\"Plumón 2 Plazas Lauren\",\"sku\":\"PLLAU20\",\"url_key\":\"plumon-lauren\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":99990},\"final_price\":{\"value\":49990},\"discount\":{\"percent_off\":50}}}},"
                + "{\"name\":\"Funda Plumón 2 Plazas Lauren Sage\",\"sku\":\"FDRSJU002P20A\",\"url_key\":\"funda-lauren-sage\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":84990},\"final_price\":{\"value\":33990},\"discount\":{\"percent_off\":60.01}}}}"
                + "]}}}";

        CannonWebClient.WebProductInfo info = CannonWebClient.parsearRespuestaGraphqlConNombre(
                jsonBusqueda, "", "Fda P. LAUREN", "2.0");
        assertNotNull(info);
        assertEquals("FDRSJU002P20A", info.sku);
        assertEquals(33990.0, info.precioFinalWeb, 0.001);

        String jsonSoloPlumon = "{\"data\":{\"products\":{\"items\":["
                + "{\"name\":\"Plumón 2 Plazas Lauren\",\"sku\":\"PLLAU20\",\"url_key\":\"plumon-lauren\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":99990},\"final_price\":{\"value\":49990},\"discount\":{\"percent_off\":50}}}}"
                + "]}}}";
        assertNull(CannonWebClient.parsearRespuestaGraphqlConNombre(
                jsonSoloPlumon, "", "Fda P. LAUREN", "2.0"));
    }

    @Test
    public void parseoGraphqlCannon_productoInexistenteRetornaNull() {
        String jsonOtrosProductos = "{\"data\":{\"products\":{\"items\":["
                + "{\"name\":\"Cubre Colchón 2 Plazas Waterproof\",\"sku\":\"CCAFBWA01P20Z\",\"url_key\":\"cubre-waterproof\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":19990},\"final_price\":{\"value\":9990},\"discount\":{\"percent_off\":50}}}},"
                + "{\"name\":\"Sábanas 2 Plazas Crochet Lila\",\"sku\":\"JSAFL25M20A26\",\"url_key\":\"sabana-crochet\",\"stock_status\":\"IN_STOCK\","
                + "\"price_range\":{\"minimum_price\":{\"regular_price\":{\"value\":28990},\"final_price\":{\"value\":14490},\"discount\":{\"percent_off\":50}}}}"
                + "]}}}";

        assertNull(CannonWebClient.parsearRespuestaGraphqlConNombre(
                jsonOtrosProductos, "", "Producto Inventado Inexistente XYZ", "2.0"));
    }
}
