# 🛏️ Cannon: Catálogo Comercial & Cotizador Inteligente

<p align="center">
  <img src="https://img.shields.io/badge/Plataforma-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Lenguaje-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/UI-Material_Design_3-757575?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3" />
  <img src="https://img.shields.io/badge/Gradle-Kotlin_DSL-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
</p>

Aplicación móvil nativa para Android diseñada para el sector retail de descanso y hogar (colchones, plumones, sábanas, almohadas). Permite a vendedores y clientes consultar el catálogo completo, filtrar por categorías y calcular cotizaciones en vivo considerando plazas específicas y descuentos promocionales dinámicos.

---

## ✨ Características Principales

- ⚡ **Cotizador Interactivo en Tiempo Real:**
  - Búsqueda predictiva con `AutoCompleteTextView`.
  - Selección dinámica de plazas mediante `ChipGroup` ("1 Plaza", "1.5 Plazas", "2 Plazas", "King", "Super King").
  - Aplicación de descuentos rápidos (10%, 20%, 30% o porcentaje personalizado).
  - Cálculo instantáneo de **Precio Base**, **Descuento Aplicado (%)**, **Ahorro ($ CLP)** y **Precio Final ($ CLP)**.

- 📋 **Catálogo Visual y Filtros Avanzados:**
  - Listado fluido basado en `RecyclerView` con tarjetas individuales por producto.
  - Buscador reactivo en vivo con `TextWatcher`.
  - Filtro por categorías con chips interactivos (*Todos*, *Colchones*, *Plumones*, *Almohadas*, *Sábanas*).

- ➕ **Gestión y Registro de Nuevos Productos:**
  - Modal interactivo con `BottomSheetDialog` para dar de alta productos y fijar precios por plaza.
  - Persistencia de catálogo personalizado mediante `SharedPreferences` serializado en JSON.

---

## 🏛️ Estructura del Proyecto

```text
app/src/main/java/com/example/cannon/
├── ProductoItem.java        # Modelo de producto, manejo de plazas (HashMap) y cálculo de precios
├── CatalogAdapter.java      # Adaptador de RecyclerView para renderizado de tarjetas
└── MainActivity.java        # Controlador principal: navegación, cotizador, buscador y modales
```

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** Java 17
- **UI Toolkit:** Android Views (XML) con componentes Material 3 (`MaterialCardView`, `ChipGroup`, `ExtendedFloatingActionButton`, `BottomSheetDialog`)
- **Persistencia:** `SharedPreferences` con serialización JSON
- **Build System:** Gradle (Kotlin DSL - `build.gradle.kts`)

---

## 🚀 Instalación y Compilación

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/sepoba18/Cannon.git
   ```
2. Abrir el proyecto en **Android Studio** (Hedgehog o superior recomendado).
3. Sincronizar las dependencias con Gradle.
4. Conectar un dispositivo físico o emulador con **Android 8.0 (API 26)** o superior.
5. Compilar y ejecutar (`Run 'app'`).

---

## 👨‍💻 Autor
Desarrollado por **Sebastián Orellana** ([@sepoba18](https://github.com/sepoba18)).
