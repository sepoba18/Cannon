# Estado del Proyecto - Cannon App (Memoria de Sesión y Traspaso)

**Fecha:** Sábado 03 de Octubre de 2026, 14:15 PM  
**Rama:** `main`  
**Estado General:** Prototipo plenamente funcional y autónomo (modo offline) en la tablet para la presentación con proyector. Flujo de automatización OneDrive ➔ Azure Logic Apps ➔ Blob Storage creado y en proceso de ajuste fino.

---

## 1. Tareas Completadas en la Sesión de Hoy

1. **Motor Nativo de Excel (.xlsx) en Android (Sin librerías externas pesadas):**
   - **`ExcelCatalogParser.java`**: Creado lector OpenXML nativo con `ZipFile` y `XmlPullParser` de Android para procesar hojas `.xlsx` directamente (`sharedStrings.xml` + `sheet1.xml`), mapeando columnas de producto, plaza, precio normal y descuento.
   - **`AzureSyncManager.java`**: Actualizado para detectar URLs `.xlsx`, seguir redirecciones HTTP (301, 302, 307, 308) y derivar el flujo binario directamente a `ExcelCatalogParser`.
   - Compilado y desplegado exitosamente a la tablet (`34212ab47d91`) y teléfono vía ADB.

2. **Extracción de Datos y Generación de Planilla Excel Corporativa:**
   - Extraídos 41 productos y precios actualizados directamente de la memoria del teléfono.
   - Generada planilla profesional `Plantilla_Catalogo_Cannon.xlsx` con múltiples pestañas categorizadas (Resumen General, Quilts, Sábanas, Plumones, Almohadas, Toppers, Fundas) y formato de moneda formal.

3. **Arquitectura Cloud Automática OneDrive ➔ Azure (Sin código, sin JSON):**
   - Creado recurso **Azure Logic App (`logic-cannon-sync`)** bajo el plan *Consumo* en el grupo de recursos `rg-cannon` (`East US 2`).
   - Conexión autorizada con la cuenta institucional USS (`sorellana@correo.uss.cl`).
   - Disparador: **OneDrive para la Empresa** monitoreando `/Documents`.
   - Acción 1: **Obtener contenido de archivo** apuntando a `Plantilla_Catalogo_Cannon12.xlsx`.
   - Acción 2: **Crear blob (V2)** conectado a `sebacannon2` para guardar en `/catalogo/Plantilla_Catalogo_Cannon.xlsx`.

---

## 2. Diagnóstico Técnico y Próximos Pasos para la Noche

1. **Ajuste en la Tarjeta "Crear blob (V2)" en Azure Logic Apps:**
   - En la primera ejecución, el campo *"Contenido del blob"* guardó texto concatenado (`Contenido del archivoPK...`), lo que corrompió la cabecera binaria del ZIP de Excel en Azure Blob Storage.
   - **Acción requerida:** En el diseñador de `logic-cannon-sync`, vaciar el texto del campo *"Contenido del blob"* e insertar únicamente la ficha dinámica azul `Contenido del archivo` (sin escribir letras).
   - Verificar que la frecuencia de revisión del disparador esté en `Minuto` (intervalo 1).
   - Probar modificando un precio en OneDrive y comprobar la sincronización en vivo en la tablet.

2. **Explicación Pedagógica de Java (Pendiente acordado):**
   - Explicación línea por línea de la arquitectura limpia en Java (`MainActivity.java`, `CotizadorEngine.java`, `CatalogRepository.java`, `AzureSyncManager.java`, `ExcelCatalogParser.java`).
   - Recomendaciones de estudio de Java para desarrollo empresarial.

---

## 3. Estado de la App para la Presentación de Hoy

- **La tablet está 100% lista para proyectar:**
  - El catálogo local cuenta con todos los 41 productos reales y actualizados.
  - El cotizador calcula descuentos, precios normales y precios rebajados en tiempo real de forma ultra fluida.
  - Filtro por categorías rápidas (Quilts, Sábanas, Plumones, Almohadas, Toppers) y selector de plazas (1.5, 2.0, King, etc.).
  - Funciona de forma totalmente autónoma (modo local offline), ideal para evitar imprevistos de red durante la presentación con el proyector.

---

## 4. Comandos de Referencia

- Inspeccionar blob en Azure:
  ```powershell
  curl.exe -I "https://sebacannon2.blob.core.windows.net/catalogo/Plantilla_Catalogo_Cannon.xlsx"
  ```
- Compilar e instalar APK en dispositivos:
  ```powershell
  .\gradlew.bat assembleDebug
  & "C:\Users\seban\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r -d "app\build\outputs\apk\debug\app-debug.apk"
  ```
