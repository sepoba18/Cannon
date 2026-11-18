package com.example.cannon;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // Capas modulares de Datos, Red y Negocio
    private CatalogRepository repository;
    private AzureSyncManager syncManager;
    private CannonWebClient webClient;

    // Vistas principales
    private View layoutHeader, scrollCotizador, layoutCatalogo;
    private TextView tvHeaderSubtitulo, tvEstadoNube;
    private BottomNavigationView bottomNavigation;
    private ExtendedFloatingActionButton fabAgregarProducto;
    private MaterialButton btnSincronizarNube;
    private MaterialButton btnEscanearSku, btnEscanearSkuCatalogo;
    private TextInputEditText pendingDialogSkuTarget = null;

    // Lanzador del Escáner de Códigos de Barras / QR con Cámara
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String skuEscaneado = result.getContents().trim().toUpperCase(Locale.ROOT);
                    if (pendingDialogSkuTarget != null) {
                        TextInputEditText target = pendingDialogSkuTarget;
                        pendingDialogSkuTarget = null;
                        target.setText(skuEscaneado);
                        if (target.getText() != null) {
                            target.setSelection(target.getText().length());
                        }
                        Toast.makeText(this, "SKU escaneado: " + skuEscaneado, Toast.LENGTH_SHORT).show();
                    } else {
                        procesarSkuEscaneado(skuEscaneado);
                    }
                } else {
                    pendingDialogSkuTarget = null;
                }
            }
    );

    // Vistas Cotizador
    private AutoCompleteTextView autoProducto;
    private LinearLayout layoutSelectorPlaza;
    private ChipGroup chipGroupPlazas, chipGroupCategorias;
    private TextView tvResumenProducto, tvResumenPlaza, tvResumenSku, tvPrecioBase, tvBadgeDescuento, tvAhorro, tvPrecioFinal;
    private TextInputEditText etDescuento;
    private MaterialButton btnFijarDescuentoLocal;
    private MaterialButton btnVerTodosLosProductos, btnAgregarProductoCotizador, btnEditarProductoCotizador;

    // Vistas Comparativa Web (cannonhome.cl)
    private LinearLayout layoutComparativaWeb, layoutAccionesWeb;
    private TextView tvEstadoWeb, tvNombreWeb, tvCompSistemaFinal, tvCompSistemaDcto, tvCompSistemaNormal;
    private TextView tvCompWebFinal, tvCompWebDcto, tvCompWebNormal, tvCompDiferencia;
    private MaterialButton btnUsarDescuentoWeb, btnGuardarDescuentoWeb, btnVerEnWeb, btnRegistrarSkuDesdeWeb;

    // Vistas Catálogo
    private RecyclerView rvCatalogo;
    private CatalogAdapter adapterCatalogo;
    private TextInputEditText etBuscarCatalogo;
    private TextView tvContadorProductos;

    // Estado actual de cotización
    private String productoSeleccionado = "";
    private String plazaSeleccionada = "";
    private String skuActivo = "";
    private double precioSeleccionado = 0.0;
    private String categoriaFiltroActual = "Todos";
    private boolean actualizandoControlesDescuento = false;
    private boolean modoSoloWeb = false;
    private long consultaWebSeq = 0L;
    private CannonWebClient.WebProductInfo ultimaInfoWeb = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar módulos de arquitectura
        repository = new CatalogRepository(this);
        syncManager = new AzureSyncManager(this);
        webClient = new CannonWebClient();

        inicializarVistas();
        configurarInsetsSistema();
        configurarNavegacion();
        configurarCotizador();
        configurarCatalogo();
        configurarFiltroCategorias();

        // Si ya hay URL configurada, sincronización silenciosa de inicio
        if (syncManager.tieneUrlConfigurada()) {
            ejecutarSincronizacion(true);
        }
    }

    private void inicializarVistas() {
        layoutHeader = findViewById(R.id.layoutHeader);
        scrollCotizador = findViewById(R.id.scrollCotizador);
        layoutCatalogo = findViewById(R.id.layoutCatalogo);
        tvHeaderSubtitulo = findViewById(R.id.tvHeaderSubtitulo);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fabAgregarProducto = findViewById(R.id.fabAgregarProducto);

        // Cotizador
        autoProducto = findViewById(R.id.autoCompleteProducto);
        layoutSelectorPlaza = findViewById(R.id.layoutSelectorPlaza);
        chipGroupPlazas = findViewById(R.id.chipGroupPlazas);
        chipGroupCategorias = findViewById(R.id.chipGroupCategorias);
        tvResumenProducto = findViewById(R.id.tvResumenProducto);
        tvResumenPlaza = findViewById(R.id.tvResumenPlaza);
        tvResumenSku = findViewById(R.id.tvResumenSku);
        tvPrecioBase = findViewById(R.id.tvPrecioBase);
        tvBadgeDescuento = findViewById(R.id.tvBadgeDescuento);
        tvAhorro = findViewById(R.id.tvAhorro);
        tvPrecioFinal = findViewById(R.id.tvPrecioFinal);
        etDescuento = findViewById(R.id.etDescuento);
        btnFijarDescuentoLocal = findViewById(R.id.btnFijarDescuentoLocal);
        btnVerTodosLosProductos = findViewById(R.id.btnVerTodosLosProductos);
        btnAgregarProductoCotizador = findViewById(R.id.btnAgregarProductoCotizador);
        btnEditarProductoCotizador = findViewById(R.id.btnEditarProductoCotizador);
        btnEditarProductoCotizador.setVisibility(View.GONE);

        // Comparativa Web
        layoutComparativaWeb = findViewById(R.id.layoutComparativaWeb);
        layoutAccionesWeb = findViewById(R.id.layoutAccionesWeb);
        tvEstadoWeb = findViewById(R.id.tvEstadoWeb);
        tvNombreWeb = findViewById(R.id.tvNombreWeb);
        tvCompSistemaFinal = findViewById(R.id.tvCompSistemaFinal);
        tvCompSistemaDcto = findViewById(R.id.tvCompSistemaDcto);
        tvCompSistemaNormal = findViewById(R.id.tvCompSistemaNormal);
        tvCompWebFinal = findViewById(R.id.tvCompWebFinal);
        tvCompWebDcto = findViewById(R.id.tvCompWebDcto);
        tvCompWebNormal = findViewById(R.id.tvCompWebNormal);
        tvCompDiferencia = findViewById(R.id.tvCompDiferencia);
        btnUsarDescuentoWeb = findViewById(R.id.btnUsarDescuentoWeb);
        btnGuardarDescuentoWeb = findViewById(R.id.btnGuardarDescuentoWeb);
        btnVerEnWeb = findViewById(R.id.btnVerEnWeb);
        btnRegistrarSkuDesdeWeb = findViewById(R.id.btnRegistrarSkuDesdeWeb);

        // Catálogo
        rvCatalogo = findViewById(R.id.rvCatalogo);
        etBuscarCatalogo = findViewById(R.id.etBuscarCatalogo);
        tvContadorProductos = findViewById(R.id.tvContadorProductos);
        btnSincronizarNube = findViewById(R.id.btnSincronizarNube);
        tvEstadoNube = findViewById(R.id.tvEstadoNube);

        btnSincronizarNube.setOnClickListener(v -> ejecutarSincronizacion(false));
        btnSincronizarNube.setOnLongClickListener(v -> {
            mostrarDialogoConfigurarUrlNube();
            return true;
        });
        actualizarEstadoNubeVisual();

        // Botones de Escaneo de SKU con Cámara
        btnEscanearSku = findViewById(R.id.btnEscanearSku);
        if (btnEscanearSku != null) {
            btnEscanearSku.setOnClickListener(v -> iniciarEscaneoCamara());
        }

        btnEscanearSkuCatalogo = findViewById(R.id.btnEscanearSkuCatalogo);
        if (btnEscanearSkuCatalogo != null) {
            btnEscanearSkuCatalogo.setOnClickListener(v -> iniciarEscaneoCamara());
        }

        fabAgregarProducto.setOnClickListener(v -> mostrarDialogoProducto(null, null));
    }

    private void configurarInsetsSistema() {
        if (layoutHeader != null) {
            final int baseTop = layoutHeader.getPaddingTop();
            final int baseBottom = layoutHeader.getPaddingBottom();
            final int baseLeft = layoutHeader.getPaddingLeft();
            final int baseRight = layoutHeader.getPaddingRight();
            ViewCompat.setOnApplyWindowInsetsListener(layoutHeader, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(baseLeft + bars.left, Math.max(baseTop, bars.top + 14), baseRight + bars.right, baseBottom);
                return windowInsets;
            });
        }
        if (bottomNavigation != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, v.getPaddingTop(), bars.right, bars.bottom);
                return windowInsets;
            });
        }
    }

    private void configurarNavegacion() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calculator) {
                scrollCotizador.setVisibility(View.VISIBLE);
                layoutCatalogo.setVisibility(View.GONE);
                fabAgregarProducto.setVisibility(View.GONE);
                tvHeaderSubtitulo.setText("Cotizador y Calculadora de Descuentos");
                return true;
            } else if (id == R.id.nav_catalog) {
                scrollCotizador.setVisibility(View.GONE);
                layoutCatalogo.setVisibility(View.VISIBLE);
                fabAgregarProducto.setVisibility(View.VISIBLE);
                tvHeaderSubtitulo.setText("Catálogo de Productos y Plazas");
                actualizarListaCatalogo();
                return true;
            }
            return false;
        });
    }

    private void configurarCotizador() {
        actualizarAdaptadorProductos();

        autoProducto.setThreshold(0);
        autoProducto.setOnClickListener(v -> autoProducto.showDropDown());
        autoProducto.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) autoProducto.showDropDown();
        });

        autoProducto.setOnItemClickListener((parent, view, position, id) -> {
            String prod = (String) parent.getItemAtPosition(position);
            seleccionarProducto(prod);
            autoProducto.clearFocus();
            hideKeyboard();
        });

        btnVerTodosLosProductos.setOnClickListener(v -> bottomNavigation.setSelectedItemId(R.id.nav_catalog));
        btnAgregarProductoCotizador.setOnClickListener(v -> mostrarDialogoProducto(null, null));
        btnEditarProductoCotizador.setOnClickListener(v -> {
            if (modoSoloWeb) {
                abrirDialogoParaSkuEscaneado();
            } else if (productoSeleccionado != null && !productoSeleccionado.isEmpty()) {
                mostrarDialogoProducto(productoSeleccionado, plazaSeleccionada);
            }
        });
        if (btnRegistrarSkuDesdeWeb != null) {
            btnRegistrarSkuDesdeWeb.setOnClickListener(v -> abrirDialogoParaSkuEscaneado());
        }

        // Escucha cambios en el campo de descuento
        etDescuento.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (actualizandoControlesDescuento) return;
                calcularPrecioFinal();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Guardar descuento editado a mano para el producto/plaza actual
        if (btnFijarDescuentoLocal != null) {
            btnFijarDescuentoLocal.setOnClickListener(v -> guardarDescuentoActualManual(obtenerDescuentoActualInput()));
        }

        // Chips rápidos de descuentos (10, 20, 30, 40, 50%)
        int[] chipIds = {
                R.id.chipDcto10, R.id.chipDcto20, R.id.chipDcto30, R.id.chipDcto40, R.id.chipDcto50
        };
        double[] chipVals = {10, 20, 30, 40, 50};
        for (int i = 0; i < chipIds.length; i++) {
            final double val = chipVals[i];
            View chipView = findViewById(chipIds[i]);
            if (chipView != null) {
                chipView.setOnClickListener(v -> {
                    hideKeyboard();
                    if (etDescuento != null) etDescuento.clearFocus();
                    aplicarDescuentoRapido(val);
                });
            }
        }

        // Acciones de la comparativa Web
        if (btnUsarDescuentoWeb != null) {
            btnUsarDescuentoWeb.setOnClickListener(v -> {
                if (ultimaInfoWeb == null) return;
                double dctoWeb = Math.round(ultimaInfoWeb.porcentajeDescuentoWeb);
                aplicarDescuentoRapido(dctoWeb);
                Toast.makeText(this, String.format(Locale.getDefault(), "Aplicado %.0f%% de la web", dctoWeb), Toast.LENGTH_SHORT).show();
            });
        }
        if (btnGuardarDescuentoWeb != null) {
            btnGuardarDescuentoWeb.setOnClickListener(v -> {
                if (ultimaInfoWeb == null) return;
                double dctoWeb = Math.round(ultimaInfoWeb.porcentajeDescuentoWeb);
                aplicarDescuentoRapido(dctoWeb);
                guardarDescuentoActualManual(dctoWeb);
            });
        }
        if (btnVerEnWeb != null) {
            btnVerEnWeb.setOnClickListener(v -> {
                if (ultimaInfoWeb == null || ultimaInfoWeb.urlProducto.isEmpty()) return;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ultimaInfoWeb.urlProducto)));
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private double obtenerDescuentoActualInput() {
        String descStr = etDescuento.getText() != null ? etDescuento.getText().toString().trim() : "";
        if (descStr.isEmpty()) return 0.0;
        try {
            double val = Double.parseDouble(descStr.replace(",", "."));
            return Double.isFinite(val) ? Math.max(0.0, Math.min(100.0, val)) : 0.0;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static double parsearPrecioChileno(String texto) {
        if (texto == null) return Double.NaN;
        String limpio = texto.replace("$", "").replace(" ", "").trim();
        if (limpio.isEmpty()) return Double.NaN;
        if (limpio.contains(".") && limpio.contains(",")) {
            if (limpio.indexOf('.') < limpio.indexOf(',')) {
                limpio = limpio.replace(".", "").replace(",", ".");
            } else {
                limpio = limpio.replace(",", "");
            }
        } else if (limpio.contains(",")) {
            int idx = limpio.indexOf(',');
            if (limpio.length() - idx == 4 && !limpio.substring(idx + 1).contains(",")) {
                limpio = limpio.replace(",", "");
            } else {
                limpio = limpio.replace(",", ".");
            }
        } else if (limpio.contains(".")) {
            int primer = limpio.indexOf('.');
            int ultimo = limpio.lastIndexOf('.');
            if (primer != ultimo || limpio.length() - primer == 4) {
                limpio = limpio.replace(".", "");
            }
        }
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private void aplicarDescuentoRapido(double porcentaje) {
        double norm = Math.max(0.0, Math.min(100.0, porcentaje));
        actualizandoControlesDescuento = true;
        String textoFormateado = (norm == Math.floor(norm))
                ? String.format(Locale.US, "%.0f", norm)
                : String.format(Locale.US, "%.1f", norm);
        etDescuento.setText(textoFormateado);
        if (etDescuento.getText() != null) {
            etDescuento.setSelection(etDescuento.getText().length());
        }
        actualizandoControlesDescuento = false;
        calcularPrecioFinal();
    }

    private void guardarDescuentoActualManual(double porcentaje) {
        if (productoSeleccionado == null || productoSeleccionado.trim().isEmpty()) {
            Toast.makeText(this, "Selecciona o escanea un producto primero", Toast.LENGTH_SHORT).show();
            return;
        }
        String plazaFinal = (plazaSeleccionada == null || plazaSeleccionada.isEmpty()) ? "Única" : plazaSeleccionada;
        double norm = Math.max(0.0, Math.min(100.0, porcentaje));

        if (modoSoloWeb && precioSeleccionado > 0) {
            repository.guardarOEditarProducto(null, productoSeleccionado, plazaFinal, precioSeleccionado, norm);
            if (!skuActivo.isEmpty()) {
                repository.registrarSkuManual(skuActivo, productoSeleccionado, plazaFinal);
            }
            modoSoloWeb = false;
            if (btnEditarProductoCotizador != null) {
                btnEditarProductoCotizador.setText("Editar");
                btnEditarProductoCotizador.setVisibility(View.VISIBLE);
            }
            if (btnRegistrarSkuDesdeWeb != null) {
                btnRegistrarSkuDesdeWeb.setVisibility(View.GONE);
            }
            actualizarAdaptadorProductos();
        } else {
            repository.guardarDescuentoManual(productoSeleccionado, plazaFinal, norm);
        }

        actualizarListaCatalogo();
        actualizarResumenSkuYManual();
        Toast.makeText(this, String.format(Locale.getDefault(), "✅ Guardado %.0f%% para %s (%s)", norm, productoSeleccionado, plazaFinal), Toast.LENGTH_SHORT).show();
    }

    private void configurarFiltroCategorias() {
        chipGroupCategorias.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                categoriaFiltroActual = "Todos";
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipCatQuilts) categoriaFiltroActual = "QUILT";
                else if (checkedId == R.id.chipCatSabanas) categoriaFiltroActual = "SAB";
                else if (checkedId == R.id.chipCatPlumones) categoriaFiltroActual = "PLUMON";
                else if (checkedId == R.id.chipCatAlmohadas) categoriaFiltroActual = "ALMOHADA";
                else if (checkedId == R.id.chipCatToppers) categoriaFiltroActual = "TOPPER";
                else if (checkedId == R.id.chipCatCojines) categoriaFiltroActual = "COJIN";
                else categoriaFiltroActual = "Todos";
            }
            actualizarAdaptadorProductos();
        });
    }

    private void actualizarAdaptadorProductos() {
        List<String> productosFiltrados = repository.obtenerNombresPorCategoria(categoriaFiltroActual);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, productosFiltrados);
        autoProducto.setAdapter(adapter);
    }

    private void seleccionarProducto(String nombreProducto) {
        seleccionarProducto(nombreProducto, null);
    }

    private void seleccionarProducto(String nombreProducto, String plazaInicialPreferida) {
        modoSoloWeb = false;
        productoSeleccionado = nombreProducto;
        tvResumenProducto.setText(nombreProducto);
        if (btnEditarProductoCotizador != null) {
            btnEditarProductoCotizador.setText("Editar");
            btnEditarProductoCotizador.setVisibility(View.VISIBLE);
        }
        if (btnRegistrarSkuDesdeWeb != null) {
            btnRegistrarSkuDesdeWeb.setVisibility(View.GONE);
        }

        HashMap<String, Double> plazas = repository.obtenerPlazas(nombreProducto);
        if (plazas == null || plazas.isEmpty()) return;

        chipGroupPlazas.removeAllViews();

        boolean unicaMedida = plazas.size() == 1 &&
                (plazas.containsKey("Única") || nombreProducto.toUpperCase().contains("ALMOHADA"));

        if (unicaMedida) {
            layoutSelectorPlaza.setVisibility(View.GONE);
            plazaSeleccionada = plazas.keySet().iterator().next();
            Double pVal = plazas.get(plazaSeleccionada);
            precioSeleccionado = (pVal != null && Double.isFinite(pVal)) ? pVal : 0.0;
            tvResumenPlaza.setText("Medida: " + plazaSeleccionada);
            aplicarDescuentoDesdeRepositorio(nombreProducto, plazaSeleccionada);
            actualizarUIPrecio();
            consultarWebParaSeleccionActual();
        } else {
            layoutSelectorPlaza.setVisibility(View.VISIBLE);
            tvResumenPlaza.setText("Selecciona una plaza o medida arriba");

            List<String> listaPlazas = ordenarPlazasCannon(new ArrayList<>(plazas.keySet()));

            String plazaObjetivo = null;
            if (plazaInicialPreferida != null && !plazaInicialPreferida.trim().isEmpty()) {
                for (String pl : listaPlazas) {
                    if (pl.equalsIgnoreCase(plazaInicialPreferida.trim())) {
                        plazaObjetivo = pl;
                        break;
                    }
                }
            }
            if (plazaObjetivo == null && !listaPlazas.isEmpty()) {
                plazaObjetivo = listaPlazas.get(0);
            }

            for (String plaza : listaPlazas) {
                Chip chip = new Chip(this);
                chip.setText(plaza);
                chip.setCheckable(true);
                chip.setClickable(true);

                if (plaza.equals(plazaObjetivo)) {
                    chip.setChecked(true);
                    plazaSeleccionada = plaza;
                    Double pVal = plazas.get(plaza);
                    precioSeleccionado = (pVal != null && Double.isFinite(pVal)) ? pVal : 0.0;
                    tvResumenPlaza.setText("Plaza: " + plaza);
                    aplicarDescuentoDesdeRepositorio(nombreProducto, plaza);
                }

                chip.setOnClickListener(v -> {
                    plazaSeleccionada = plaza;
                    Double pVal = plazas.get(plaza);
                    precioSeleccionado = (pVal != null && Double.isFinite(pVal)) ? pVal : 0.0;
                    tvResumenPlaza.setText("Plaza: " + plaza);
                    aplicarDescuentoDesdeRepositorio(nombreProducto, plaza);
                    actualizarUIPrecio();
                    consultarWebParaSeleccionActual();
                });

                chipGroupPlazas.addView(chip);
            }
            actualizarUIPrecio();
            consultarWebParaSeleccionActual();
        }
    }

    private void aplicarDescuentoDesdeRepositorio(String nombreProducto, String plaza) {
        double desc = repository.obtenerDescuento(nombreProducto, plaza);
        skuActivo = repository.obtenerSku(nombreProducto, plaza);
        actualizarResumenSkuYManual();
        aplicarDescuentoRapido(desc);
    }

    private void actualizarResumenSkuYManual() {
        if (tvResumenSku == null) return;
        StringBuilder sb = new StringBuilder();
        if (skuActivo != null && !skuActivo.isEmpty()) {
            sb.append("SKU: ").append(skuActivo);
        }
        if (modoSoloWeb) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("Solo en Web (sin XLSX)");
        } else if (repository.tieneDescuentoManual(productoSeleccionado, plazaSeleccionada)) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("✏️ % editado manual");
        }
        if (sb.length() > 0) {
            tvResumenSku.setVisibility(View.VISIBLE);
            tvResumenSku.setText(sb.toString());
        } else {
            tvResumenSku.setVisibility(View.GONE);
        }
    }

    private void consultarWebParaSeleccionActual() {
        if (layoutComparativaWeb == null || webClient == null) return;
        if (productoSeleccionado == null || productoSeleccionado.isEmpty()) {
            layoutComparativaWeb.setVisibility(View.GONE);
            return;
        }

        final long seq = ++consultaWebSeq;
        ultimaInfoWeb = null;
        layoutComparativaWeb.setVisibility(View.VISIBLE);
        tvEstadoWeb.setText("Consultando...");
        tvNombreWeb.setVisibility(View.GONE);
        tvCompWebFinal.setText("...");
        tvCompWebDcto.setText("-");
        tvCompWebNormal.setText("Normal: -");
        tvCompDiferencia.setVisibility(View.GONE);
        layoutAccionesWeb.setVisibility(View.GONE);
        if (btnRegistrarSkuDesdeWeb != null) {
            btnRegistrarSkuDesdeWeb.setVisibility(modoSoloWeb ? View.VISIBLE : View.GONE);
        }

        final String skuConsulta = skuActivo;
        final String nombreConsulta = modoSoloWeb ? "" : productoSeleccionado;
        final String plazaConsulta = plazaSeleccionada;

        webClient.consultarProducto(skuConsulta, nombreConsulta, plazaConsulta, new CannonWebClient.WebCallback() {
            @Override
            public void onIniciando() {
                if (seq != consultaWebSeq) return;
                tvEstadoWeb.setText("Consultando...");
            }

            @Override
            public void onResultado(CannonWebClient.WebProductInfo info) {
                if (seq != consultaWebSeq) return;
                ultimaInfoWeb = info;
                tvEstadoWeb.setText(info.coincidenciaExactaSku ? "SKU Exacto" : "Similar Web");
                if (!info.nombreWeb.isEmpty()) {
                    tvNombreWeb.setVisibility(View.VISIBLE);
                    tvNombreWeb.setText(info.nombreWeb + (info.sku.isEmpty() ? "" : " (" + info.sku + ")"));
                }
                tvCompWebFinal.setText(CotizadorEngine.formatearMoneda(info.precioFinalWeb));
                tvCompWebDcto.setText(String.format(Locale.getDefault(), "-%.0f%% dcto", info.porcentajeDescuentoWeb));
                tvCompWebNormal.setText("Normal: " + CotizadorEngine.formatearMoneda(info.precioNormalWeb));
                layoutAccionesWeb.setVisibility(View.VISIBLE);

                if (modoSoloWeb && precioSeleccionado <= 0) {
                    tvResumenProducto.setText(info.nombreWeb.isEmpty() ? ("SKU " + skuConsulta) : info.nombreWeb);
                    productoSeleccionado = info.nombreWeb.isEmpty() ? ("SKU " + skuConsulta) : info.nombreWeb;
                    plazaSeleccionada = "Única";
                    tvResumenPlaza.setText("Medida: Web cannonhome.cl");
                    precioSeleccionado = info.precioNormalWeb;
                    aplicarDescuentoRapido(Math.round(info.porcentajeDescuentoWeb));
                }

                CotizadorEngine.Resultado resActual = CotizadorEngine.calcular(precioSeleccionado, obtenerDescuentoActualInput());
                actualizarDiferenciaSistemaVsWeb(resActual.getPrecioFinal());
            }

            @Override
            public void onNoEncontrado() {
                if (seq != consultaWebSeq) return;
                ultimaInfoWeb = null;
                tvEstadoWeb.setText("No publicado en web");
                tvNombreWeb.setVisibility(View.GONE);
                tvCompWebFinal.setText("-");
                tvCompWebDcto.setText("Este SKU no está en cannonhome.cl");
                tvCompWebNormal.setText("Normal: -");
                tvCompDiferencia.setVisibility(View.GONE);
                layoutAccionesWeb.setVisibility(View.GONE);
                if (modoSoloWeb) {
                    tvResumenProducto.setText("SKU " + skuConsulta + " (nuevo)");
                    tvResumenPlaza.setText("No está en catálogo ni en la web. Agrégalo abajo.");
                    Toast.makeText(MainActivity.this, "SKU '" + skuConsulta + "' no encontrado. Puedes agregarlo al catálogo.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String detalle) {
                if (seq != consultaWebSeq) return;
                ultimaInfoWeb = null;
                tvEstadoWeb.setText("Sin internet");
                tvNombreWeb.setVisibility(View.GONE);
                tvCompWebFinal.setText("-");
                tvCompWebDcto.setText("Offline");
                tvCompWebNormal.setText("Normal: -");
                tvCompDiferencia.setVisibility(View.GONE);
                layoutAccionesWeb.setVisibility(View.GONE);
            }
        });
    }

    private void actualizarDiferenciaSistemaVsWeb(double precioSistemaFinal) {
        if (tvCompDiferencia == null || ultimaInfoWeb == null || precioSistemaFinal <= 0) {
            if (tvCompDiferencia != null) tvCompDiferencia.setVisibility(View.GONE);
            return;
        }
        double diff = Math.round(precioSistemaFinal - ultimaInfoWeb.precioFinalWeb);
        tvCompDiferencia.setVisibility(View.VISIBLE);
        if (Math.abs(diff) < 10) {
            tvCompDiferencia.setText("Mismo precio final que en cannonhome.cl");
        } else if (diff > 0) {
            tvCompDiferencia.setText("En web está " + CotizadorEngine.formatearMoneda(diff) + " más barato");
        } else {
            tvCompDiferencia.setText("En sistema está " + CotizadorEngine.formatearMoneda(-diff) + " más barato");
        }
    }

    private void actualizarUIPrecio() {
        if (precioSeleccionado <= 0) {
            tvPrecioBase.setText("Precio Normal: $0");
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tvBadgeDescuento.setVisibility(View.GONE);
            tvAhorro.setVisibility(View.GONE);
            tvPrecioFinal.setText("$0");
            if (tvCompSistemaFinal != null) tvCompSistemaFinal.setText("$0");
            if (tvCompSistemaDcto != null) tvCompSistemaDcto.setText("0% dcto");
            if (tvCompSistemaNormal != null) tvCompSistemaNormal.setText("Normal: $0");
            return;
        }

        calcularPrecioFinal();
    }

    private void calcularPrecioFinal() {
        if (precioSeleccionado <= 0) return;

        double descuento = obtenerDescuentoActualInput();

        // Delegación del cálculo matemático a CotizadorEngine
        CotizadorEngine.Resultado res = CotizadorEngine.calcular(precioSeleccionado, descuento);

        if (res.getPorcentajeDescuento() > 0) {
            tvPrecioBase.setText(String.format(Locale.getDefault(), "Precio Normal: %s", CotizadorEngine.formatearMoneda(res.getPrecioBase())));
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            tvBadgeDescuento.setVisibility(View.VISIBLE);
            tvBadgeDescuento.setText(String.format(Locale.getDefault(), "-%.0f%%", res.getPorcentajeDescuento()));

            tvAhorro.setVisibility(View.VISIBLE);
            tvAhorro.setText(String.format(Locale.getDefault(), "Ahorras: %s", CotizadorEngine.formatearMoneda(res.getMontoAhorro())));
        } else {
            tvPrecioBase.setText(String.format(Locale.getDefault(), "Precio Normal: %s", CotizadorEngine.formatearMoneda(res.getPrecioBase())));
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tvBadgeDescuento.setVisibility(View.GONE);
            tvAhorro.setVisibility(View.GONE);
        }

        tvPrecioFinal.setText(CotizadorEngine.formatearMoneda(res.getPrecioFinal()));

        if (tvCompSistemaFinal != null) {
            tvCompSistemaFinal.setText(CotizadorEngine.formatearMoneda(res.getPrecioFinal()));
            tvCompSistemaDcto.setText(String.format(Locale.getDefault(), "-%.0f%% dcto", res.getPorcentajeDescuento()));
            tvCompSistemaNormal.setText("Normal: " + CotizadorEngine.formatearMoneda(res.getPrecioBase()));
        }
        actualizarDiferenciaSistemaVsWeb(res.getPrecioFinal());
    }

    private void configurarCatalogo() {
        rvCatalogo.setLayoutManager(new LinearLayoutManager(this));
        adapterCatalogo = new CatalogAdapter(repository.obtenerListaItems(), new CatalogAdapter.OnProductoClickListener() {
            @Override
            public void onCotizar(ProductoItem producto) {
                bottomNavigation.setSelectedItemId(R.id.nav_calculator);
                autoProducto.setText(producto.getNombre(), false);
                seleccionarProducto(producto.getNombre());
                if (scrollCotizador != null) {
                    scrollCotizador.post(() -> scrollCotizador.scrollTo(0, 0));
                }
                autoProducto.clearFocus();
                hideKeyboard();
            }

            @Override
            public void onEditar(ProductoItem producto) {
                mostrarDialogoProducto(producto.getNombre(), null);
            }
        });
        rvCatalogo.setAdapter(adapterCatalogo);

        etBuscarCatalogo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapterCatalogo.filtrar(s.toString());
                actualizarTextoContador();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        actualizarTextoContador();
    }

    private void actualizarListaCatalogo() {
        if (adapterCatalogo != null) {
            adapterCatalogo.actualizarDatos(repository.obtenerListaItems());
            String query = etBuscarCatalogo.getText() != null ? etBuscarCatalogo.getText().toString() : "";
            adapterCatalogo.filtrar(query);
            actualizarTextoContador();
        }
    }

    private void actualizarTextoContador() {
        if (tvContadorProductos == null) return;
        int count = adapterCatalogo != null ? adapterCatalogo.getItemCountFiltrado() : 0;
        tvContadorProductos.setText(String.format(Locale.getDefault(), "%d productos en el catálogo", count));
    }

    private void mostrarDialogoProducto(String productoAEditar, String plazaSugerida) {
        mostrarDialogoProducto(productoAEditar, plazaSugerida, "", "", 0.0);
    }

    private void mostrarDialogoProducto(
            String productoAEditar,
            String plazaSugerida,
            String skuInicial,
            String nombreInicial,
            double precioInicial) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        dialog.setContentView(view);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.setOnDismissListener(d -> pendingDialogSkuTarget = null);

        TextView tvTitulo = view.findViewById(R.id.tvDialogTitulo);
        TextView tvSubtitulo = view.findViewById(R.id.tvDialogSubtitulo);
        TextInputEditText etNombre = view.findViewById(R.id.etDialogNombre);
        TextInputEditText etDcto = view.findViewById(R.id.etDialogDescuento);
        MaterialButton btnCojines = view.findViewById(R.id.btnDialogCojines);
        LinearLayout layoutPlazasContainer = view.findViewById(R.id.layoutDialogPlazasContainer);
        MaterialButton btnGuardar = view.findViewById(R.id.btnDialogGuardar);
        MaterialButton btnEliminar = view.findViewById(R.id.btnDialogEliminar);

        LinkedHashMap<String, Chip> chipsPlazas = new LinkedHashMap<>();
        chipsPlazas.put("1.0", view.findViewById(R.id.chipPlaza10));
        chipsPlazas.put("1.5", view.findViewById(R.id.chipPlaza15));
        chipsPlazas.put("2.0", view.findViewById(R.id.chipPlaza20));
        chipsPlazas.put("2.5", view.findViewById(R.id.chipPlaza25));
        chipsPlazas.put("3.0", view.findViewById(R.id.chipPlaza30));
        chipsPlazas.put("50 x 70", view.findViewById(R.id.chipPlaza50x70));
        chipsPlazas.put("50 x 90", view.findViewById(R.id.chipPlaza50x90));
        chipsPlazas.put("Única", view.findViewById(R.id.chipPlazaUnica));

        List<View> filasPlazas = new ArrayList<>();
        boolean[] actualizandoChips = {false};

        boolean esEdicion = (productoAEditar != null && !productoAEditar.trim().isEmpty() && repository.existeProducto(productoAEditar));
        final double[] dctoInicialRef = {0.0};
        final HashMap<String, Double> dctosPreviosPorPlaza = new HashMap<>();

        if (esEdicion) {
            tvTitulo.setText("Editar Producto");
            tvSubtitulo.setText("Edita nombre, medidas, precios o escanea el SKU de cada medida");
            btnGuardar.setText("Actualizar Producto");
            etNombre.setText(productoAEditar);

            HashMap<String, Double> plazasDelProducto = repository.obtenerPlazas(productoAEditar);
            List<String> plazasOrdenadas = ordenarPlazasCannon(new ArrayList<>(plazasDelProducto.keySet()));

            String plazaRef = "1.5";
            if (plazaSugerida != null && !plazaSugerida.trim().isEmpty() && plazasDelProducto.containsKey(plazaSugerida.trim())) {
                plazaRef = plazaSugerida.trim();
            } else if (productoAEditar.equals(productoSeleccionado) && plazaSeleccionada != null && plazasDelProducto.containsKey(plazaSeleccionada)) {
                plazaRef = plazaSeleccionada;
            } else if (!plazasOrdenadas.isEmpty()) {
                plazaRef = plazasOrdenadas.get(0);
            }

            double dctoActual = repository.obtenerDescuento(productoAEditar, plazaRef);
            dctoInicialRef[0] = dctoActual;
            etDcto.setText((dctoActual == Math.floor(dctoActual))
                    ? String.format(Locale.US, "%.0f", dctoActual)
                    : String.format(Locale.US, "%.1f", dctoActual));

            btnEliminar.setVisibility(View.VISIBLE);
            btnEliminar.setOnClickListener(v -> confirmarEliminarProducto(productoAEditar, dialog));

            for (String pl : plazasOrdenadas) {
                dctosPreviosPorPlaza.put(pl, repository.obtenerDescuento(productoAEditar, pl));
                Double precioObj = plazasDelProducto.get(pl);
                double p = (precioObj != null && Double.isFinite(precioObj)) ? precioObj : 0.0;
                String skuPl = repository.obtenerSkuExacto(productoAEditar, pl);
                boolean esChipEstandar = contieneChipIgnoreCase(chipsPlazas, pl);
                agregarFilaPlazaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips,
                        pl, !esChipEstandar, p, skuPl, false);
            }
            if (plazasOrdenadas.isEmpty()) {
                agregarFilaPlazaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips,
                        "1.5", false, 0.0, "", false);
            }
        } else {
            tvTitulo.setText("Agregar Producto");
            tvSubtitulo.setText("Escribe el nombre, elige sus plazas/medidas y anota o escanea el SKU y precio de cada una");
            btnGuardar.setText("Guardar en Catálogo");
            btnEliminar.setVisibility(View.GONE);

            if (nombreInicial != null && !nombreInicial.trim().isEmpty()) {
                etNombre.setText(nombreInicial.trim());
            }
            etDcto.setText("0");

            String plazaDefault = (plazaSugerida != null && !plazaSugerida.trim().isEmpty()) ? plazaSugerida.trim() : "1.5";
            boolean esCustom = !contieneChipIgnoreCase(chipsPlazas, plazaDefault);
            agregarFilaPlazaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips,
                    plazaDefault, esCustom, precioInicial, skuInicial != null ? skuInicial : "", false);
        }

        for (Map.Entry<String, Chip> entry : chipsPlazas.entrySet()) {
            final String plazaChip = entry.getKey();
            Chip chip = entry.getValue();
            if (chip == null) continue;
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (actualizandoChips[0]) return;
                if (isChecked) {
                    agregarFilaPlazaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips,
                            plazaChip, false, 0.0, "", false);
                } else {
                    quitarFilaFijaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips, plazaChip);
                }
            });
        }

        if (btnCojines != null) {
            btnCojines.setOnClickListener(v -> {
                String skuArrastrado = "";
                if (!esEdicion && filasPlazas.size() == 1) {
                    View unicaFila = filasPlazas.get(0);
                    Object tag = unicaFila.getTag();
                    TextInputEditText etPrecioUnica = unicaFila.findViewById(R.id.etPlazaPrecioItem);
                    TextInputEditText etSkuUnica = unicaFila.findViewById(R.id.etPlazaSkuItem);
                    String precioTxt = etPrecioUnica.getText() != null ? etPrecioUnica.getText().toString().trim() : "";
                    String skuTxt = etSkuUnica.getText() != null ? etSkuUnica.getText().toString().trim() : "";
                    if (tag instanceof String && "1.5".equalsIgnoreCase((String) tag) && precioTxt.isEmpty()) {
                        skuArrastrado = skuTxt;
                        quitarFilaDialogo(unicaFila, layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips);
                    }
                }
                agregarFilaPlazaDialogo(layoutPlazasContainer, filasPlazas, chipsPlazas, actualizandoChips,
                        "", true, 0.0, skuArrastrado, true);
            });
        }

        btnGuardar.setOnClickListener(v -> {
            String nombreNuevo = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            String dctoStr = etDcto.getText() != null ? etDcto.getText().toString().trim() : "";

            if (nombreNuevo.isEmpty()) {
                etNombre.setError("Ingresa el nombre");
                etNombre.requestFocus();
                Toast.makeText(this, "Escribe el nombre del producto", Toast.LENGTH_SHORT).show();
                return;
            }
            if (filasPlazas.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos una plaza o medida", Toast.LENGTH_SHORT).show();
                return;
            }

            double dcto = 0.0;
            if (!dctoStr.isEmpty()) {
                try {
                    dcto = Double.parseDouble(dctoStr.replace(",", "."));
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Porcentaje de descuento inválido", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!Double.isFinite(dcto) || dcto < 0.0 || dcto > 100.0) {
                    Toast.makeText(this, "El descuento debe estar entre 0 y 100%", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            boolean descuentoGeneralModificado = !esEdicion || Math.abs(dcto - dctoInicialRef[0]) > 0.001;

            List<CatalogRepository.PlazaEntrada> entradas = new ArrayList<>();
            Set<String> medidasVistas = new HashSet<>();
            Set<String> skusVistos = new HashSet<>();
            for (View fila : filasPlazas) {
                String plaza = obtenerMedidaDeFila(fila);
                if (plaza.isEmpty()) {
                    TextInputEditText etMedidaCustom = fila.findViewById(R.id.etPlazaMedidaCustomItem);
                    etMedidaCustom.setError("Escribe la medida");
                    etMedidaCustom.requestFocus();
                    Toast.makeText(this, "Escribe la medida del cojín a mano (ej: 50 x 50)", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!medidasVistas.add(plaza.toLowerCase(Locale.ROOT))) {
                    Toast.makeText(this, "La medida '" + plaza + "' está repetida", Toast.LENGTH_SHORT).show();
                    return;
                }

                TextInputEditText etPrecioFila = fila.findViewById(R.id.etPlazaPrecioItem);
                TextInputEditText etSkuFila = fila.findViewById(R.id.etPlazaSkuItem);

                String precioStr = etPrecioFila.getText() != null ? etPrecioFila.getText().toString().trim() : "";
                if (precioStr.isEmpty()) {
                    etPrecioFila.setError("Falta precio");
                    etPrecioFila.requestFocus();
                    Toast.makeText(this, "Ingresa el precio para " + plaza, Toast.LENGTH_SHORT).show();
                    return;
                }

                double precio = parsearPrecioChileno(precioStr);
                if (!Double.isFinite(precio) || precio <= 0.0) {
                    etPrecioFila.setError("Precio inválido");
                    etPrecioFila.requestFocus();
                    Toast.makeText(this, "Ingresa un precio válido mayor a $0 en " + plaza, Toast.LENGTH_SHORT).show();
                    return;
                }

                String sku = etSkuFila.getText() != null ? etSkuFila.getText().toString().trim().toUpperCase(Locale.ROOT) : "";
                if (!sku.isEmpty() && !skusVistos.add(sku)) {
                    etSkuFila.setError("SKU repetido");
                    etSkuFila.requestFocus();
                    Toast.makeText(this, "El SKU " + sku + " está repetido en otra medida", Toast.LENGTH_SHORT).show();
                    return;
                }

                Double dctoPlazaPrevio = null;
                if (!descuentoGeneralModificado) {
                    for (Map.Entry<String, Double> entryD : dctosPreviosPorPlaza.entrySet()) {
                        if (entryD.getKey().equalsIgnoreCase(plaza)) {
                            dctoPlazaPrevio = entryD.getValue();
                            break;
                        }
                    }
                }
                entradas.add(new CatalogRepository.PlazaEntrada(plaza, precio, sku, dctoPlazaPrevio));
            }

            repository.guardarProductoCompleto(esEdicion ? productoAEditar : null, nombreNuevo, dcto, entradas);
            actualizarAdaptadorProductos();
            actualizarListaCatalogo();

            modoSoloWeb = false;
            autoProducto.setText(nombreNuevo, false);
            seleccionarProducto(nombreNuevo);

            Toast.makeText(this, "✅ Guardado: " + nombreNuevo + " (" + entradas.size() + " medidas)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            hideKeyboard();
        });

        dialog.show();
    }

    private void confirmarEliminarProducto(String productoAEditar, BottomSheetDialog dialog) {
        new AlertDialog.Builder(this)
                .setTitle("¿Eliminar producto?")
                .setMessage("¿Deseas eliminar \"" + productoAEditar + "\" del catálogo?")
                .setPositiveButton("Eliminar", (dInterface, which) -> {
                    repository.eliminarProducto(productoAEditar);
                    actualizarAdaptadorProductos();
                    actualizarListaCatalogo();

                    if (productoSeleccionado.equals(productoAEditar)) {
                        productoSeleccionado = "";
                        plazaSeleccionada = "";
                        precioSeleccionado = 0.0;
                        tvResumenProducto.setText("Selecciona un producto");
                        tvResumenPlaza.setText("Plaza: -");
                        if (btnEditarProductoCotizador != null) {
                            btnEditarProductoCotizador.setVisibility(View.GONE);
                        }
                        if (btnRegistrarSkuDesdeWeb != null) {
                            btnRegistrarSkuDesdeWeb.setVisibility(View.GONE);
                        }
                        autoProducto.setText("", false);
                        chipGroupPlazas.removeAllViews();
                        layoutSelectorPlaza.setVisibility(View.GONE);
                        if (tvResumenSku != null) tvResumenSku.setVisibility(View.GONE);
                        if (layoutComparativaWeb != null) layoutComparativaWeb.setVisibility(View.GONE);
                        actualizarUIPrecio();
                    }

                    Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void agregarFilaPlazaDialogo(
            LinearLayout container,
            List<View> filasPlazas,
            LinkedHashMap<String, Chip> chipsPlazas,
            boolean[] actualizandoChips,
            String plaza,
            boolean esMedidaAMano,
            double precioInicial,
            String skuInicial,
            boolean enfocarMedida) {
        String plazaLimpia = plaza != null ? plaza.trim() : "";
        if (!esMedidaAMano && plazaLimpia.isEmpty()) return;

        if (!esMedidaAMano) {
            for (View existente : filasPlazas) {
                Object tag = existente.getTag();
                if (tag instanceof String && ((String) tag).equalsIgnoreCase(plazaLimpia)) {
                    if (skuInicial != null && !skuInicial.isEmpty()) {
                        TextInputEditText etSku = existente.findViewById(R.id.etPlazaSkuItem);
                        if (etSku != null) etSku.setText(skuInicial.trim().toUpperCase(Locale.ROOT));
                    }
                    sincronizarChipPlazaDialogo(chipsPlazas, actualizandoChips, (String) tag, true);
                    return;
                }
            }
        }

        View fila = LayoutInflater.from(this).inflate(R.layout.item_dialog_plaza, container, false);
        TextView tvPlaza = fila.findViewById(R.id.tvPlazaNombreItem);
        View tilMedidaCustom = fila.findViewById(R.id.tilPlazaMedidaCustomItem);
        TextInputEditText etMedidaCustom = fila.findViewById(R.id.etPlazaMedidaCustomItem);
        TextInputEditText etPrecio = fila.findViewById(R.id.etPlazaPrecioItem);
        TextInputEditText etSku = fila.findViewById(R.id.etPlazaSkuItem);
        MaterialButton btnEscanear = fila.findViewById(R.id.btnPlazaEscanearItem);
        MaterialButton btnQuitar = fila.findViewById(R.id.btnPlazaQuitarItem);

        if (esMedidaAMano) {
            fila.setTag(null);
            tvPlaza.setText("Cojín / Medida a mano");
            tilMedidaCustom.setVisibility(View.VISIBLE);
            if (!plazaLimpia.isEmpty()) {
                etMedidaCustom.setText(plazaLimpia);
            }
            if (enfocarMedida) {
                etMedidaCustom.requestFocus();
            }
        } else {
            fila.setTag(plazaLimpia);
            tvPlaza.setText("Medida / Plaza: " + plazaLimpia);
            tilMedidaCustom.setVisibility(View.GONE);
            sincronizarChipPlazaDialogo(chipsPlazas, actualizandoChips, plazaLimpia, true);
        }

        if (precioInicial > 0) {
            etPrecio.setText(String.format(Locale.US, "%.0f", precioInicial));
        }
        if (skuInicial != null && !skuInicial.isEmpty()) {
            etSku.setText(skuInicial.trim().toUpperCase(Locale.ROOT));
        }

        btnEscanear.setOnClickListener(v -> {
            pendingDialogSkuTarget = etSku;
            iniciarEscaneoCamara();
        });

        btnQuitar.setOnClickListener(v ->
                quitarFilaDialogo(fila, container, filasPlazas, chipsPlazas, actualizandoChips));

        filasPlazas.add(fila);
        container.addView(fila);
    }

    private void quitarFilaFijaDialogo(
            LinearLayout container,
            List<View> filasPlazas,
            LinkedHashMap<String, Chip> chipsPlazas,
            boolean[] actualizandoChips,
            String plaza) {
        for (int i = 0; i < filasPlazas.size(); i++) {
            View fila = filasPlazas.get(i);
            Object tag = fila.getTag();
            if (tag instanceof String && ((String) tag).equalsIgnoreCase(plaza)) {
                quitarFilaDialogo(fila, container, filasPlazas, chipsPlazas, actualizandoChips);
                return;
            }
        }
    }

    private void quitarFilaDialogo(
            View fila,
            LinearLayout container,
            List<View> filasPlazas,
            LinkedHashMap<String, Chip> chipsPlazas,
            boolean[] actualizandoChips) {
        filasPlazas.remove(fila);
        container.removeView(fila);
        Object tag = fila.getTag();
        if (tag instanceof String) {
            sincronizarChipPlazaDialogo(chipsPlazas, actualizandoChips, (String) tag, false);
        }
    }

    private static String obtenerMedidaDeFila(View fila) {
        Object tag = fila.getTag();
        if (tag instanceof String) {
            return ((String) tag).trim();
        }
        TextInputEditText etMedidaCustom = fila.findViewById(R.id.etPlazaMedidaCustomItem);
        return (etMedidaCustom != null && etMedidaCustom.getText() != null)
                ? etMedidaCustom.getText().toString().trim()
                : "";
    }

    private static boolean contieneChipIgnoreCase(LinkedHashMap<String, Chip> chipsPlazas, String plaza) {
        if (plaza == null) return false;
        for (String key : chipsPlazas.keySet()) {
            if (key.equalsIgnoreCase(plaza.trim())) return true;
        }
        return false;
    }

    private static void sincronizarChipPlazaDialogo(
            LinkedHashMap<String, Chip> chipsPlazas,
            boolean[] actualizandoChips,
            String plaza,
            boolean checked) {
        actualizandoChips[0] = true;
        for (Map.Entry<String, Chip> entry : chipsPlazas.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(plaza) && entry.getValue() != null) {
                entry.getValue().setChecked(checked);
            }
        }
        actualizandoChips[0] = false;
    }

    private static List<String> ordenarPlazasCannon(List<String> plazas) {
        List<String> ordenBase = List.of("1.0", "1.5", "2.0", "2.5", "3.0", "50 x 70", "50 x 90", "Única");
        plazas.sort((a, b) -> {
            int ia = ordenBase.indexOf(a);
            int ib = ordenBase.indexOf(b);
            if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
            if (ia >= 0) return -1;
            if (ib >= 0) return 1;
            return a.compareToIgnoreCase(b);
        });
        return plazas;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void actualizarEstadoNubeVisual() {
        if (tvEstadoNube == null) return;
        String ultimaSync = syncManager.getUltimaSincronizacion();

        if (syncManager.tieneUrlConfigurada()) {
            tvEstadoNube.setVisibility(View.VISIBLE);
            if (!ultimaSync.isEmpty()) {
                tvEstadoNube.setText("☁️ Azure: " + ultimaSync);
            } else {
                tvEstadoNube.setText("☁️ Nube Azure conectada");
            }
        } else {
            tvEstadoNube.setVisibility(View.GONE);
        }
    }

    private void ejecutarSincronizacion(boolean silencioso) {
        if (!syncManager.tieneUrlConfigurada()) {
            if (!silencioso) {
                mostrarDialogoConfigurarUrlNube();
            }
            return;
        }

        syncManager.sincronizar(new AzureSyncManager.SyncCallback() {
            @Override
            public void onIniciando() {
                btnSincronizarNube.setEnabled(false);
                btnSincronizarNube.setText("Conectando...");
                if (tvEstadoNube != null) {
                    tvEstadoNube.setVisibility(View.VISIBLE);
                    tvEstadoNube.setText("⏳ Conectando con Azure...");
                }
            }

            @Override
            public void onSuccess(
                    HashMap<String, HashMap<String, Double>> precios,
                    HashMap<String, Double> descuentos,
                    HashMap<String, HashMap<String, Double>> descuentosPorPlaza,
                    HashMap<String, ExcelCatalogParser.SkuInfo> skuMap,
                    String horaSync) {
                btnSincronizarNube.setEnabled(true);
                btnSincronizarNube.setText("Sincronizar");

                repository.actualizarCatalogoCompleto(precios, descuentos, descuentosPorPlaza, skuMap);
                actualizarAdaptadorProductos();
                actualizarListaCatalogo();

                if (!productoSeleccionado.isEmpty() && repository.existeProducto(productoSeleccionado)) {
                    seleccionarProducto(productoSeleccionado);
                }

                actualizarEstadoNubeVisual();
                int totalSkus = (skuMap != null) ? skuMap.size() : repository.totalSkus();
                Toast.makeText(MainActivity.this, "✅ Catálogo sincronizado desde Azure (" + precios.size() + " productos, " + totalSkus + " SKUs)", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String mensajeError, boolean esErrorConexion) {
                btnSincronizarNube.setEnabled(true);
                btnSincronizarNube.setText("Sincronizar");

                if (esErrorConexion) {
                    if (tvEstadoNube != null) {
                        tvEstadoNube.setText("☁️ Catálogo local (Sin conexión)");
                    }
                    if (!silencioso) {
                        Toast.makeText(MainActivity.this, "⚠️ Sin conexión a Azure. Usando catálogo local.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    if (tvEstadoNube != null) {
                        tvEstadoNube.setText("⚠️ " + mensajeError);
                    }
                    Toast.makeText(MainActivity.this, mensajeError, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void mostrarDialogoConfigurarUrlNube() {
        String urlActual = syncManager.getUrlConfigurada();

        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("https://<tu-cuenta>.blob.core.windows.net/catalogo/Precios_Cyber_Day.xlsx");
        input.setText(urlActual);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = (int) (20 * getResources().getDisplayMetrics().density);
        params.rightMargin = (int) (20 * getResources().getDisplayMetrics().density);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("☁️ Conexión Microsoft Azure")
                .setMessage("Ingresa la URL del archivo Excel (.xlsx) oficial en Azure Blob Storage:")
                .setView(container)
                .setPositiveButton("Guardar y Sincronizar", (d, w) -> {
                    String nuevaUrl = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!nuevaUrl.isEmpty()) {
                        if (!nuevaUrl.toLowerCase().startsWith("https://")) {
                            Toast.makeText(this, "Seguridad: La URL debe comenzar con https://", Toast.LENGTH_LONG).show();
                            return;
                        }
                        syncManager.guardarUrl(nuevaUrl);
                        actualizarEstadoNubeVisual();
                        ejecutarSincronizacion(false);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciarEscaneoCamara() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Apunta la cámara al código de barras o SKU del producto");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(PortraitCaptureActivity.class);
        options.setBarcodeImageEnabled(false);
        barcodeLauncher.launch(options);
    }

    private void procesarSkuEscaneado(String sku) {
        if (sku == null || sku.trim().isEmpty()) return;
        String skuLimpio = sku.trim().toUpperCase();
        ExcelCatalogParser.SkuInfo info = repository.buscarPorSku(skuLimpio);

        bottomNavigation.setSelectedItemId(R.id.nav_calculator);
        scrollCotizador.setVisibility(View.VISIBLE);
        layoutCatalogo.setVisibility(View.GONE);
        fabAgregarProducto.setVisibility(View.GONE);
        tvHeaderSubtitulo.setText("Cotizador y Calculadora de Descuentos");
        if (scrollCotizador != null) {
            scrollCotizador.post(() -> scrollCotizador.scrollTo(0, 0));
        }

        if (info != null) {
            autoProducto.setText(info.producto, false);
            seleccionarProducto(info.producto, info.plaza);

            Toast.makeText(this, "🎯 SKU: " + skuLimpio + " • " + info.plaza, Toast.LENGTH_SHORT).show();
        } else {
            // Si no está en el XLSX local, consultamos directo en cannonhome.cl y permitimos agregarlo
            modoSoloWeb = true;
            productoSeleccionado = "SKU " + skuLimpio;
            plazaSeleccionada = "Única";
            skuActivo = skuLimpio;
            precioSeleccionado = 0.0;
            autoProducto.setText("", false);
            chipGroupPlazas.removeAllViews();
            layoutSelectorPlaza.setVisibility(View.GONE);
            tvResumenProducto.setText("Buscando SKU " + skuLimpio + "...");
            tvResumenPlaza.setText("Consulta directa a cannonhome.cl");
            if (btnEditarProductoCotizador != null) {
                btnEditarProductoCotizador.setText("Agregar");
                btnEditarProductoCotizador.setVisibility(View.VISIBLE);
            }
            actualizarResumenSkuYManual();
            actualizarUIPrecio();
            consultarWebParaSeleccionActual();

            Toast.makeText(this, "🔎 SKU no está en catálogo, buscando en cannonhome.cl...", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirDialogoParaSkuEscaneado() {
        String nombreSugerido = "";
        double precioSugerido = 0.0;
        if (ultimaInfoWeb != null && !ultimaInfoWeb.nombreWeb.isEmpty()) {
            nombreSugerido = ultimaInfoWeb.nombreWeb;
            precioSugerido = ultimaInfoWeb.precioNormalWeb;
        }
        mostrarDialogoProducto(null, null, skuActivo, nombreSugerido, precioSugerido);
    }

    private void seleccionarPlazaEspecifica(String plazaBuscada) {
        if (plazaBuscada == null || plazaBuscada.isEmpty()) return;
        for (int i = 0; i < chipGroupPlazas.getChildCount(); i++) {
            View v = chipGroupPlazas.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                if (chip.getText().toString().equalsIgnoreCase(plazaBuscada)) {
                    chip.setChecked(true);
                    chip.performClick();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncManager != null) {
            syncManager.shutdown();
        }
        if (webClient != null) {
            webClient.shutdown();
        }
    }
}
