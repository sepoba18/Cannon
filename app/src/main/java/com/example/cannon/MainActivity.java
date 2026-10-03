package com.example.cannon;

import android.graphics.Paint;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Capas modulares de Datos, Red y Negocio
    private CatalogRepository repository;
    private AzureSyncManager syncManager;

    // Vistas principales
    private View scrollCotizador, layoutCatalogo;
    private TextView tvHeaderSubtitulo, tvEstadoNube;
    private BottomNavigationView bottomNavigation;
    private ExtendedFloatingActionButton fabAgregarProducto;
    private MaterialButton btnSincronizarNube;

    // Vistas Cotizador
    private AutoCompleteTextView autoProducto;
    private LinearLayout layoutSelectorPlaza;
    private ChipGroup chipGroupPlazas, chipGroupCategorias;
    private TextView tvResumenProducto, tvResumenPlaza, tvPrecioBase, tvBadgeDescuento, tvAhorro, tvPrecioFinal;
    private TextInputEditText etDescuento;
    private MaterialButton btnVerTodosLosProductos, btnAgregarProductoCotizador, btnEditarProductoCotizador;

    // Vistas Catálogo
    private RecyclerView rvCatalogo;
    private CatalogAdapter adapterCatalogo;
    private TextInputEditText etBuscarCatalogo;
    private TextView tvContadorProductos;

    // Estado actual de cotización
    private String productoSeleccionado = "";
    private String plazaSeleccionada = "";
    private double precioSeleccionado = 0.0;
    private String categoriaFiltroActual = "Todos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar módulos de arquitectura
        repository = new CatalogRepository(this);
        syncManager = new AzureSyncManager(this);

        inicializarVistas();
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
        tvPrecioBase = findViewById(R.id.tvPrecioBase);
        tvBadgeDescuento = findViewById(R.id.tvBadgeDescuento);
        tvAhorro = findViewById(R.id.tvAhorro);
        tvPrecioFinal = findViewById(R.id.tvPrecioFinal);
        etDescuento = findViewById(R.id.etDescuento);
        btnVerTodosLosProductos = findViewById(R.id.btnVerTodosLosProductos);
        btnAgregarProductoCotizador = findViewById(R.id.btnAgregarProductoCotizador);
        btnEditarProductoCotizador = findViewById(R.id.btnEditarProductoCotizador);
        btnEditarProductoCotizador.setVisibility(View.GONE);

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

        fabAgregarProducto.setOnClickListener(v -> mostrarDialogoProducto(null, null));
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
            if (productoSeleccionado != null && !productoSeleccionado.isEmpty()) {
                mostrarDialogoProducto(productoSeleccionado, plazaSeleccionada);
            }
        });

        // Escucha cambios en el campo de descuento
        etDescuento.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                calcularPrecioFinal();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Chips rápidos de descuentos
        findViewById(R.id.chipDcto20).setOnClickListener(v -> aplicarDescuentoRapido(20));
        findViewById(R.id.chipDcto30).setOnClickListener(v -> aplicarDescuentoRapido(30));
        findViewById(R.id.chipDcto40).setOnClickListener(v -> aplicarDescuentoRapido(40));
        findViewById(R.id.chipDcto50).setOnClickListener(v -> aplicarDescuentoRapido(50));
    }

    private void aplicarDescuentoRapido(double porcentaje) {
        etDescuento.setText(String.format(Locale.US, "%.0f", porcentaje));
        etDescuento.setSelection(etDescuento.getText().length());
        calcularPrecioFinal();
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
        productoSeleccionado = nombreProducto;
        tvResumenProducto.setText(nombreProducto);
        if (btnEditarProductoCotizador != null) {
            btnEditarProductoCotizador.setVisibility(View.VISIBLE);
        }

        HashMap<String, Double> plazas = repository.obtenerPlazas(nombreProducto);
        if (plazas == null || plazas.isEmpty()) return;

        double descDefault = repository.obtenerDescuento(nombreProducto);
        etDescuento.setText(String.format(Locale.US, "%.0f", descDefault));

        chipGroupPlazas.removeAllViews();

        boolean esAlmohada = nombreProducto.toUpperCase().contains("ALMOHADA") ||
                (plazas.size() == 1 && plazas.containsKey("Única"));

        if (esAlmohada) {
            layoutSelectorPlaza.setVisibility(View.GONE);
            plazaSeleccionada = plazas.keySet().iterator().next();
            precioSeleccionado = plazas.get(plazaSeleccionada);
            tvResumenPlaza.setText("Medida: " + plazaSeleccionada);
            actualizarUIPrecio();
        } else {
            layoutSelectorPlaza.setVisibility(View.VISIBLE);
            tvResumenPlaza.setText("Selecciona una plaza arriba");

            List<String> listaPlazas = new ArrayList<>(plazas.keySet());
            Collections.sort(listaPlazas);

            boolean primerChip = true;
            for (String plaza : listaPlazas) {
                Chip chip = new Chip(this);
                chip.setText(plaza);
                chip.setCheckable(true);
                chip.setClickable(true);

                if (primerChip) {
                    chip.setChecked(true);
                    plazaSeleccionada = plaza;
                    precioSeleccionado = plazas.get(plaza);
                    tvResumenPlaza.setText("Plaza: " + plaza);
                    primerChip = false;
                }

                chip.setOnClickListener(v -> {
                    plazaSeleccionada = plaza;
                    precioSeleccionado = plazas.get(plaza);
                    tvResumenPlaza.setText("Plaza: " + plaza);
                    actualizarUIPrecio();
                });

                chipGroupPlazas.addView(chip);
            }
            actualizarUIPrecio();
        }
    }

    private void actualizarUIPrecio() {
        if (precioSeleccionado <= 0) {
            tvPrecioBase.setText("Precio Normal: $0");
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tvBadgeDescuento.setVisibility(View.GONE);
            tvAhorro.setVisibility(View.GONE);
            tvPrecioFinal.setText("$0");
            return;
        }

        calcularPrecioFinal();
    }

    private void calcularPrecioFinal() {
        if (precioSeleccionado <= 0) return;

        String descStr = etDescuento.getText() != null ? etDescuento.getText().toString().trim() : "";
        double descuento = 0.0;
        try {
            if (!descStr.isEmpty()) {
                descuento = Double.parseDouble(descStr.replace(",", "."));
            }
        } catch (NumberFormatException ignored) {}

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
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        dialog.setContentView(view);

        TextView tvTitulo = view.findViewById(R.id.tvDialogTitulo);
        TextView tvSubtitulo = view.findViewById(R.id.tvDialogSubtitulo);
        TextInputEditText etNombre = view.findViewById(R.id.etDialogNombre);
        AutoCompleteTextView autoPlaza = view.findViewById(R.id.autoDialogPlaza);
        TextInputEditText etPrecio = view.findViewById(R.id.etDialogPrecio);
        TextInputEditText etDcto = view.findViewById(R.id.etDialogDescuento);
        MaterialButton btnGuardar = view.findViewById(R.id.btnDialogGuardar);
        MaterialButton btnEliminar = view.findViewById(R.id.btnDialogEliminar);

        boolean esEdicion = (productoAEditar != null && !productoAEditar.trim().isEmpty() && repository.existeProducto(productoAEditar));

        String[] plazasArray = {"1.0", "1.5", "2.0", "2.5", "3.0", "King", "Super King", "Única"};
        ArrayAdapter<String> adapterPlazas = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, plazasArray);
        autoPlaza.setAdapter(adapterPlazas);
        autoPlaza.setThreshold(0);
        autoPlaza.setOnClickListener(v -> autoPlaza.showDropDown());

        HashMap<String, Double> plazasDelProducto = esEdicion ? repository.obtenerPlazas(productoAEditar) : null;

        if (esEdicion) {
            tvTitulo.setText("Editar Producto");
            tvSubtitulo.setText("Modifica el nombre, descuento o precio por plaza");
            btnGuardar.setText("Actualizar Producto");
            etNombre.setText(productoAEditar);

            double dctoActual = repository.obtenerDescuento(productoAEditar);
            etDcto.setText(String.format(Locale.US, "%.0f", dctoActual));

            btnEliminar.setVisibility(View.VISIBLE);
            btnEliminar.setOnClickListener(v -> {
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
                                autoProducto.setText("", false);
                                chipGroupPlazas.removeAllViews();
                                layoutSelectorPlaza.setVisibility(View.GONE);
                                actualizarUIPrecio();
                            }

                            Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });

            String plazaInicial = "";
            if (plazaSugerida != null && plazasDelProducto != null && plazasDelProducto.containsKey(plazaSugerida)) {
                plazaInicial = plazaSugerida;
            } else if (plazasDelProducto != null && !plazasDelProducto.isEmpty()) {
                plazaInicial = plazasDelProducto.keySet().iterator().next();
            } else {
                plazaInicial = "1.5";
            }

            autoPlaza.setText(plazaInicial, false);
            if (plazasDelProducto != null && plazasDelProducto.containsKey(plazaInicial)) {
                double precio = plazasDelProducto.get(plazaInicial);
                etPrecio.setText(String.format(Locale.US, "%.0f", precio));
            }
        } else {
            tvTitulo.setText("Agregar Producto");
            tvSubtitulo.setText("Ingresa los datos para guardarlo en el catálogo");
            btnGuardar.setText("Guardar en Catálogo");
            btnEliminar.setVisibility(View.GONE);
            autoPlaza.setText("1.5", false);
        }

        Runnable actualizarPrecioParaPlaza = () -> {
            String pl = autoPlaza.getText() != null ? autoPlaza.getText().toString().trim() : "";
            String prodActual = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            if (repository.existeProducto(prodActual)) {
                HashMap<String, Double> mapP = repository.obtenerPlazas(prodActual);
                if (mapP != null && mapP.containsKey(pl)) {
                    etPrecio.setText(String.format(Locale.US, "%.0f", mapP.get(pl)));
                }
            } else if (esEdicion && repository.existeProducto(productoAEditar)) {
                HashMap<String, Double> mapP = repository.obtenerPlazas(productoAEditar);
                if (mapP != null && mapP.containsKey(pl)) {
                    etPrecio.setText(String.format(Locale.US, "%.0f", mapP.get(pl)));
                }
            }
        };

        autoPlaza.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                actualizarPrecioParaPlaza.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Click en chips de sugerencia rápida
        view.findViewById(R.id.chipPlaza10).setOnClickListener(v -> { autoPlaza.setText("1.0", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlaza15).setOnClickListener(v -> { autoPlaza.setText("1.5", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlaza20).setOnClickListener(v -> { autoPlaza.setText("2.0", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlaza25).setOnClickListener(v -> { autoPlaza.setText("2.5", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlaza30).setOnClickListener(v -> { autoPlaza.setText("3.0", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlazaKing).setOnClickListener(v -> { autoPlaza.setText("King", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlazaSuperKing).setOnClickListener(v -> { autoPlaza.setText("Super King", false); actualizarPrecioParaPlaza.run(); });
        view.findViewById(R.id.chipPlazaUnica).setOnClickListener(v -> { autoPlaza.setText("Única", false); actualizarPrecioParaPlaza.run(); });

        btnGuardar.setOnClickListener(v -> {
            String nombreNuevo = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            String plaza = autoPlaza.getText() != null ? autoPlaza.getText().toString().trim() : "";
            String precioStr = etPrecio.getText() != null ? etPrecio.getText().toString().trim() : "";
            String dctoStr = etDcto.getText() != null ? etDcto.getText().toString().trim() : "";

            if (nombreNuevo.isEmpty() || precioStr.isEmpty() || dctoStr.isEmpty()) {
                Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            if (plaza.isEmpty()) {
                plaza = "Única";
            }

            try {
                double precio = Double.parseDouble(precioStr);
                double dcto = Double.parseDouble(dctoStr.replace(",", "."));

                repository.guardarOEditarProducto(productoAEditar, nombreNuevo, plaza, precio, dcto);

                actualizarAdaptadorProductos();
                actualizarListaCatalogo();

                if (productoSeleccionado.equals(nombreNuevo) || (productoAEditar != null && productoAEditar.equals(productoSeleccionado))) {
                    seleccionarProducto(nombreNuevo);
                }

                Toast.makeText(this, "✅ Guardado: " + nombreNuevo + " (" + plaza + ")", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                hideKeyboard();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Valores numéricos inválidos", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
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
            public void onSuccess(HashMap<String, HashMap<String, Double>> precios, HashMap<String, Double> descuentos, String horaSync) {
                btnSincronizarNube.setEnabled(true);
                btnSincronizarNube.setText("Sincronizar");

                repository.actualizarCatalogoCompleto(precios, descuentos);
                actualizarAdaptadorProductos();
                actualizarListaCatalogo();

                if (!productoSeleccionado.isEmpty() && repository.existeProducto(productoSeleccionado)) {
                    seleccionarProducto(productoSeleccionado);
                }

                actualizarEstadoNubeVisual();
                Toast.makeText(MainActivity.this, "✅ Catálogo sincronizado desde Azure (" + precios.size() + " productos)", Toast.LENGTH_LONG).show();
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
        input.setHint("https://<tu-cuenta>.blob.core.windows.net/catalogo/precios_azure.json");
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
                .setMessage("Ingresa la URL del archivo precios_azure.json de tu contenedor en Azure Blob Storage:")
                .setView(container)
                .setPositiveButton("Guardar y Sincronizar", (d, w) -> {
                    String nuevaUrl = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!nuevaUrl.isEmpty()) {
                        syncManager.guardarUrl(nuevaUrl);
                        actualizarEstadoNubeVisual();
                        ejecutarSincronizacion(false);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
