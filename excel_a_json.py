import json
import os
import openpyxl

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def _parsear_numero(valor, permitir_miles_punto=True):
    if valor is None:
        return None
    if isinstance(valor, (int, float)):
        return float(valor)
    texto = str(valor).replace("$", "").replace("%", "").replace(" ", "").strip()
    if not texto:
        return None
    if "." in texto and "," in texto:
        if texto.find(".") < texto.find(","):
            texto = texto.replace(".", "").replace(",", ".")
        else:
            texto = texto.replace(",", "")
    elif "," in texto:
        idx = texto.find(",")
        if len(texto) - idx == 4 and "," not in texto[idx + 1:]:
            texto = texto.replace(",", "")
        else:
            texto = texto.replace(",", ".")
    elif "." in texto:
        if texto.count(".") > 1:
            texto = texto.replace(".", "")
        elif permitir_miles_punto and (len(texto) - texto.find(".")) == 4:
            texto = texto.replace(".", "")
    try:
        return float(texto)
    except ValueError:
        return None


def _resolver_excel(excel_path=None):
    if excel_path and os.path.exists(excel_path):
        return excel_path
    candidatos = [
        os.path.join(BASE_DIR, "Plantilla_Catalogo_Cannon.xlsx"),
        os.path.join(BASE_DIR, "Plantilla_Catalogo_Cannon_12.xlsx"),
    ]
    for path in candidatos:
        if os.path.exists(path):
            return path
    return candidatos[0]


def _detectar_columnas(header_cells):
    """Detecta los índices de columna según el encabezado de la planilla."""
    mapa = {}
    for idx, cell in enumerate(header_cells):
        titulo = str(cell.value or "").strip().upper()
        if any(k in titulo for k in ("PRODUCTO", "ARTICULO", "ARTÍCULO", "NOMBRE")):
            mapa["producto"] = idx
        elif "CATEGOR" in titulo:
            mapa["categoria"] = idx
        elif any(k in titulo for k in ("PLAZA", "MEDIDA")):
            mapa["plaza"] = idx
        elif any(k in titulo for k in ("PRECIO", "NORMAL")):
            mapa["precio"] = idx
        elif any(k in titulo for k in ("DESCUENTO", "DCTO", "%")):
            mapa["descuento"] = idx
    return mapa


def convertir_excel_a_json(excel_path=None, json_path=None):
    """Convierte el catálogo Excel de Cannon a precios_azure.json de forma portátil."""
    ruta_excel = _resolver_excel(excel_path)
    ruta_json = json_path or os.path.join(BASE_DIR, "precios_azure.json")

    if not os.path.exists(ruta_excel):
        print(f"Error: No se encontró la planilla en {ruta_excel}")
        return False

    wb = openpyxl.load_workbook(ruta_excel, data_only=True)
    if "Resumen General" in wb.sheetnames:
        ws = wb["Resumen General"]
    elif "Catálogo Cannon" in wb.sheetnames:
        ws = wb["Catálogo Cannon"]
    else:
        ws = wb.active

    filas = list(ws.iter_rows())
    if not filas:
        print("Error: La planilla está vacía")
        return False

    cols = _detectar_columnas(filas[0])
    idx_prod = cols.get("producto", 0)
    idx_plaza = cols.get("plaza", 2)
    idx_precio = cols.get("precio", 3)
    idx_dcto = cols.get("descuento", 4)

    productos_dict = {}

    for row in filas[1:]:
        max_idx = max(idx_prod, idx_plaza, idx_precio)
        if len(row) <= max_idx or row[idx_prod].value is None:
            continue

        nombre = str(row[idx_prod].value).strip()
        if not nombre or nombre.lower() == "producto":
            continue

        plaza_val = row[idx_plaza].value if len(row) > idx_plaza else None
        plaza = str(plaza_val).strip() if plaza_val else "Única"

        celda_precio = row[idx_precio]
        precio = _parsear_numero(celda_precio.value, permitir_miles_punto=isinstance(celda_precio.value, str))
        if precio is None or precio <= 0:
            continue

        dcto = 0.0
        if len(row) > idx_dcto:
            celda_dcto = row[idx_dcto]
            raw_dcto = celda_dcto.value
            es_texto_pct = isinstance(raw_dcto, str) and "%" in raw_dcto
            fmt_pct = "%" in str(getattr(celda_dcto, "number_format", "") or "")
            dcto_val = _parsear_numero(raw_dcto, permitir_miles_punto=False)
            if dcto_val is not None:
                if fmt_pct and not es_texto_pct and not isinstance(raw_dcto, str):
                    dcto = dcto_val * 100.0
                elif 0.0 < dcto_val < 1.0 and not es_texto_pct:
                    dcto = dcto_val * 100.0
                else:
                    dcto = dcto_val
                dcto = max(0.0, min(100.0, dcto))

        if nombre not in productos_dict:
            productos_dict[nombre] = {
                "nombre": nombre,
                "descuento": round(dcto, 1),
                "plazas": {},
            }

        productos_dict[nombre]["plazas"][plaza] = precio

    lista_final = list(productos_dict.values())

    with open(ruta_json, "w", encoding="utf-8") as f:
        json.dump(lista_final, f, ensure_ascii=False, indent=2)

    print(f"✅ ¡Éxito! Se procesaron {len(lista_final)} productos desde {ruta_excel} a {ruta_json}")
    return True


if __name__ == "__main__":
    convertir_excel_a_json()
