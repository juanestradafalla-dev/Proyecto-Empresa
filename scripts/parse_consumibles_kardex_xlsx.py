#!/usr/bin/env python3
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "pkgrel": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def sin_acentos(value):
    text = str(value or "")
    return "".join(
        ch for ch in unicodedata.normalize("NFD", text)
        if unicodedata.category(ch) != "Mn"
    )


def normalize_text(value):
    return re.sub(r"\s+", " ", sin_acentos(value).upper()).strip()


def clean_text(value):
    return (
        normalize_text(value)
        .replace("COGINETE", "COJINETE")
        .replace("EXPANCIVO", "EXPANSIVO")
        .replace("VAREILLA", "VARILLA")
        .replace("COMBUSTIBE", "COMBUSTIBLE")
        .replace("EMPACAQUE", "EMPAQUE")
        .replace("GALVANIZASA", "GALVANIZADA")
        .replace("GALBANIZADO", "GALVANIZADO")
        .replace("HEMPBRA", "HEMBRA")
        .replace("CORRTINA", "CORTINA")
        .replace("HIDRANDE", "HIDRANTE")
    )


def clean_code(value):
    return re.sub(r"\s+", "", str(value or "").upper().strip())


def parse_quantity(value):
    text = str(value or "").replace(",", ".").strip()
    if not text:
        return 0.0, False
    try:
        number = float(text)
        return number, True
    except ValueError:
        return 0.0, False


def code_location(code):
    match = re.match(r"^([A-Z])-P(\d+)-", code)
    if not match:
        return ""
    return f"Estanteria {match.group(1)} - Piso {int(match.group(2))}"


def infer_category(item, reference, code):
    text = normalize_text(f"{item} {reference}")
    if re.search(r"AMARRES|CABLE|ELECTRIC|BOMBILLO|FUSIBLE", text):
        return "Electricidad"
    if (
        code.startswith("D-")
        or re.search(r"PVC|SANITARIO|RIEGO|GALVANIZ|ALUMINIO|LATON|MANGUERA|COLLARIN|ACOPLE|NIPLE|TAPON|BUJE|CODO|UNION|TEE|LLAVE|CHEQUE|PERA|TUBERIA", text)
    ):
        return "Plomeria y Riego"
    if re.search(r"FILTRO|BOMBA|COLADERA|SEPARATOR|FILTER", text):
        return "Filtros y Bombas"
    if re.search(r"SOBRE|PAPEL|ROLLO|EMPAQUE|VACIO", text):
        return "Oficina y Empaque"
    if re.search(r"NEUMATICO|RODAMIENTO|COJINETE|CHUMACERA|CRUCETA|RETENEDOR|BUJIA|EMBRAGUE|BANDA|PASTILLA|ARRASTRE|SELLO|PISTON|CILINDRO|SILENCIADOR|CARBURADOR|ARRANCADOR|ARRANQUE", text):
        return "Mecanica y Rodamientos"
    return "Ferreteria y Tornilleria"


def infer_subcategory(item, reference):
    text = normalize_text(f"{item} {reference}")
    if re.search(r"NEUMATICO|PARCHE|TUBELESS", text):
        return "Neumaticos y parches"
    if re.search(r"RODAMIENTO|COJINETE|CHUMACERA|CRUCETA", text):
        return "Rodamientos y chumaceras"
    if re.search(r"FILTRO|SEPARATOR|FILTER", text):
        return "Filtros"
    if re.search(r"BOMBA|COLADERA|PERA", text):
        return "Bombas y accesorios"
    if re.search(r"RETENEDOR|SELLO", text):
        return "Sellos y retenedores"
    if re.search(r"TORNILLO|PERNO|TUERCA|ARANDELA|CHAZO|ESPARRAGO|MARIPOSA|PASADOR|CONECTOR|REMACHE|PUNTILLA|GRAPA|CLAVO", text):
        return "Tornilleria y fijacion"
    if re.search(r"PVC|SANITARIO|RIEGO|GALVANIZ|LATON|ALUMINIO|MANGUERA|COLLARIN|ACOPLE|NIPLE|TAPON|BUJE|CODO|UNION|TEE|LLAVE|CHEQUE", text):
        return "Plomeria y riego"
    if re.search(r"SOBRE|PAPEL|ROLLO|EMPAQUE", text):
        return "Empaque"
    return "General"


