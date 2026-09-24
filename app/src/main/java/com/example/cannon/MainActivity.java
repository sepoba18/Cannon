package com.example.cannon;

import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "CannonPrefs";
    private static final String KEY_CUSTOM_CATALOG = "custom_catalog";

    // Estructura: Producto -> { Plaza -> Precio }
    private HashMap<String, HashMap<String, Double>> catalogoPrecios = new HashMap<>();
    private HashMap<String, Double> catalogoDescuentos = new HashMap<>();

    // Vistas principales
    private View scrollCotizador, layoutCatalogo;
    private TextView tvHeaderSubtitulo;
    private BottomNavigationView bottomNavigation;
    private ExtendedFloatingActionButton fabAgregarProducto;

    // Vistas Cotizador
    private AutoCompleteTextView autoProducto;
    private LinearLayout layoutSelectorPlaza;
    private ChipGroup chipGroupPlazas, chipGroupCategorias, chipGroupDescuentos;
    private TextView tvResumenProducto, tvResumenPlaza, tvPrecioBase, tvBadgeDescuento, tvAhorro, tvPrecioFinal;
    private TextInputEditText etDescuento;

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

        inicializarVistas();
        inicializarCatalogo();
        configurarNavegacion();
        configurarCotizador();
        configurarCatalogo();
        configurarFiltroCategorias();
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
        chipGroupDescuentos = findViewById(R.id.chipGroupDescuentos);
        tvResumenProducto = findViewById(R.id.tvResumenProducto);
        tvResumenPlaza = findViewById(R.id.tvResumenPlaza);
        tvPrecioBase = findViewById(R.id.tvPrecioBase);
        tvBadgeDescuento = findViewById(R.id.tvBadgeDescuento);
        tvAhorro = findViewById(R.id.tvAhorro);
        tvPrecioFinal = findViewById(R.id.tvPrecioFinal);
        etDescuento = findViewById(R.id.etDescuento);

        // Catálogo
        rvCatalogo = findViewById(R.id.rvCatalogo);
        etBuscarCatalogo = findViewById(R.id.etBuscarCatalogo);
        tvContadorProductos = findViewById(R.id.tvContadorProductos);

        fabAgregarProducto.setOnClickListener(v -> mostrarDialogoAgregarProducto(null));
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

        autoProducto.setOnItemClickListener((parent, view, position, id) -> {
            String prod = (String) parent.getItemAtPosition(position);
            seleccionarProducto(prod);
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
        List<String> productosFiltrados = new ArrayList<>();
        List<String> todos = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(todos);

        for (String p : todos) {
            if (categoriaFiltroActual.equals("Todos")) {
                productosFiltrados.add(p);
            } else {
                if (p.toUpperCase().contains(categoriaFiltroActual)) {
                    productosFiltrados.add(p);
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, productosFiltrados);
        autoProducto.setAdapter(adapter);
    }

    /**
     * Configura la interfaz cuando se selecciona un producto en el cotizador.
     */
    private void seleccionarProducto(String nombreProducto) {
        productoSeleccionado = nombreProducto;
        tvResumenProducto.setText(nombreProducto);

        HashMap<String, Double> plazas = catalogoPrecios.get(nombreProducto);
        if (plazas == null || plazas.isEmpty()) return;

        double descDefault = catalogoDescuentos.getOrDefault(nombreProducto, 0.0);
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
        double descuento = descStr.isEmpty() ? 0 : Double.parseDouble(descStr.replace(",", "."));

        double pFinal = precioSeleccionado * (1.0 - (descuento / 100.0));
        double montoAhorrado = precioSeleccionado - pFinal;

        if (descuento > 0) {
            tvPrecioBase.setText(String.format(Locale.getDefault(), "Precio Normal: $%,.0f", precioSeleccionado));
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            tvBadgeDescuento.setVisibility(View.VISIBLE);
            tvBadgeDescuento.setText(String.format(Locale.getDefault(), "-%.0f%%", descuento));

            tvAhorro.setVisibility(View.VISIBLE);
            tvAhorro.setText(String.format(Locale.getDefault(), "Ahorras: $%,.0f", montoAhorrado));
        } else {
            tvPrecioBase.setText(String.format(Locale.getDefault(), "Precio Normal: $%,.0f", precioSeleccionado));
            tvPrecioBase.setPaintFlags(tvPrecioBase.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tvBadgeDescuento.setVisibility(View.GONE);
            tvAhorro.setVisibility(View.GONE);
        }

        tvPrecioFinal.setText(String.format(Locale.getDefault(), "$%,.0f", pFinal));
    }

    private void configurarCatalogo() {
        rvCatalogo.setLayoutManager(new LinearLayoutManager(this));
        adapterCatalogo = new CatalogAdapter(obtenerListaProductos(), producto -> {
            // Acción rápida: traspasar al cotizador y seleccionarlo automáticamente
            bottomNavigation.setSelectedItemId(R.id.nav_calculator);
            autoProducto.setText(producto.getNombre(), false);
            seleccionarProducto(producto.getNombre());
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
            adapterCatalogo.actualizarDatos(obtenerListaProductos());
            String query = etBuscarCatalogo.getText() != null ? etBuscarCatalogo.getText().toString() : "";
            adapterCatalogo.filtrar(query);
            actualizarTextoContador();
        }
    }

    private void actualizarTextoContador() {
        int count = adapterCatalogo != null ? adapterCatalogo.getItemCountFiltrado() : 0;
        tvContadorProductos.setText(String.format(Locale.getDefault(), "%d productos en el catálogo", count));
    }

    private List<ProductoItem> obtenerListaProductos() {
        List<ProductoItem> lista = new ArrayList<>();
        List<String> nombres = new ArrayList<>(catalogoPrecios.keySet());
        Collections.sort(nombres);

        for (String nombre : nombres) {
            double desc = catalogoDescuentos.getOrDefault(nombre, 0.0);
            HashMap<String, Double> plazas = catalogoPrecios.get(nombre);
            lista.add(new ProductoItem(nombre, desc, plazas));
        }
        return lista;
    }

    /**
     * Muestra un diálogo estilo BottomSheet para ingresar o editar un producto con sugerencias rápidas.
     */
    private void mostrarDialogoAgregarProducto(String nombreSugerido) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        dialog.setContentView(view);

        TextInputEditText etNombre = view.findViewById(R.id.etDialogNombre);
        AutoCompleteTextView autoPlaza = view.findViewById(R.id.autoDialogPlaza);
        TextInputEditText etPrecio = view.findViewById(R.id.etDialogPrecio);
        TextInputEditText etDcto = view.findViewById(R.id.etDialogDescuento);
        MaterialButton btnGuardar = view.findViewById(R.id.btnDialogGuardar);

        if (nombreSugerido != null && !nombreSugerido.isEmpty()) {
            etNombre.setText(nombreSugerido);
        }

        String[] plazasArray = {"1.0", "1.5", "2.0", "2.5", "3.0", "King", "Super King", "Única"};
        ArrayAdapter<String> adapterPlazas = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, plazasArray);
        autoPlaza.setAdapter(adapterPlazas);

        // Click en chips de sugerencia rápida
        view.findViewById(R.id.chipPlaza10).setOnClickListener(v -> autoPlaza.setText("1.0", false));
        view.findViewById(R.id.chipPlaza15).setOnClickListener(v -> autoPlaza.setText("1.5", false));
        view.findViewById(R.id.chipPlaza20).setOnClickListener(v -> autoPlaza.setText("2.0", false));
        view.findViewById(R.id.chipPlaza25).setOnClickListener(v -> autoPlaza.setText("2.5", false));
        view.findViewById(R.id.chipPlaza30).setOnClickListener(v -> autoPlaza.setText("3.0", false));
        view.findViewById(R.id.chipPlazaUnica).setOnClickListener(v -> autoPlaza.setText("Única", false));

        btnGuardar.setOnClickListener(v -> {
            String nombre = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            String plaza = autoPlaza.getText() != null ? autoPlaza.getText().toString().trim() : "";
            String precioStr = etPrecio.getText() != null ? etPrecio.getText().toString().trim() : "";
            String dctoStr = etDcto.getText() != null ? etDcto.getText().toString().trim() : "";

            if (nombre.isEmpty() || precioStr.isEmpty() || dctoStr.isEmpty()) {
                Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            if (plaza.isEmpty()) {
                plaza = "Única";
            }

            try {
                double precio = Double.parseDouble(precioStr);
                double dcto = Double.parseDouble(dctoStr.replace(",", "."));

                if (!catalogoPrecios.containsKey(nombre)) {
                    catalogoPrecios.put(nombre, new HashMap<>());
                }
                catalogoPrecios.get(nombre).put(plaza, precio);
                catalogoDescuentos.put(nombre, dcto);

                guardarEnPreferencias();
                actualizarAdaptadorProductos();
                actualizarListaCatalogo();

                Toast.makeText(this, "✅ Guardado: " + nombre + " (" + plaza + ")", Toast.LENGTH_SHORT).show();
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

    /**
     * Guarda el catálogo modificado en SharedPreferences para que persista al cerrar la app.
     */
    private void guardarEnPreferencias() {
        try {
            JSONArray arr = new JSONArray();
            for (String prod : catalogoPrecios.keySet()) {
                JSONObject obj = new JSONObject();
                obj.put("nombre", prod);
                obj.put("descuento", catalogoDescuentos.getOrDefault(prod, 0.0));

                JSONObject plazasObj = new JSONObject();
                HashMap<String, Double> mapPlazas = catalogoPrecios.get(prod);
                if (mapPlazas != null) {
                    for (String pl : mapPlazas.keySet()) {
                        plazasObj.put(pl, mapPlazas.get(pl));
                    }
                }
                obj.put("plazas", plazasObj);
                arr.put(obj);
            }

            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            sp.edit().putString(KEY_CUSTOM_CATALOG, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void inicializarCatalogo() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String customJson = sp.getString(KEY_CUSTOM_CATALOG, null);

        if (customJson != null && !customJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(customJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String nombre = obj.getString("nombre");
                    double dcto = obj.getDouble("descuento");
                    JSONObject plazasObj = obj.getJSONObject("plazas");

                    HashMap<String, Double> map = new HashMap<>();
                    Iterator<String> keys = plazasObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        map.put(key, plazasObj.getDouble(key));
                    }
                    catalogoPrecios.put(nombre, map);
                    catalogoDescuentos.put(nombre, dcto);
                }
                return;
            } catch (Exception ignored) {}
        }

        // Si no hay datos guardados, cargar catálogo base Cannon
        String[][] datosRaw = {
            {"QUILT Maria - 1.5", "72990", "30"}, {"QUILT Maria - 2.0", "92990", "30"}, {"QUILT Maria - 2.5", "102990", "30"}, {"QUILT Maria - 3.0", "112990", "30"},
            {"SAB 1000 H RS - 2.0", "229990", "20"}, {"SAB 1000 H RS - 2.5", "249990", "20"}, {"SAB 1000 H RS - 3.0", "269990", "20"},
            {"Fda P. OXF - 1.5", "69990", "50"}, {"Fda P. OXF - 2.0", "79990", "50"}, {"Fda P. OXF - 2.5", "89990", "50"},
            {"Fda P. LAUREN - 1.5", "64990", "50"}, {"Fda P. LAUREN - 2.0", "84990", "50"}, {"Fda P. LAUREN - 2.5", "94990", "50"}, {"Fda P. LAUREN - 3.0", "104990", "50"},
            {"Fda P. 180 HILOS - 1.5", "69990", "50"}, {"Fda P. 180 HILOS - 2.0", "89990", "50"}, {"Fda P. 180 HILOS - 2.5", "99990", "50"}, {"Fda P. 180 HILOS - 3.0", "109990", "50"},
            {"Fda P. JACQUARD - 2.0", "98990", "50"}, {"Fda P. JACQUARD - 2.5", "108990", "50"}, {"Fda P. JACQUARD - 3.0", "118990", "50"},
            {"Fda P. BORDADA - 1.5", "69990", "50"}, {"Fda P. BORDADA - 2.0", "89990", "50"}, {"Fda P. BORDADA - 2.5", "99990", "50"}, {"Fda P. BORDADA - 3.0", "109990", "50"},
            {"Fda P. LINO - 1.5", "94990", "50"}, {"Fda P. LINO - 2.0", "124990", "50"}, {"Fda P. LINO - 2.5", "134990", "50"},
            {"Fda P. ALMA - 1.5", "42990", "50"}, {"Fda P. ALMA - 2.0", "49990", "50"}, {"Fda P. ALMA - 2.5", "54990", "50"}, {"Fda P. ALMA - 3.0", "59990", "50"},
            {"Fda P. Verona - 1.5", "34990", "50"}, {"Fda P. Verona - 2.0", "39990", "50"}, {"Fda P. Verona - 2.5", "42990", "50"},
            {"VIVO - 1.0", "19990", "40"}, {"VIVO - 1.5", "22990", "40"}, {"VIVO - 2.0", "26990", "40"}, {"VIVO - 2.5", "32990", "40"}, {"VIVO - 3.0", "34990", "40"},
            {"TENCEL - 1.5", "44990", "40"}, {"TENCEL - 2.0", "54990", "40"}, {"TENCEL - 2.5", "64990", "40"}, {"TENCEL - 3.0", "74990", "40"},
            {"TOPPER - 1.5", "89990", "30"}, {"TOPPER - 2.0", "99990", "30"}, {"TOPPER - 2.5", "119990", "30"}, {"TOPPER - 3.0", "129990", "30"},
            {"TOPPER PLUM - 1.5", "149990", "30"}, {"TOPPER PLUM - 2.0", "199990", "30"}, {"TOPPER PLUM - 2.5", "209990", "30"}, {"TOPPER PLUM - 3.0", "219990", "30"},
            {"ALMOHADAS Visco CRISTAL", "62990", "30"}, {"ALMOHADAS Visco ZEN", "57990", "31"}, {"ALMOHADAS Visco GREEN", "59990", "40"},
            {"ALMOHADAS Visco ERGO PILOW", "34990", "30"}, {"ALMOHADAS Visco GEL ROYAL", "79990", "40"}, {"ALMOHADAS Visco Gel Cannon", "49990", "40"},
            {"ALMOHADAS Visco Dream", "26990", "41"},
            {"COPPER - 2.0", "44990", "33"}, {"COPPER - 2.5", "54990", "30"},
            {"SAB ESTMP 200H - 1.5", "44990", "20"}, {"SAB ESTMP 200H - 2.0", "56990", "20"}, {"SAB ESTMP 200H - 2.5", "64990", "20"}, {"SAB ESTMP 200H - 3.0", "69990", "20"},
            {"SAB LISA 200H 100% - 1.0", "44990", "50"}, {"SAB LISA 200H 100% - 1.5", "46990", "50"}, {"SAB LISA 200H 100% - 2.0", "59990", "50"}, {"SAB LISA 200H 100% - 2.5", "69990", "50"}, {"SAB LISA 200H 100% - 3.0", "74990", "50"},
            {"SAB RS 200H Bordadas - 1.5", "36990", "50"}, {"SAB RS 200H Bordadas - 2.0", "46990", "50"}, {"SAB RS 200H Bordadas - 2.5", "56990", "50"},
            {"SAB RS 200H ESTAMP - 1.5", "59990", "40"}, {"SAB RS 200H ESTAMP - 2.0", "69990", "40"}, {"SAB RS 200H ESTAMP - 2.5", "79990", "40"}, {"SAB RS 200H ESTAMP - 3.0", "89990", "40"},
            {"SAB 300H RS - 1.5", "69990", "40"}, {"SAB 300H RS - 2.0", "79990", "40"}, {"SAB 300H RS - 2.5", "89990", "40"}, {"SAB 300H RS - 3.0", "99990", "40"},
            {"SAB 300H CN - 1.5", "74990", "40"}, {"SAB 300H CN - 2.0", "86990", "40"}, {"SAB 300H CN - 2.5", "96990", "40"}, {"SAB 300H CN - 3.0", "106990", "40"},
            {"SAB 500 H CN - 1.5", "109990", "20"}, {"SAB 500 H CN - 2.0", "129990", "20"}, {"SAB 500 H CN - 2.5", "149990", "20"}, {"SAB 500 H CN - 3.0", "169990", "20"},
            {"SAB 600 H RS - 2.0", "149990", "20"}, {"SAB 600 H RS - 2.5", "159990", "20"}, {"SAB 600 H RS - 3.0", "179990", "20"},
            {"SAB 800 H RS - 2.0", "199990", "20"}, {"SAB 800 H RS - 2.5", "229990", "20"}, {"SAB 800 H RS - 3.0", "249990", "20"},
            {"PLUMON ESTAMP - 1.5", "59990", "20"}, {"PLUMON ESTAMP - 2.0", "69990", "20"}, {"PLUMON ESTAMP - 2.5", "79990", "20"}, {"PLUMON ESTAMP - 3.0", "84990", "20"},
            {"PLUMON ESTAMP (2) - 1.5", "42990", "40"}, {"PLUMON ESTAMP (2) - 2.0", "52990", "40"}, {"PLUMON ESTAMP (2) - 2.5", "56990", "40"}, {"PLUMON ESTAMP (2) - 3.0", "64990", "40"},
            {"Quilt Estampado - 1.5", "39990", "20"}, {"Quilt Estampado - 2.0", "49990", "20"}, {"Quilt Estampado - 2.5", "59990", "20"}, {"Quilt Estampado - 3.0", "69990", "20"},
            {"Quilt Liso Moss - 1.5", "39990", "20"}, {"Quilt Liso Moss - 2.0", "49990", "20"}, {"Quilt Liso Moss - 2.5", "99990", "20"}, {"Quilt Liso Moss - 3.0", "109990", "20"},
            {"QUILT VELVET STWSH - 1.5", "149990", "50"}, {"QUILT VELVET STWSH - 2.0", "159990", "50"}, {"QUILT VELVET STWSH - 2.5", "169990", "50"}, {"QUILT VELVET STWSH - 3.0", "179990", "50"},
            {"QUILT MATELADO - 1.5", "39990", "40"}, {"QUILT MATELADO - 2.0", "49990", "40"}, {"QUILT MATELADO - 2.5", "59990", "40"}, {"QUILT MATELADO - 3.0", "69990", "40"},
            {"Q. EC LEA - MAISON - 1.5", "39990", "50"}, {"Q. EC LEA - MAISON - 2.0", "49990", "50"}, {"Q. EC LEA - MAISON - 2.5", "59990", "50"}, {"Q. EC LEA - MAISON - 3.0", "69990", "50"},
            {"QUILT RS GARDEN - 1.5", "89990", "50"}, {"QUILT RS GARDEN - 2.0", "109990", "50"}, {"QUILT RS GARDEN - 2.5", "119990", "50"}, {"QUILT RS GARDEN - 3.0", "129990", "50"},
            {"QUILT NUIT RS - 1.5", "79990", "50"}, {"QUILT NUIT RS - 2.0", "99990", "50"}, {"QUILT NUIT RS - 2.5", "109990", "50"}, {"QUILT NUIT RS - 3.0", "119990", "50"},
            {"Q. RS VENT WILLOW - 1.5", "96990", "50"}, {"Q. RS VENT WILLOW - 2.0", "122990", "50"}, {"Q. RS VENT WILLOW - 2.5", "132990", "50"}, {"Q. RS VENT WILLOW - 3.0", "142990", "50"}
        };

        for (String[] d : datosRaw) {
            String fullNombre = d[0];
            double precio = Double.parseDouble(d[1]);
            double desc = Double.parseDouble(d[2]);

            String nombreBase = fullNombre;
            String plaza = "Única";

            if (fullNombre.contains(" - ")) {
                String[] partes = fullNombre.split(" - ");
                nombreBase = partes[0].trim();
                plaza = partes[1].trim();
            }

            if (!catalogoPrecios.containsKey(nombreBase)) {
                catalogoPrecios.put(nombreBase, new HashMap<>());
            }
            catalogoPrecios.get(nombreBase).put(plaza, precio);
            catalogoDescuentos.put(nombreBase, desc);
        }
    }
}
