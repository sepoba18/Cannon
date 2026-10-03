# Estado del Proyecto - Cannon App (Traspaso y Memoria de Sesión)

**Fecha:** Sábado 03 de Octubre de 2026, 01:40 AM  
**Rama:** `main`  
**Estado General:** Prototipo plenamente funcional, conectado a la nube de Microsoft Azure y corriendo en la tablet física.

---

## 1. Tareas Completadas en la Sesión Actual

1. **Infraestructura Cloud en Microsoft Azure (Estudiante USS):**
   - Grupo de recursos `rg-cannon` en `East US 2`.
   - Cuenta de almacenamiento Blob Storage `sebacannon2` creada con acceso anónimo habilitado para blobs.
   - Contenedor `catalogo` configurado con archivo `precios_azure.json` en línea y accesible públicamente.
   - URL activa del endpoint: `https://sebacannon2.blob.core.windows.net/catalogo/precios_azure.json`.

2. **Integración y Verificación en Vivo en la Tablet Física (Xiaomi HyperOS):**
   - Configuración de permisos de red en `AndroidManifest.xml` (`INTERNET`, `ACCESS_NETWORK_STATE`).
   - Botón e indicador de sincronización en la barra superior (`btnSincronizarNube`, `tvEstadoNube`).
   - Sincronización exitosa verificada con captura de pantalla: *"☁️ Azure: Sincronizado hoy 01:23"*.

3. **Arquitectura Limpia y Modularización:**
   - **`CotizadorEngine.java`**: Motor matemático desacoplado de Android para cálculo de descuentos, precios finales y redondeos en microsegundos (optimizado para procesadores lentos).
   - **`CatalogRepository.java`**: Gestor de datos y persistencia en memoria y disco (`SharedPreferences`). Búsqueda y filtrado en una sola pasada.
   - **`AzureSyncManager.java`**: Gestor asíncrono de peticiones HTTP en segundo plano con timeouts de 8s, manejo UTF-8 explícito y callbacks a la UI.
   - **`MainActivity.java`**: Refactorizada y aligerada para encargarse exclusivamente del ciclo de vida y la interacción gráfica.

4. **Mejoras Defensivas y de UX:**
   - Blindaje de `calcularPrecioFinal()` contra `NumberFormatException` al escribir o borrar texto en el campo de descuento.
   - Ocultamiento automático del teclado al seleccionar producto o presionar "Cotizar".
   - Auto-scroll superior al cotizar desde la pestaña Catálogo.
   - Sincronización silenciosa en segundo plano al abrir la aplicación si ya hay URL configurada y conexión a internet.

---

## 2. Tareas y Pasos Pendientes para la Próxima Sesión

1. **Explicación Pedagógica de Java (Compromiso del Usuario):**
   - Explicar archivo por archivo y línea por línea en Java (`MainActivity.java`, `CotizadorEngine.java`, `CatalogRepository.java`, `AzureSyncManager.java`, `CatalogAdapter.java`, `ProductoItem.java`).
   - Entregar ruta de aprendizaje recomendada de Java (orientada a lógica empresarial y Android).

2. **Estrategia Multiplataforma para iPhone (Sin necesidad de Mac):**
   - Opción recomendada inmediata: Versión Web Corporativa (PWA) alojada en Azure Storage para que cualquier iPhone la agregue a la pantalla de inicio con ícono nativo y pantalla completa.
   - Opción a mediano plazo: Flutter / Expo con builds en la nube.

3. **Presentación a Jefaturas:**
   - Demostración de cambio de precio/descuento en el portal de Azure y actualización instantánea en la tablet con el botón "Sincronizar".
   - Demostración del funcionamiento offline (modo tienda sin Wi-Fi).

---

## 3. Archivos y Módulos Creados / Modificados Recientemente

- `app/src/main/java/com/example/cannon/CotizadorEngine.java` (Nuevo: Motor de cálculo)
- `app/src/main/java/com/example/cannon/CatalogRepository.java` (Nuevo: Repositorio de datos)
- `app/src/main/java/com/example/cannon/AzureSyncManager.java` (Nuevo: Servicio de nube)
- `app/src/main/java/com/example/cannon/MainActivity.java` (Modificado: Integración modular)
- `app/src/main/AndroidManifest.xml` (Modificado: Permisos de red)
- `app/src/main/res/layout/activity_main.xml` (Modificado: Barra y botón de nube)
- `app/src/main/res/drawable/ic_cloud_sync.xml` (Nuevo: Ícono vector de sincronización)
- `precios_azure.json` (Nuevo: Catálogo JSON subido a Azure)
- `Plantilla_Catalogo_Cannon.xlsx` (Nuevo: Planilla modelo para ejecutivos)

---

## 4. Pruebas y Comandos para Retomar

- Compilar e instalar en tablet:
  ```powershell
  .\gradlew.bat assembleDebug
  & "C:\Users\seban\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
  ```
- Endpoint de Azure:
  ```powershell
  Invoke-RestMethod -Uri "https://sebacannon2.blob.core.windows.net/catalogo/precios_azure.json"
  ```