def col_to_index(cell_ref):
    match = re.match(r"([A-Z]+)", cell_ref)
    value = 0
    for ch in match.group(1):
        value = value * 26 + ord(ch) - 64
    return value


def load_shared_strings(zip_file):
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    strings = []
    for si in root.findall("main:si", NS):
        strings.append("".join(t.text or "" for t in si.findall(".//main:t", NS)))
    return strings


def workbook_paths(zip_file):
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rels = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))
    rel_map = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels.findall("pkgrel:Relationship", NS)}
    paths = {}
    for sheet in workbook.findall("main:sheets/main:sheet", NS):
        rel_id = sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]
        target = rel_map[rel_id]
        paths[sheet.attrib["name"]] = target if target.startswith("xl/") else f"xl/{target}"
    return paths


def cell_value(cell, shared_strings):
    cell_type = cell.attrib.get("t")
    value = cell.find("main:v", NS)
    if cell_type == "s":
        return shared_strings[int(value.text)] if value is not None and value.text is not None else ""
    if cell_type == "inlineStr":
        return "".join(t.text or "" for t in cell.findall(".//main:t", NS))
    if value is None or value.text is None:
        return ""
    raw = value.text
    try:
        number = float(raw)
        return int(number) if number.is_integer() else number
    except ValueError:
        return raw


def read_rows(zip_file, sheet_path, shared_strings):
    root = ET.fromstring(zip_file.read(sheet_path))
    for row in root.findall("main:sheetData/main:row", NS):
        row_number = int(row.attrib.get("r", "0"))
        values = {}
        for cell in row.findall("main:c", NS):
            values[col_to_index(cell.attrib["r"])] = cell_value(cell, shared_strings)
        yield row_number, values


def make_match_key(item, reference, unit=""):
    return (clean_text(item), clean_text(reference), clean_text(unit))


def choose_raw_match(raw_matches, item, reference, unit, quantity):
    keys = [
        make_match_key(item, reference, unit),
        make_match_key(item, reference, ""),
    ]
    for key in keys:
        candidates = raw_matches.get(key, [])
        if not candidates:
            continue
        selected_index = 0
        for index, candidate in enumerate(candidates):
            stock = candidate.get("stock")
            total = candidate.get("total")
            if stock == quantity or total == quantity:
                selected_index = index
                break
        return candidates.pop(selected_index)
    return {}


def product_from_row(row_number, raw_code, raw_item, raw_ref, raw_unit, raw_stock, generated_tornilleria, raw_match):
    item = clean_text(raw_item)
    reference = clean_text(raw_ref) or "N/A"
    unit = clean_text(raw_unit) or "UNIDAD"
    quantity, quantity_ok = parse_quantity(raw_stock)
    brand = clean_text(raw_match.get("marca", ""))
    source_code = clean_code(raw_code)

    generated = False
    if re.match(r"^[A-Z]-P\d+-\d+$", source_code):
        code = source_code
        original_code = source_code
        location = code_location(code)
    elif source_code == "TORNILLERIA":
        generated = True
        code = f"TOR-{generated_tornilleria:03d}"
        original_code = source_code
        location = "Tornilleria"
    else:
        generated = True
        code = f"IMP-CONS-{row_number:03d}"
        original_code = source_code or code
        location = ""

    category = "Ferreteria y Tornilleria" if source_code == "TORNILLERIA" else infer_category(item, reference, code)
    subcategory = infer_subcategory(item, reference)
    full_name = " ".join(part for part in [item, brand, reference] if part and part != "N/A")
    observations = []
    if generated:
        observations.append(f"Codigo generado desde fila Excel {row_number}; codigo origen: {original_code or 'sin codigo'}.")
    if not quantity_ok:
        observations.append(f"Stock no numerico en Excel: {raw_stock!r}; cargado como 0.")

    return {
        "modulo": "Consumibles",
        "categoria": category,
        "subcategoria": subcategory,
        "item": item,
        "referencia": reference,
        "marca": brand,
        "cantidad": quantity,
        "unidad": unit,
        "codigo_interno": code,
        "codigo_original": original_code,
        "codigo_excel": source_code,
        "documento_id": code,
        "producto_id": code,
        "ubicacion": location,
        "nombre_completo": full_name,
        "busqueda": normalize_text(f"{code} {full_name} {category} {subcategory} {location}").lower(),
        "activo": True,
        "fila_excel": row_number,
        "stock_excel_original": str(raw_stock or ""),
        "codigo_raw_origen": raw_match.get("codigo", ""),
        "fila_raw_origen": raw_match.get("fila"),
        "codigo_generado": generated,
        "requiere_revision_stock": not quantity_ok,
        "observaciones": " ".join(observations),
    }


