"""Busca un elemento en la pantalla del emulador (por texto o descripción) e imprime su centro "x y".

Uso: python3 ui.py <archivo_ui.xml> <texto> [--contains]
`uiautomator dump` genera un XML con todos los nodos visibles y sus coordenadas (bounds).
"""
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
contains = "--contains" in sys.argv
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)

for node in root.iter("node"):
    for attr in ("text", "content-desc"):
        value = node.get(attr) or ""
        if value == wanted or (contains and wanted.lower() in value.lower()):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print((x1 + x2) // 2, (y1 + y2) // 2)
                sys.exit(0)