def parse_workbook(path):
    products = []
    warnings = []
    generated_tornilleria = 0

    with ZipFile(path) as zip_file:
        shared_strings = load_shared_strings(zip_file)
        paths = workbook_paths(zip_file)
        if "PRODUCTOS" not in paths:
            raise RuntimeError("No se encontro la hoja PRODUCTOS")

        raw_matches = {}
        if "RAW_ORIGEN" in paths:
            for row_number, values in read_rows(zip_file, paths["RAW_ORIGEN"], shared_strings):
                if row_number < 6:
                    continue
                raw_item = values.get(3, "")
                raw_brand = values.get(4, "")
                raw_ref = values.get(5, "")
                raw_unit = values.get(6, "")
                raw_stock, _ = parse_quantity(values.get(9, ""))
                raw_total, _ = parse_quantity(values.get(11, ""))
                if not str(raw_item or "").strip() or clean_text(raw_item) == "#N/A":
                    continue
                raw_data = {
                    "fila": row_number,
                    "codigo": clean_code(values.get(2, "")),
                    "item": clean_text(raw_item),
                    "marca": clean_text(raw_brand),
                    "referencia": clean_text(raw_ref),
                    "unidad": clean_text(raw_unit),
                    "stock": raw_stock,
                    "total": raw_total,
                }
                raw_matches.setdefault(make_match_key(raw_item, raw_ref, raw_unit), []).append(raw_data)
                raw_matches.setdefault(make_match_key(raw_item, raw_ref, ""), []).append(raw_data)

        for row_number, values in read_rows(zip_file, paths["PRODUCTOS"], shared_strings):
            if row_number < 5:
                continue
            raw_code = values.get(1, "")
            raw_item = values.get(2, "")
            raw_ref = values.get(3, "")
            raw_unit = values.get(4, "")
            raw_stock = values.get(5, "")
            if not any(str(value or "").strip() for value in [raw_code, raw_item, raw_ref, raw_unit, raw_stock]):
                continue
            if not str(raw_item or "").strip():
                warnings.append({"fila": row_number, "tipo": "sin_item", "codigo": raw_code})
                continue
            if clean_code(raw_code) == "TORNILLERIA":
                generated_tornilleria += 1
            quantity, _ = parse_quantity(raw_stock)
            raw_match = choose_raw_match(raw_matches, raw_item, raw_ref, raw_unit, quantity)
            product = product_from_row(row_number, raw_code, raw_item, raw_ref, raw_unit, raw_stock, generated_tornilleria, raw_match)
            products.append(product)

    codes = [product["codigo_interno"] for product in products]
    duplicates = sorted(code for code, count in Counter(codes).items() if count > 1)
    if duplicates:
        raise RuntimeError(f"Codigos duplicados tras normalizar: {', '.join(duplicates)}")

    summary = {
        "archivo": str(path),
        "productos": len(products),
        "cantidad_total": sum(product["cantidad"] for product in products),
        "codigos_generados": sum(1 for product in products if product["codigo_generado"]),
        "productos_con_marca": sum(1 for product in products if product["marca"]),
        "requieren_revision_stock": sum(1 for product in products if product["requiere_revision_stock"]),
        "por_categoria": dict(sorted(Counter(product["categoria"] for product in products).items())),
        "por_prefijo": dict(sorted(Counter(product["codigo_interno"].split("-")[0] for product in products).items())),
    }
    return {"summary": summary, "warnings": warnings, "products": sorted(products, key=lambda p: p["codigo_interno"])}


def main():
    if len(sys.argv) < 3:
        print("Uso: parse_consumibles_kardex_xlsx.py <input.xlsx> <output.json>", file=sys.stderr)
        return 2
    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    parsed = parse_workbook(input_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(parsed, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(parsed["summary"], ensure_ascii=False, indent=2))
    if parsed["warnings"]:
        print(json.dumps({"warnings": parsed["warnings"][:20]}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
