import { type CSSProperties, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Camera,
  ChevronDown,
  CircleDollarSign,
  ExternalLink,
  Eye,
  FileSpreadsheet,
  Inbox,
  LogOut,
  PackageCheck,
  Search,
  ShieldCheck,
  PanelLeftClose,
  PanelLeftOpen,
  SlidersHorizontal,
  UserRound,
  X,
} from 'lucide-react';
import { onAuthStateChanged, signInWithEmailAndPassword, signOut, User } from 'firebase/auth';
import { collection, doc, onSnapshot, QueryDocumentSnapshot, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { auth, db } from './firebase';
import {
  AGROQUIMICOS_UBICACIONES,
  coincideUbicacionAgroquimicos,
  etiquetaUbicacionAgroquimicos,
  movimientoPerteneceUbicacionAgroquimicos,
} from './agroquimicosCanonicos';
import {
  coincideSubmoduloTaller,
  esBodegaRojaTaller,
  esEntradaVistaTaller,
  esSalidaVistaTaller,
  esTrasladoMovimiento,
  etiquetasMovimientoTaller,
  extraerNumeroQr,
  movimientoPerteneceSubmoduloTaller,
  normalizarSubmoduloTaller,
  resolverSubmoduloDesdeCampos,
  TALLER_SUBMODULOS,
} from './tallerCanonicos';
import SubmoduleButtons from './ui/SubmoduleButtons';
import InventoryValuationModule from './ui/InventoryValuationModule';
import ValuationEditModal from './ui/ValuationEditModal';
import type { CurrentValuationRow, ValuationSaveState } from './valuation/models';
import {
  crearReporteMovimientos,
  exportarReporteMovimientos,
  etiquetaPeriodoReporte,
  fechaExportacionReporte,
  nombreArchivoReporte,
} from './reporteMovimientosExcel';
import { brand, inventoryValuationModule, moduleAccent, moduleDescription, modules } from './theme';

const logoSrc = './logo-arles.jpeg';
const brandName = brand.name;
const operationalModules = modules.filter((entry) => entry !== inventoryValuationModule);

const moduleIcons: Record<string, string> = {
  [inventoryValuationModule]: './module-icons/valoracion.svg',
  EPP: './module-icons/epp.svg',
  Dotación: './module-icons/dotacion.svg',
  Consumibles: './module-icons/consumibles.svg',
  ASEO: './module-icons/aseo.svg',
  Agroquimicos: './module-icons/agroquimicos.svg',
  'Lubricantes taller': './module-icons/lubricantes.svg',
  Químico: './module-icons/quimico.svg',
  Combustible: './module-icons/combustible.svg',
  TALLER: './module-icons/herramientas.svg',
};

function moduleIcon(moduleName: string) {
  return moduleIcons[moduleName] ?? './module-icons/consumibles.svg';
}

type InventoryItem = {
  id: string;
  valuationId: string;
  modulo: string;
  codigo: string;
  descripcion: string;
  referencia: string;
  categoria: string;
  unidad: string;
  saldo: number;
  estado?: string;
  ubicacion?: string;
  subcategoria?: string;
  caracteristica?: string;
  total?: number;
  ocupados?: number;
  requiereQr?: boolean;
  codigoQr?: string;
  responsable?: string;
};

type OccupiedUnitCard = {
  id: string;
  submodulo: string;
  codigo: string;
  descripcion: string;
  subcategoria?: string;
  caracteristica?: string;
  solicitante: string;
  unitIndex: number;
  unitTotal: number;
};

type OccupiedSubmoduleGroup = {
  submodulo: string;
  items: OccupiedUnitCard[];
};

type Movement = {
  id: string;
  modulo: string;
  tipo: string;
  codigo: string;
  descripcion: string;
  referencia: string;
  cantidad: number;
  unidad: string;
  fecha: string;
  solicitante: string;
  cargo: string;
  usuario: string;
  observaciones: string;
  fotoUrl: string;
  submodulo?: string;
  submoduloOrigen?: string;
  maquinaria?: string;
  ubicacion?: string;
  zona?: string;
  labor?: string;
  frente?: string;
  horometro?: string;
  debugExtras?: string;
  responsableEntrega?: string;
};

type UserProfile = {
  id: string;
  nombre: string;
  cargo: string;
  email: string;
};

type Totals = Record<string, { entradas: number; salidas: number }>;

type StatusOrder = 'default' | 'good-first' | 'maintenance-first';

type PanelCache = {
  version: 1;
  inventory: InventoryItem[];
  aseoInventory: InventoryItem[];
  tools: InventoryItem[];
  movements: Movement[];
  users: Record<string, UserProfile>;
  lastSync: string;
};

const panelCacheKey = 'gestion-almacen-panel-cache-v1';
const retiredToolCodes = new Set(['001', '002', 'QR-001', 'QR-002']);

function hasPanelCacheData(cache: PanelCache) {
  return cache.inventory.length > 0 || cache.aseoInventory.length > 0 || cache.tools.length > 0 || cache.movements.length > 0 || Object.keys(cache.users).length > 0;
}

function loadPanelCache(): PanelCache | null {
  try {
    const raw = window.localStorage.getItem(panelCacheKey);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PanelCache>;
    if (!Array.isArray(parsed.inventory) || !Array.isArray(parsed.tools) || !Array.isArray(parsed.movements)) return null;
    return {
      version: 1,
      inventory: parsed.inventory.map(withValuationId),
      aseoInventory: Array.isArray(parsed.aseoInventory) ? parsed.aseoInventory.map(withValuationId) : [],
      tools: parsed.tools.map(withValuationId),
      movements: parsed.movements,
      users: parsed.users && typeof parsed.users === 'object' ? parsed.users : {},
      lastSync: typeof parsed.lastSync === 'string' ? parsed.lastSync : '',
    };
  } catch {
    return null;
  }
}

function savePanelCache(cache: PanelCache) {
  try {
    window.localStorage.setItem(panelCacheKey, JSON.stringify(cache));
  } catch {
    // Si el almacenamiento local se llena o falla, Firestore conserva su propia cache.
  }
}

function textValue(data: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = data[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
    if (typeof value === 'number') return String(value);
  }
  return '';
}

function numberValue(data: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = data[key];
    if (typeof value === 'number') return value;
    if (typeof value === 'string') {
      const parsed = Number(value.replace(',', '.'));
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return 0;
}

function valuationDocumentId(source: string, id: string) {
  return `${source}__${encodeURIComponent(id)}`;
}

function withValuationId(item: InventoryItem): InventoryItem {
  if (item.valuationId) return item;
  if (item.id.startsWith('aseo-')) {
    return { ...item, valuationId: valuationDocumentId('productos_aseo', item.id.replace(/^aseo-/, '')) };
  }
  if (item.id.startsWith('herramienta-')) {
    return { ...item, valuationId: valuationDocumentId('herramientas', item.id.replace(/^herramienta-/, '')) };
  }
  if (item.id.startsWith('fallback-')) {
    return { ...item, valuationId: valuationDocumentId('catalogo_respaldo', item.id.replace(/^fallback-/, '')) };
  }
  return { ...item, valuationId: valuationDocumentId('existencias', item.id) };
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(value);
}

function valuationQuantity(item: InventoryItem) {
  return Math.max(item.total ?? item.saldo, 0);
}

function findValueByKeyPart(data: Record<string, unknown>, parts: string[]): string {
  // Busca en cualquier key que contenga alguna de las partes (útil para Combustible donde los nombres de campo pueden variar)
  const entries = Object.entries(data);
  const lowerParts = parts.map(p => p.toLowerCase());
  for (const [key, value] of entries) {
    const k = key.toLowerCase();
    if (lowerParts.some(part => k.includes(part))) {
      if (typeof value === 'string' && value.trim()) return value.trim();
      if (typeof value === 'number') return String(value);
    }
  }
  return '';
}

function normalize(text: string) {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim()
    .toLowerCase();
}

function normalizeModule(text: string) {
  return normalize(text).replace(/\s+/g, '');
}

function moduleMatches(value: string, module: string) {
  const a = normalizeModule(value);
  const b = normalizeModule(module);
  if (module === 'TALLER') return a === 'taller' || a.includes('herramienta');
  if (b === 'agroquimicos') return a === 'agroquimicos' || a.includes('agroquimico');
  if (b === 'lubricantestaller') return a === 'lubricantestaller' || (a.includes('lubricante') && a.includes('taller'));
  if (b === 'aseo') return a === 'aseo';
  return a === b || a.includes(b);
}

function valuationModuleForItem(item: InventoryItem) {
  return operationalModules.find((moduleName) => moduleMatches(item.modulo, moduleName)) ?? item.modulo;
}

function isChemicalModule(moduleName: string) {
  return moduleMatches(moduleName, 'Agroquimicos') || moduleMatches(moduleName, 'Lubricantes taller');
}

function normalizeAseoCode(value: string) {
  const compact = value.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  const match = compact.match(/^H(\d{2})(\d{3})$/);
  return match ? `H${match[1]}-${match[2]}` : value.trim().toUpperCase();
}

function normalizeToolCode(value: string) {
  const clean = value.trim().toUpperCase();
  if (/^\d+$/.test(clean)) return `QR-${clean}`;
  return clean;
}

function displayCodeForInventory(data: Record<string, unknown>, modulo: string, fallback: string) {
  if (isChemicalModule(modulo)) {
    return textValue(data, 'codigo_original', 'codigoOriginal', 'codigo_excel', 'codigo', 'codigo_interno', 'codigoInterno') || fallback;
  }
  if (moduleMatches(modulo, 'ASEO')) {
    return normalizeAseoCode(textValue(data, 'codigo_interno', 'codigoInterno', 'codigo') || fallback);
  }
  if (moduleMatches(modulo, 'TALLER')) {
    return normalizeToolCode(textValue(data, 'codigo_principal', 'codigo', 'codigo_qr', 'codigo_interno', 'codigoInterno') || fallback);
  }
  return textValue(data, 'codigo_interno', 'codigoInterno', 'codigo', 'code') || fallback;
}

function displayCodeForMovement(data: Record<string, unknown>, modulo: string, fallback = '') {
  if (isChemicalModule(modulo)) {
    return textValue(data, 'codigo_original', 'codigoOriginal', 'codigo_excel', 'codigo', 'codigo_interno', 'codigoInterno') || fallback;
  }
  if (moduleMatches(modulo, 'ASEO')) {
    return normalizeAseoCode(textValue(data, 'codigo_interno', 'codigoInterno', 'codigo') || fallback);
  }
  if (moduleMatches(modulo, 'TALLER')) {
    return normalizeToolCode(textValue(data, 'codigo_principal', 'codigo', 'codigo_qr', 'codigo_interno', 'codigoInterno') || fallback);
  }
  return textValue(data, 'codigo_interno', 'codigoInterno', 'codigo', 'codigo_interno_origen', 'codigo_original') || fallback;
}

function referenceForDisplay(reference: string, module: string) {
  if (!moduleMatches(module, 'Dotación')) return reference || 'N/A';
  const cleanReference = reference.trim();
  if (!cleanReference) return 'N/A';

  const tallaMatch = cleanReference.match(/\btalla\s*[:\-]?\s*([a-zA-ZáéíóúÁÉÍÓÚñÑ0-9]+)/i);
  const rawSize = tallaMatch?.[1] || cleanReference.match(/\b(única|unica|xs|s|m|l|xl|xxl|xxxl|\d{1,3})\b/i)?.[1] || '';
  if (!rawSize) return cleanReference.split('|')[0].replace(/^[^-:]+[-:]\s*/i, '').trim() || 'N/A';

  const normalizedSize = normalize(rawSize);
  const size = normalizedSize === 'unica' ? 'Unica' : rawSize.toUpperCase();
  return `Talla: ${size}`;
}

function isLubricantesTallerItem(module: string, data: Record<string, unknown>) {
  if (!moduleMatches(module, 'TALLER')) return false;
  const categoria = normalize(textValue(data, 'categoria', 'subcategoria', 'submodulo', 'referencia') || '');
  return categoria.includes('lubricante');
}

function readInventoryDoc(doc: QueryDocumentSnapshot): InventoryItem {
  const data = doc.data();
  const modulo = textValue(data, 'modulo') || 'Sin módulo';
  const codigo = displayCodeForInventory(data, modulo, doc.id);
  const item = textValue(data, 'item', 'producto', 'nombre') || doc.id;
  const subcategoria = textValue(data, 'subcategoria', 'categoria', 'referencia', 'ref');
  const ubicacion = textValue(data, 'ubicacion');
  const referencia = isLubricantesTallerItem(modulo, data) || moduleMatches(modulo, 'Agroquimicos')
    ? ''
    : referenceForDisplay(textValue(data, 'referencia', 'ref', 'talla'), modulo);

  return {
    id: doc.id,
    valuationId: valuationDocumentId('existencias', doc.id),
    modulo,
    codigo,
    descripcion: item,
    referencia,
    categoria: textValue(data, 'categoria') || 'General',
    unidad: textValue(data, 'unidad') || 'Unidad',
    saldo: numberValue(data, 'cantidad', 'stock_actual', 'stock', 'saldo'),
    estado: textValue(data, 'estado'),
    ubicacion,
    subcategoria: subcategoria || 'Sin subcategoría',
  };
}

function readAseoDoc(doc: QueryDocumentSnapshot): InventoryItem {
  const data = doc.data();
  const codigo = normalizeAseoCode(textValue(data, 'codigo_interno') || doc.id);
  const piso = textValue(data, 'piso');
  const pisoTexto = piso.padStart(2, '0');

  return {
    id: `aseo-${doc.id}`,
    valuationId: valuationDocumentId('productos_aseo', doc.id),
    modulo: 'ASEO',
    codigo,
    descripcion: textValue(data, 'producto', 'item', 'nombre') || doc.id,
    referencia: piso ? `Piso ${pisoTexto}` : 'Productos de aseo',
    categoria: textValue(data, 'categoria') || 'Productos de aseo',
    unidad: textValue(data, 'unidad') || 'Unidad',
    saldo: numberValue(data, 'stock_actual'),
    estado: textValue(data, 'estado'),
  };
}

function readToolDoc(doc: QueryDocumentSnapshot): InventoryItem {
  const data = doc.data();
  const estado = textValue(data, 'estado') || 'Disponible';
  const marca = textValue(data, 'marca');
  const subcategoria = textValue(data, 'subcategoria', 'referencia', 'ref');
  const tipo = textValue(data, 'tipo', 'tipo_herramienta');
  const tamano = textValue(data, 'tamano');
  const codigoRaw = textValue(data, 'codigo_principal', 'codigo', 'codigo_interno', 'clave') || doc.id;
  const codigo = normalizeToolCode(codigoRaw);
  const codigoQr = extraerNumeroQr(codigoRaw, textValue(data, 'codigo_qr', 'codigoQr', 'qr'));
  const disponible = numberValue(data, 'cantidad_disponible', 'disponibles');
  const total = numberValue(data, 'cantidad_total', 'stock_total', 'cantidad');
  const ocupado = numberValue(data, 'cantidad_ocupada', 'ocupados');
  const totalCalculado = total > 0 ? total : Math.max(disponible + ocupado, 0);
  const disponiblesCalculado = disponible > 0 || total > 0
    ? (disponible > 0 ? disponible : Math.max(totalCalculado - ocupado, 0))
    : 0;
  const caracteristica = [tipo, tamano].filter(Boolean).join(' / ') || 'Sin característica';
  const submodulo = resolverSubmoduloDesdeCampos({
    submoduloTaller: textValue(data, 'submodulo_taller', 'submodulo'),
    categoria: textValue(data, 'categoria'),
    ubicacion: textValue(data, 'ubicacion'),
    seccion: textValue(data, 'seccion', 'area', 'zona'),
  });

  return {
    id: `herramienta-${doc.id}`,
    valuationId: valuationDocumentId('herramientas', doc.id),
    modulo: textValue(data, 'modulo') || 'Taller',
    codigo,
    descripcion: textValue(data, 'nombre', 'item', 'producto') || doc.id,
    referencia: [subcategoria, tipo, tamano, marca].filter(Boolean).join(' - ') || 'N/A',
    categoria: submodulo,
    unidad: textValue(data, 'unidad') || 'Unidad',
    saldo: disponiblesCalculado,
    estado,
    subcategoria: subcategoria || 'Sin subcategoría',
    caracteristica,
    total: totalCalculado,
    ocupados: ocupado,
    requiereQr: Boolean(data.requiere_asignar_qr) || codigo.startsWith('SINQR'),
    codigoQr,
    responsable: textValue(data, 'responsable'),
  };
}

function fallbackTool(codigo: string, subcategoria: string, descripcion: string, caracteristica: string, saldo: number, unidad = 'UNIDAD'): InventoryItem {
  return {
    id: `fallback-${codigo}`,
    valuationId: valuationDocumentId('catalogo_respaldo', codigo),
    modulo: 'Taller',
    codigo,
    descripcion,
    referencia: `${subcategoria} - ${caracteristica}`,
    categoria: 'HERRAMIENTAS TALLER',
    unidad,
    saldo,
    estado: 'Disponible',
    subcategoria,
    caracteristica,
    total: saldo,
    ocupados: 0,
    requiereQr: codigo.startsWith('SINQR'),
  };
}

const fallbackTools: InventoryItem[] = [
  fallbackTool('SINQR-HT-001', 'ALICATES Y PINZAS', 'ALICATE NEGRO', 'ALICATE / GRANDE', 4),
  fallbackTool('SINQR-HT-002', 'ALICATES Y PINZAS', 'ALICATE AMARILLO', 'ALICATE / GRANDE', 1),
  fallbackTool('SINQR-HT-003', 'ALICATES Y PINZAS', 'ALICATE NEGRO', 'ALICATE / PEQUENO', 1),
  fallbackTool('SINQR-HT-018', 'ALICATES Y PINZAS', 'PINZA PARA PIN REDONDO', 'PINZA / GRANDE', 5),
  fallbackTool('SINQR-HT-019', 'ALICATES Y PINZAS', 'PINZA PARA PIN REDONDO', 'PINZA / MEDIANA', 5),
  fallbackTool('SINQR-HT-004', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE PALA', 'PALA / GRANDE', 3),
  fallbackTool('SINQR-HT-005', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE PALA', 'PALA / MEDIANO', 2),
  fallbackTool('SINQR-HT-006', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE PALA', 'PALA / PEQUENO', 1),
  fallbackTool('SINQR-HT-007', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE ESTRELLA', 'ESTRELLA / GRANDE', 4),
  fallbackTool('SINQR-HT-008', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE ESTRELLA', 'ESTRELLA / MEDIANO', 1),
  fallbackTool('SINQR-HT-009', 'DESTORNILLADORES', 'DESTORNILLADOR AZUL DE ESTRELLA', 'ESTRELLA / PEQUENO', 1),
  fallbackTool('SINQR-HT-010', 'LLAVES MANUALES', 'LLAVES MANUALES MILIMETRICAS', 'JUEGO DE LLAVES / VARIOS / #8 AL #1 1/2', 1, 'JUEGO'),
  fallbackTool('SINQR-HT-012', 'LLAVES MANUALES', 'LLAVE DE TUBO', 'LLAVE DE TUBO / GRANDE', 1),
  fallbackTool('SINQR-HT-013', 'LLAVES MANUALES', 'LLAVE DE TUBO', 'LLAVE DE TUBO / PEQUENA', 1),
  fallbackTool('SINQR-HT-014', 'LLAVES MANUALES', 'LLAVE EXPANSIVA', 'LLAVE EXPANSIVA / GRANDE', 4),
  fallbackTool('SINQR-HT-015', 'LLAVES MANUALES', 'LLAVE EXPANSIVA', 'LLAVE EXPANSIVA / PEQUENA', 1),
  fallbackTool('SINQR-HT-016', 'LLAVES BRISTOL Y COPAS', 'JUEGO DE LLAVES BRISTOL', 'BRISTOL / GRANDE', 2, 'JUEGO'),
  fallbackTool('SINQR-HT-017', 'LLAVES BRISTOL Y COPAS', 'JUEGO DE LLAVES BRISTOL', 'BRISTOL / PEQUENA', 1, 'JUEGO'),
  fallbackTool('QR-106', 'LLAVES BRISTOL Y COPAS', 'JUEGO DE COPAS FORCE', 'COPAS / FORCE', 1, 'JUEGO'),
  fallbackTool('QR-107', 'LLAVES BRISTOL Y COPAS', 'JUEGO DE COPAS STANLEY', 'COPAS / STANLEY', 1, 'JUEGO'),
  fallbackTool('QR-269', 'LLAVES BRISTOL Y COPAS', 'JUEGO DE COPAS STANLEY', 'COPAS / STANLEY', 1, 'JUEGO'),
  fallbackTool('SINQR-HT-011', 'SUJECION Y PRESION', 'HOMBRE SOLO', 'HOMBRE SOLO / GRANDE', 2),
  fallbackTool('SINQR-HT-020', 'SUJECION Y PRESION', 'DIABLO NEGRO', 'DIABLO / GRANDE', 2),
  fallbackTool('SINQR-HT-021', 'SUJECION Y PRESION', 'DIABLO AMARILLO', 'DIABLO / GRANDE', 1),
  fallbackTool('SINQR-HT-022', 'CORTE, GOLPE Y CINCELADO', 'SERRUCHO', 'SERRUCHO / GRANDE', 1),
  fallbackTool('SINQR-HT-023', 'CORTE, GOLPE Y CINCELADO', 'SEGUETA', 'SEGUETA / GRANDE', 1),
  fallbackTool('SINQR-HT-024', 'CORTE, GOLPE Y CINCELADO', 'JUEGO DE CINCELES DE ACERO MILIMETRICOS', 'CINCEL / VARIOS / #6 AL #13', 1, 'JUEGO'),
  fallbackTool('SINQR-HT-025', 'CORTE, GOLPE Y CINCELADO', 'MARTILLO', 'MARTILLO / GRANDE', 1),
  fallbackTool('QR-104', 'KITS DE ROSCA', 'KIT DE ROSCA EXTERNA', 'ROSCA EXTERNA', 1, 'KIT'),
  fallbackTool('QR-105', 'KITS DE ROSCA', 'KIT DE ROSCA INTERNA', 'ROSCA INTERNA', 1, 'KIT'),
  fallbackTool('QR-912', 'HERRAMIENTAS ELECTRICAS', 'TALADRO INALAMBRICO', 'TALADRO', 1),
  fallbackTool('QR-250', 'HERRAMIENTAS ELECTRICAS', 'TALADRO ELECTRICO', 'TALADRO', 1),
  fallbackTool('QR-249', 'HERRAMIENTAS ELECTRICAS', 'PULIDORA DEWALT', 'PULIDORA / GRANDE / DEWALT', 1),
  fallbackTool('QR-248', 'HERRAMIENTAS ELECTRICAS', 'PULIDORA DEWALT', 'PULIDORA / PEQUENA / DEWALT', 1),
  fallbackTool('QR-914', 'HERRAMIENTAS ELECTRICAS', 'TROZADORA DEWALT', 'TROZADORA / GRANDE / DEWALT', 1),
  fallbackTool('QR-103', 'HERRAMIENTAS ELECTRICAS', 'POLICHADORA BAUKER', 'POLICHADORA / BAUKER', 1),
  fallbackTool('QR-416', 'HERRAMIENTAS ELECTRICAS', 'PISTOLA DE IMPACTO', 'PISTOLA DE IMPACTO', 1),
  fallbackTool('QR-1007', 'HERRAMIENTAS ELECTRICAS', 'MOTOSIERRA ELECTRICA AZUL', 'MOTOSIERRA ELECTRICA / PEQUENA', 1),
  fallbackTool('QR-1023', 'HERRAMIENTAS ELECTRICAS', 'GRAPADORA', 'GRAPADORA', 1),
  fallbackTool('QR-494', 'EQUIPOS DE TALLER', 'SOPLADOR AMARILLO', 'SOPLADOR', 1),
  fallbackTool('QR-956', 'EQUIPOS DE TALLER', 'EQUIPO SOLDADURA NEXT INVERSOR INV9200', 'SOLDADURA / NEXT / INV9200', 1),
  fallbackTool('QR-58', 'EQUIPOS DE TALLER', 'COMPRESOR WOLFOX ROJO', 'COMPRESOR / PEQUENO / WOLFOX', 1),
  fallbackTool('QR-185', 'EQUIPOS DE TALLER', 'COMPRESOR ROJO', 'COMPRESOR / GRANDE', 1),
  fallbackTool('QR-916', 'EQUIPOS DE TALLER', 'ESMERIL TRUPER', 'ESMERIL / TRUPER', 1),
  fallbackTool('QR-271', 'EQUIPOS DE TALLER', 'INYECTOR FERTON AMARILLO', 'INYECTOR / GRANDE / FERTON', 1),
  fallbackTool('QR-108', 'EQUIPOS DE TALLER', 'OXICORTE AZUL', 'OXICORTE / GRANDE', 1),
  fallbackTool('QR-948', 'HIDRAULICOS Y SUMINISTRO', 'GATO HIDRAULICO ROJO', 'GATO HIDRAULICO / GRANDE', 1),
  fallbackTool('SINQR-HT-026', 'HIDRAULICOS Y SUMINISTRO', 'GATO HIDRAULICO AZUL', 'GATO HIDRAULICO', 1),
  fallbackTool('SINQR-HT-027', 'HIDRAULICOS Y SUMINISTRO', 'BOMBA DE SUMINISTRO ACPM', 'BOMBA DE SUMINISTRO / ACPM', 1),
];

function visibleToolInventory(currentTools: InventoryItem[], usingOfflineFallback: boolean) {
  if (currentTools.length > 0) return currentTools;
  return usingOfflineFallback ? fallbackTools : [];
}

function readMovementDoc(doc: QueryDocumentSnapshot): Movement {
  const data = doc.data();
  const modulo = textValue(data, 'modulo') || 'Sin módulo';
  const tipo = textValue(data, 'tipoMovimiento', 'tipo', 'movimiento') || 'Movimiento';

  const movement: Movement = {
    id: doc.id,
    modulo,
    tipo,
    codigo: displayCodeForMovement(
      data,
      modulo,
      textValue(data, 'codigo_interno', 'codigoInterno', 'documento_id', 'producto_id', 'herramienta_clave', 'herramientaId'),
    ),
    descripcion: textValue(data, 'item', 'producto', 'herramientaNombre') || 'Sin descripción',
    referencia: referenceForDisplay(textValue(data, 'referencia', 'ref', 'talla'), modulo),
    cantidad: numberValue(data, 'cantidad'),
    unidad: textValue(data, 'unidad') || 'Unidad',
    fecha: dateTextValue(data, 'fecha', 'createdAt') || '',
    solicitante: textValue(data, 'solicitante') || textValue(data, 'responsable'),
    cargo: textValue(data, 'labor', 'cargo'),
    usuario: textValue(data, 'usuario', 'registradoPor', 'usuario_uid'),
    observaciones: textValue(data, 'observaciones', 'nota'),
    fotoUrl: textValue(data, 'fotoUrl', 'foto_url', 'evidenciaUrl', 'evidencia_url', 'evidencia', 'photoUrl', 'photo_url'),
    submodulo: resolverSubmoduloDesdeCampos({
      submoduloTaller: textValue(data, 'submodulo_taller', 'submodulo', 'categoria'),
      categoria: textValue(data, 'categoria'),
      ubicacion: textValue(data, 'ubicacion'),
      seccion: textValue(data, 'seccion'),
    }),
    submoduloOrigen: (() => {
      const raw = textValue(data, 'submodulo_origen');
      return raw ? normalizarSubmoduloTaller(raw) : undefined;
    })(),
    maquinaria: (() => {
      const raw = textValue(data, 'maquinaria', 'equipo', 'maquina', 'vehiculo') ||
                  findValueByKeyPart(data, ['maquin', 'equipo', 'maq']);
      if (!raw) return undefined;
      // Para Taller normalizamos, para Combustible y otros usamos el valor tal cual
      if (moduleMatches(modulo, 'TALLER')) {
        return normalizarSubmoduloTaller(raw);
      }
      return raw;
    })(),
    zona: textValue(data, 'zona_ejecucion', 'zona') || findValueByKeyPart(data, ['zona', 'ejecucion']),
    labor: textValue(data, 'tipo_labor', 'labor', 'frente') || findValueByKeyPart(data, ['labor', 'frente', 'actividad', 'obra']),
    frente: textValue(data, 'frente', 'labor_frente', 'frente_trabajo') || findValueByKeyPart(data, ['frente', 'frent']),
    horometro: textValue(data, 'horometro', 'horómetro', 'horomet', 'horas', 'lectura_horometro', 'horometro_maquinaria') || findValueByKeyPart(data, ['horom', 'horas', 'lectura', 'horomet']),
    responsableEntrega: textValue(data, 'responsable_entrega', 'registradoPor'),
    ubicacion: textValue(data, 'ubicacion'),
  };

  // Debug temporal para Combustible - visible en la UI para que puedas ver los campos reales sin necesidad de consola
  if (moduleMatches(modulo, 'Combustible')) {
    const hasHor = !!movement.horometro;
    const hasLabor = !!(movement.labor || movement.frente);
    if (!hasHor && !hasLabor) {
      const interestingKeys = Object.keys(data).filter(k =>
        /hor|lab|frent|maq|equi|obra|activ|zona|maquina|equipo|horo/i.test(k)
      );
      if (interestingKeys.length > 0) {
        const pairs = interestingKeys.map(k => `${k}=${JSON.stringify(data[k])}`).join(' | ');
        (movement as any).debugExtras = pairs;
        console.log('[DEBUG Combustible sin hor/labor]', doc.id, 'keys interesantes:', interestingKeys);
        console.log('[DEBUG Combustible raw data]', doc.id, JSON.stringify(data, null, 2));
      }
    }
  }

  return movement;
}

function readUserDoc(doc: QueryDocumentSnapshot): UserProfile {
  const data = doc.data();
  const nombres = textValue(data, 'nombres', 'nombre', 'displayName');
  const apellidos = textValue(data, 'apellidos', 'apellido');
  const nombre = [nombres, apellidos].filter(Boolean).join(' ').trim();

  return {
    id: doc.id,
    nombre: nombre || textValue(data, 'email') || doc.id,
    cargo: textValue(data, 'cargo', 'rol'),
    email: textValue(data, 'email'),
  };
}

function statusFor(item: InventoryItem, overrideEstado?: string) {
  if (moduleMatches(item.modulo, 'TALLER')) {
    if (item.requiereQr) {
      const occ = item.ocupados ?? 0;
      if (occ > 0) return { label: 'En uso', className: 'warning' };
      return { label: 'Disponible', className: 'ok' };
    }
    const estado = overrideEstado ?? item.estado;
    if (estado) {
      const normalized = normalize(estado);
      if (normalized.includes('disponible')) return { label: estado, className: 'ok' };
      if (normalized.includes('uso') || normalized.includes('prest')) return { label: estado, className: 'warning' };
      if (normalized.includes('mant')) return { label: estado, className: 'danger' };
      return { label: estado, className: 'notice' };
    }
  }
  if (item.saldo <= 0) return { label: 'Sin stock', className: 'danger' };
  if (item.saldo <= 3) return { label: 'Crítico', className: 'warning' };
  if (item.saldo <= 10) return { label: 'Bajo', className: 'notice' };
  return { label: 'Disponible', className: 'ok' };
}

function statusCategory(item: InventoryItem) {
  const label = statusFor(item).label.toLowerCase();
  if (label.includes('mant')) return 'maintenance';
  if (label.includes('disponible') || label.includes('ok') || label.includes('bueno')) return 'good';
  return 'other';
}

function statusPriority(item: InventoryItem, order: StatusOrder) {
  const category = statusCategory(item);
  if (order === 'good-first') {
    if (category === 'good') return 0;
    if (category === 'other') return 1;
    return 2;
  }
  if (order === 'maintenance-first') {
    if (category === 'maintenance') return 0;
    if (category === 'other') return 1;
    return 2;
  }
  return 0;
}

function compareCodes(a: string, b: string) {
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

function compareDate(a: Movement, b: Movement) {
  return (b.fecha || '').localeCompare(a.fecha || '');
}

function isEntry(movement: Movement) {
  return normalize(movement.tipo).includes('entrada') || normalize(movement.tipo).includes('ingreso');
}

function isExit(movement: Movement) {
  const tipo = normalize(movement.tipo);
  return tipo.includes('salida') || tipo.includes('entrega') || tipo.includes('traslado') || tipo.includes('consumo');
}

function movementIsEntry(movement: Movement, tallerSubmodulo: string) {
  if (tallerSubmodulo) return esEntradaVistaTaller(movement, tallerSubmodulo);
  return isEntry(movement);
}

function movementIsExit(movement: Movement, tallerSubmodulo: string) {
  if (tallerSubmodulo) return esSalidaVistaTaller(movement, tallerSubmodulo);
  return isExit(movement);
}

function reconcileKey(modulo: string, codigo: string, descripcion: string, referencia: string) {
  const mod = normalizeModule(modulo || 'sinmodulo');
  const code = codigo.trim();
  if (code && code.toLowerCase() !== 'sin código') return `${mod}::${normalize(code)}`;
  const desc = normalize(descripcion);
  const ref = normalize(referencia);
  if (desc) return `${mod}::${desc}|${ref}`;
  return `${mod}::${ref || 'sinreferencia'}`;
}

function inventoryLookupKeys(item: InventoryItem) {
  const keys = [
    reconcileKey(item.modulo, item.codigo, item.descripcion, item.referencia),
    reconcileKey(item.modulo, item.codigo, item.descripcion, ''),
    reconcileKey(item.modulo, item.codigo, '', ''),
    reconcileKey(item.modulo, '', item.descripcion, item.referencia),
  ];
  return [...new Set(keys)];
}

function movementLookupKeys(movement: Movement) {
  const keys = [
    reconcileKey(movement.modulo, movement.codigo, movement.descripcion, movement.referencia),
    reconcileKey(movement.modulo, movement.codigo, movement.descripcion, ''),
    reconcileKey(movement.modulo, movement.codigo, '', ''),
    reconcileKey(movement.modulo, '', movement.descripcion, movement.referencia),
  ];
  return [...new Set(keys)];
}

function buildTotals(movements: Movement[], tallerSubmodulo = ''): Totals {
  return movements.reduce<Totals>((acc, movement) => {
    const key = reconcileKey(movement.modulo, movement.codigo, movement.descripcion, movement.referencia);
    const current = acc[key] ?? { entradas: 0, salidas: 0 };
    if (movementIsEntry(movement, tallerSubmodulo)) current.entradas += movement.cantidad;
    if (movementIsExit(movement, tallerSubmodulo)) current.salidas += movement.cantidad;
    acc[key] = current;
    return acc;
  }, {});
}

function lookupTotals(keys: string[], totals: Totals) {
  for (const key of keys) {
    const found = totals[key];
    if (found) return found;
  }
  return { entradas: 0, salidas: 0 };
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(value);
}

const FUEL_TYPES = ['Gasolina', 'ACPM', 'Urea'] as const;

function fuelTypeFromItem(item: InventoryItem): (typeof FUEL_TYPES)[number] | null {
  const haystack = normalize(`${item.descripcion} ${item.referencia} ${item.subcategoria ?? ''} ${item.categoria}`);
  const match = FUEL_TYPES.find((tipo) => {
    const key = normalize(tipo);
    return haystack.includes(key) || (haystack.includes('liquido') && haystack.includes(key));
  });
  return match ?? null;
}

function combustibleStockByType(inventory: InventoryItem[]) {
  const stock: Record<(typeof FUEL_TYPES)[number], number> = { Gasolina: 0, ACPM: 0, Urea: 0 };
  inventory.forEach((item) => {
    const tipo = fuelTypeFromItem(item);
    if (tipo) stock[tipo] += item.saldo;
  });
  return stock;
}

function balanceTone(value: number): 'ok' | 'warning' | 'danger' {
  if (value <= 0) return 'danger';
  if (value <= 3) return 'warning';
  return 'ok';
}

function balanceClassName(value: number) {
  return `numeric balance balance-${balanceTone(value)}`;
}

function formatSyncLabel(value: string) {
  if (!value) return 'Sin sincronizar';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function canPreviewEvidence(url: string) {
  return /^https?:\/\//i.test(url) || /^data:image\//i.test(url);
}

function formatDateKey(date: Date) {
  if (Number.isNaN(date.getTime())) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDateTime(date: Date) {
  const key = formatDateKey(date);
  if (!key) return '';
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${key} ${hours}:${minutes}`;
}

function dateTextValue(data: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = data[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
    if (typeof value === 'number') return formatDateTime(new Date(value < 1_000_000_000_000 ? value * 1000 : value));
    if (value instanceof Date) return formatDateTime(value);
    if (value && typeof value === 'object') {
      const maybeTimestamp = value as { toDate?: () => Date; seconds?: number };
      if (typeof maybeTimestamp.toDate === 'function') return formatDateTime(maybeTimestamp.toDate());
      if (typeof maybeTimestamp.seconds === 'number') return formatDateTime(new Date(maybeTimestamp.seconds * 1000));
    }
  }
  return '';
}

function dateKeyFromText(value: string) {
  const text = value.trim();
  if (!text) return '';

  const iso = text.match(/\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b/);
  if (iso) return `${iso[1]}-${iso[2].padStart(2, '0')}-${iso[3].padStart(2, '0')}`;

  const dmy = text.match(/\b(\d{1,2})[-/](\d{1,2})[-/](\d{4})\b/);
  if (dmy) return `${dmy[3]}-${dmy[2].padStart(2, '0')}-${dmy[1].padStart(2, '0')}`;

  return formatDateKey(new Date(text));
}

function inDateRange(value: string, from: string, to: string) {
  if (!from && !to) return true;
  const key = dateKeyFromText(value);
  if (!key) return false;
  if (from && key < from) return false;
  if (to && key > to) return false;
  return true;
}

function userDisplayName(rawUser: string, users: Record<string, UserProfile>) {
  const value = rawUser.trim();
  if (!value) return '';

  const profile = users[value];
  if (profile) {
    const firstName = profile.nombre.split(/\s+/).filter(Boolean).slice(0, 2).join(' ');
    return [firstName || profile.email, profile.cargo].filter(Boolean).join(' ');
  }

  return value;
}

function movementPersonText(movement: Movement, users: Record<string, UserProfile>) {
  return [
    movement.solicitante,
    movement.cargo,
    movement.usuario,
    userDisplayName(movement.usuario, users),
  ].filter(Boolean).join(' ');
}

function movementItemText(movement: Movement) {
  return [
    movement.codigo,
    movement.descripcion,
    movement.referencia,
    movement.unidad,
  ].filter(Boolean).join(' ');
}

function totalsForItem(item: InventoryItem, totals: Totals) {
  return lookupTotals(inventoryLookupKeys(item), totals);
}

function latestExitForItem(movements: Movement[], item: InventoryItem) {
  const keys = new Set(inventoryLookupKeys(item));
  return movements
    .filter((movement) => isExit(movement) && movementLookupKeys(movement).some((key) => keys.has(key)))
    .sort(compareDate)[0];
}

function expandTallerOccupiedUnits(items: InventoryItem[], movements: Movement[]): OccupiedUnitCard[] {
  const cards: OccupiedUnitCard[] = [];

  items.forEach((item) => {
    const unitTotal = Math.max(0, Math.floor(item.ocupados ?? 0));
    if (unitTotal <= 0) return;

    const latestExit = latestExitForItem(movements, item);
    const solicitante = latestExit?.solicitante || item.responsable || 'Sin responsable';
    const submodulo = normalizarSubmoduloTaller(item.categoria);

    for (let index = 0; index < unitTotal; index += 1) {
      cards.push({
        id: `${item.id}-occ-${index + 1}`,
        submodulo,
        codigo: item.codigoQr || item.codigo,
        descripcion: item.descripcion,
        subcategoria: item.subcategoria,
        caracteristica: item.caracteristica,
        solicitante,
        unitIndex: index + 1,
        unitTotal,
      });
    }
  });

  return cards;
}

function groupOccupiedBySubmodule(units: OccupiedUnitCard[]): OccupiedSubmoduleGroup[] {
  const grouped = new Map<string, OccupiedUnitCard[]>();
  units.forEach((unit) => {
    const list = grouped.get(unit.submodulo) ?? [];
    list.push(unit);
    grouped.set(unit.submodulo, list);
  });

  const ordered: OccupiedSubmoduleGroup[] = TALLER_SUBMODULOS
    .map((submodulo) => ({ submodulo, items: grouped.get(submodulo) ?? [] }))
    .filter((group) => group.items.length > 0);

  grouped.forEach((items, submodulo) => {
    if (!TALLER_SUBMODULOS.some((entry) => entry === submodulo)) {
      ordered.push({ submodulo, items });
    }
  });

  return ordered;
}

function reconcileInventoryWithMovements(stockItems: InventoryItem[], _movements: Movement[]): InventoryItem[] {
  // Saldo = valor en Firestore. La app Android actualiza existencias / productos_aseo / herramientas
  // en cada movimiento. Los totales de entradas/salidas en la tabla son solo informativos.
  return stockItems;
}

function LoginScreen({ onLogin }: { onLogin: (email: string, password: string) => Promise<void> }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await onLogin(email, password);
    } catch (err) {
      setError('No pude iniciar sesión. Revisa el correo, la clave o permisos del usuario.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-panel">
        <img src={logoSrc} alt={brandName} className="login-logo" />
        <div>
          <p className="eyebrow">Panel de computador</p>
          <h1>{brandName}</h1>
          <h2 className="login-title-sub">Gestión de almacén</h2>
          <p className="login-copy">Conexión directa a Firestore para revisar inventario, entradas y salidas.</p>
        </div>
        <form onSubmit={submit} className="login-form">
          <label>
            Correo
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" required />
          </label>
          <label>
            Contraseña
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" required />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button type="submit" disabled={loading}>{loading ? 'Ingresando...' : 'Ingresar'}</button>
        </form>
      </section>
    </main>
  );
}

// Render modal at the end of file to keep App function focused
// Note: placed here to avoid extra state lifting


function AppShell({ user }: { user: User }) {
  const [cachedPanelData] = useState(loadPanelCache);
  const [module, setModule] = useState<string>(inventoryValuationModule);
  const [search, setSearch] = useState('');
  const [inventory, setInventory] = useState<InventoryItem[]>(() => cachedPanelData?.inventory ?? []);
  const [aseoInventory, setAseoInventory] = useState<InventoryItem[]>(() => cachedPanelData?.aseoInventory ?? []);
  const [tools, setTools] = useState<InventoryItem[]>(() => cachedPanelData?.tools ?? []);
  const [movements, setMovements] = useState<Movement[]>(() => cachedPanelData?.movements ?? []);
  const [users, setUsers] = useState<Record<string, UserProfile>>(() => cachedPanelData?.users ?? {});
  const [valuations, setValuations] = useState<Record<string, number>>({});
  const [valuationDocumentIds, setValuationDocumentIds] = useState<Set<string>>(() => new Set());
  const [valuationDrafts, setValuationDrafts] = useState<Record<string, string>>({});
  const [valuationSaveStates, setValuationSaveStates] = useState<Record<string, ValuationSaveState>>({});
  const [valuationEditItem, setValuationEditItem] = useState<InventoryItem | null>(null);
  const [evidenceMovement, setEvidenceMovement] = useState<Movement | null>(null);
  const [showOccupiedModal, setShowOccupiedModal] = useState(false);
  const [showEntriesModal, setShowEntriesModal] = useState(false);
  const [exitDateFrom, setExitDateFrom] = useState('');
  const [exitDateTo, setExitDateTo] = useState('');
  const [exitCode, setExitCode] = useState('');
  const [exitPerson, setExitPerson] = useState('');
  const [exitItem, setExitItem] = useState('');
  const [exitFiltersOpen, setExitFiltersOpen] = useState(false);
  const [tallerSubmodulo, setTallerSubmodulo] = useState('');
  const [agroquimicosUbicacion, setAgroquimicosUbicacion] = useState('');
  const [statusOrder, setStatusOrder] = useState<StatusOrder>('default');
  const [manualStatuses, setManualStatuses] = useState<Record<string, string>>({});
  const [lastSync, setLastSync] = useState(() => cachedPanelData?.lastSync ?? '');
  const [online, setOnline] = useState(() => navigator.onLine);
  const [usingCachedData, setUsingCachedData] = useState(() => Boolean(cachedPanelData));
  const [loading, setLoading] = useState(() => !cachedPanelData);
  const [error, setError] = useState('');
  const [exportando, setExportando] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const isDesktopApp = typeof window !== 'undefined' && Boolean(window.electronAPI?.isElectron);

  function markSnapshotLoaded(fromCache: boolean) {
    setLoading(false);
    if (fromCache) {
      setUsingCachedData(true);
      return;
    }
    setUsingCachedData(false);
    setLastSync(new Date().toISOString());
    setError('');
  }

  function handleSnapshotError(message: string) {
    setLoading(false);
    if (cachedPanelData && !navigator.onLine) {
      setUsingCachedData(true);
      return;
    }
    setError(message);
  }

  useEffect(() => {
    if (!cachedPanelData) setLoading(true);
    const unsubscribeInventory = onSnapshot(
      collection(db, 'existencias'),
      { includeMetadataChanges: true },
      (snapshot) => {
        setInventory(snapshot.docs.map(readInventoryDoc));
        markSnapshotLoaded(snapshot.metadata.fromCache);
      },
      () => {
        setError('No pude leer inventario. Verifica que el usuario esté activo en Firebase.');
        setLoading(false);
      },
    );

    const unsubscribeMovements = onSnapshot(
      collection(db, 'movimientos'),
      { includeMetadataChanges: true },
      (snapshot) => {
        setMovements(snapshot.docs.map(readMovementDoc));
        markSnapshotLoaded(snapshot.metadata.fromCache);
      },
      () => setError('No pude leer movimientos. Verifica permisos en Firebase.'),
    );

    const unsubscribeAseoInventory = onSnapshot(
      collection(db, 'productos_aseo'),
      { includeMetadataChanges: true },
      (snapshot) => {
        setAseoInventory(snapshot.docs.map(readAseoDoc));
        markSnapshotLoaded(snapshot.metadata.fromCache);
      },
      () => setError('No pude leer productos de ASEO. Verifica permisos en Firebase.'),
    );

    const unsubscribeTools = onSnapshot(
      collection(db, 'herramientas'),
      { includeMetadataChanges: true },
      (snapshot) => {
        setTools(snapshot.docs.map(readToolDoc));
        markSnapshotLoaded(snapshot.metadata.fromCache);
      },
      () => setError('No pude leer herramientas. Verifica permisos en Firebase.'),
    );

    const unsubscribeUsers = onSnapshot(
      collection(db, 'usuarios'),
      { includeMetadataChanges: true },
      (snapshot) => {
        const profiles = snapshot.docs.map(readUserDoc);
        const entries = profiles.flatMap((profile): Array<[string, UserProfile]> => (
          profile.email ? [[profile.id, profile], [profile.email, profile]] : [[profile.id, profile]]
        ));
        setUsers(Object.fromEntries(entries));
        markSnapshotLoaded(snapshot.metadata.fromCache);
      },
      () => setError('No pude leer usuarios. Verifica permisos en Firebase.'),
    );

    const unsubscribeValuations = onSnapshot(
      collection(db, 'valoraciones_inventario'),
      { includeMetadataChanges: true },
      (snapshot) => {
        const entries = snapshot.docs.map((valuationDoc): [string, number] => {
          const value = numberValue(valuationDoc.data(), 'valor_unitario');
          return [valuationDoc.id, Math.max(value, 0)];
        });
        setValuations(Object.fromEntries(entries));
        setValuationDocumentIds(new Set(snapshot.docs.map((valuationDoc) => valuationDoc.id)));
      },
      () => setError('No pude leer la valoración del inventario. Verifica permisos en Firebase.'),
    );

    return () => {
      unsubscribeInventory();
      unsubscribeAseoInventory();
      unsubscribeMovements();
      unsubscribeTools();
      unsubscribeUsers();
      unsubscribeValuations();
    };
  }, []);

  useEffect(() => {
    const updateOnline = () => setOnline(navigator.onLine);
    window.addEventListener('online', updateOnline);
    window.addEventListener('offline', updateOnline);
    updateOnline();

    return () => {
      window.removeEventListener('online', updateOnline);
      window.removeEventListener('offline', updateOnline);
    };
  }, []);

  useEffect(() => {
    const cache: PanelCache = {
      version: 1,
      inventory,
      aseoInventory,
      tools,
      movements,
      users,
      lastSync,
    };
    if (hasPanelCacheData(cache)) savePanelCache(cache);
  }, [aseoInventory, inventory, lastSync, movements, tools, users]);

  const inventoryTableRef = useRef<HTMLDivElement>(null);

  function resetModuleViewState() {
    setExitDateFrom('');
    setExitDateTo('');
    setExitCode('');
    setExitPerson('');
    setExitItem('');
    setExitFiltersOpen(false);
    setTallerSubmodulo('');
    setAgroquimicosUbicacion('');
    setManualStatuses({});
    setSearch('');
    setStatusOrder('default');
    setValuationEditItem(null);
    setEvidenceMovement(null);
    setShowOccupiedModal(false);
    setShowEntriesModal(false);
  }

  function selectModule(nextModule: string) {
    if (nextModule === module) return;
    resetModuleViewState();
    setModule(nextModule);
  }

  function selectTallerSubmodulo(nextSubmodulo: string) {
    setTallerSubmodulo(nextSubmodulo);
  }

  function selectAgroquimicosUbicacion(nextUbicacion: string) {
    setAgroquimicosUbicacion(nextUbicacion);
  }

  useEffect(() => {
    inventoryTableRef.current?.scrollTo({ top: 0 });
  }, [module, tallerSubmodulo, agroquimicosUbicacion]);

  function toolDocumentId(item: InventoryItem) {
    return item.id.startsWith('herramienta-') ? item.id.replace(/^herramienta-/, '') : undefined;
  }

  function statusValueForItem(item: InventoryItem) {
    return manualStatuses[item.id] ?? item.estado ?? '';
  }

  async function toggleTallerStatus(item: InventoryItem) {
    const current = statusValueForItem(item);
    const isMaintenance = normalize(current).includes('mant');
    const nextStatus = isMaintenance ? 'Bueno' : 'Mantenimiento';
    setManualStatuses((prev) => ({ ...prev, [item.id]: nextStatus }));

    const toolId = toolDocumentId(item);
    if (!toolId) return;

    try {
      await updateDoc(doc(db, 'herramientas', toolId), { estado: nextStatus });
    } catch (error) {
      console.error('No pude actualizar el estado del item Taller:', error);
    }
  }

  function updateValuationDraft(item: InventoryItem, value: string) {
    setValuationDrafts((prev) => ({ ...prev, [item.valuationId]: value }));
    setValuationSaveStates((prev) => {
      const next = { ...prev };
      delete next[item.valuationId];
      return next;
    });
  }

  function resetValuationDraft(item: InventoryItem) {
    setValuationDrafts((prev) => {
      const next = { ...prev };
      delete next[item.valuationId];
      return next;
    });
  }

  async function saveUnitValuation(item: InventoryItem): Promise<boolean> {
    const rawValue = valuationDrafts[item.valuationId];
    if (rawValue === undefined) return false;
    if (!rawValue.trim()) {
      resetValuationDraft(item);
      return false;
    }

    const unitValue = Number(rawValue.replace(',', '.'));
    if (!Number.isFinite(unitValue) || unitValue < 0) {
      setValuationSaveStates((prev) => ({ ...prev, [item.valuationId]: 'error' }));
      setError('El valor unitario debe ser un número igual o mayor que cero.');
      return false;
    }

    if (unitValue === (valuations[item.valuationId] ?? 0)) {
      resetValuationDraft(item);
      return true;
    }

    setValuationSaveStates((prev) => ({ ...prev, [item.valuationId]: 'saving' }));
    try {
      await setDoc(doc(db, 'valoraciones_inventario', item.valuationId), {
        valor_unitario: unitValue,
        modulo: item.modulo,
        codigo: item.codigo,
        descripcion: item.descripcion,
        actualizado_por: user.email || user.uid,
        actualizado_por_uid: user.uid,
        actualizado_en: serverTimestamp(),
        origen_actualizacion: 'manual',
      }, { merge: true });
      setValuations((prev) => ({ ...prev, [item.valuationId]: unitValue }));
      resetValuationDraft(item);
      setValuationSaveStates((prev) => ({ ...prev, [item.valuationId]: 'saved' }));
      setError('');
      return true;
    } catch (error) {
      console.error('No pude guardar la valoración unitaria:', error);
      setValuationSaveStates((prev) => ({ ...prev, [item.valuationId]: 'error' }));
      setError('No se pudo guardar el valor unitario. Verifica la conexión y los permisos de Firebase.');
      return false;
    }
  }

  function openValuationModal(item: InventoryItem) {
    updateValuationDraft(item, String(valuations[item.valuationId] ?? 0));
    setValuationEditItem(item);
  }

  function closeValuationModal() {
    if (!valuationEditItem || valuationSaveStates[valuationEditItem.valuationId] === 'saving') return;
    resetValuationDraft(valuationEditItem);
    setValuationEditItem(null);
  }

  async function saveValuationModal() {
    if (!valuationEditItem) return;
    const saved = await saveUnitValuation(valuationEditItem);
    if (saved) setValuationEditItem(null);
  }

  function renderValuationCells(item: InventoryItem) {
    const unitValue = valuations[item.valuationId] ?? 0;
    const inputValue = valuationDrafts[item.valuationId] ?? (unitValue > 0 ? String(unitValue) : '');
    const saveState = valuationSaveStates[item.valuationId];
    const saveLabel = saveState === 'saving'
      ? 'Guardando...'
      : saveState === 'saved'
        ? 'Guardado'
        : saveState === 'error'
          ? 'Revisa el valor'
          : '';
    const disabled = !online || usingTallerFallback;

    return (
      <>
        <td className="valuation-unit-cell">
          <label className={`valuation-input ${saveState ?? ''}`} title={disabled ? 'Conéctate a Firestore para editar la valoración.' : 'Se guarda al salir del campo o presionar Enter.'}>
            <span>$</span>
            <input
              aria-label={`Valor unitario de ${item.descripcion}`}
              type="number"
              min="0"
              step="1"
              inputMode="decimal"
              placeholder="0"
              value={inputValue}
              disabled={disabled || saveState === 'saving'}
              onChange={(event) => updateValuationDraft(item, event.target.value)}
              onBlur={() => saveUnitValuation(item)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') event.currentTarget.blur();
                if (event.key === 'Escape') {
                  event.preventDefault();
                  resetValuationDraft(item);
                }
              }}
            />
          </label>
          {saveLabel && <small className={`valuation-save-state ${saveState}`}>{saveLabel}</small>}
        </td>
        <td className="numeric valuation-total">{formatCurrency(unitValue * valuationQuantity(item))}</td>
      </>
    );
  }

  const isValuationModule = module === inventoryValuationModule;
  const isTallerModule = module === 'TALLER';
  const isAgroquimicosModule = module === 'Agroquimicos';
  const isLubricantesTallerModule = moduleMatches(module, 'Lubricantes taller');
  const agroquimicosInventoryBase = useMemo(
    () => inventory.filter((item) => moduleMatches(item.modulo, 'Agroquimicos')),
    [inventory],
  );
  const toolsInventory = useMemo(
    () => visibleToolInventory(tools, usingCachedData && tools.length === 0 && !online),
    [online, tools, usingCachedData],
  );
  const valuationInventory = useMemo(() => {
    const operationalInventory = inventory.filter((item) => (
      !moduleMatches(item.modulo, 'ASEO') && !moduleMatches(item.modulo, 'TALLER')
    ));
    return [...operationalInventory, ...aseoInventory, ...toolsInventory]
      .filter((item) => !(valuationModuleForItem(item) === 'TALLER' && retiredToolCodes.has(normalizeToolCode(item.codigo))))
      .sort((a, b) => (
        valuationModuleForItem(a).localeCompare(valuationModuleForItem(b))
        || compareCodes(a.codigo, b.codigo)
        || a.descripcion.localeCompare(b.descripcion)
      ));
  }, [aseoInventory, inventory, toolsInventory]);
  const valuationModuleOptions = useMemo(() => {
    const knownModules = new Set<string>(operationalModules);
    const additionalModules = valuationInventory
      .map(valuationModuleForItem)
      .filter((moduleName) => !knownModules.has(moduleName));
    return [...operationalModules, ...Array.from(new Set(additionalModules)).sort((a, b) => a.localeCompare(b))];
  }, [valuationInventory]);
  const valuationRows = useMemo<CurrentValuationRow[]>(() => valuationInventory.map((item) => {
    const moduleName = valuationModuleForItem(item);
    const quantity = valuationQuantity(item);
    const unitValue = valuations[item.valuationId] ?? 0;
    return {
      valuationId: item.valuationId,
      moduleName,
      code: item.codigoQr || item.codigo,
      product: item.descripcion,
      reference: item.referencia.trim() || 'N/A',
      quantity,
      unit: item.unidad,
      unitValue,
      totalValue: unitValue * quantity,
      includesOccupied: moduleName === 'TALLER',
    };
  }), [valuationInventory, valuations]);
  const usingTallerFallback = isTallerModule && tools.length === 0 && toolsInventory.length > 0;
  const moduleInventoryBase = useMemo(() => {
    const operationalInventory = inventory.filter((item) => !moduleMatches(item.modulo, 'ASEO'));
    const visibleTools = isTallerModule ? toolsInventory : [];
    const stockItems = isTallerModule
      ? visibleTools
      : [...operationalInventory, ...aseoInventory, ...visibleTools];
    const reconciledInventory = reconcileInventoryWithMovements(stockItems, movements);
    return reconciledInventory
      .filter((item) => moduleMatches(item.modulo, module))
      .filter((item) => !(isTallerModule && retiredToolCodes.has(normalizeToolCode(item.codigo))))
      .filter((item) => !isTallerModule || coincideSubmoduloTaller(item.categoria, tallerSubmodulo))
      .filter((item) => !isAgroquimicosModule || coincideUbicacionAgroquimicos(item.ubicacion ?? '', agroquimicosUbicacion));
  }, [agroquimicosUbicacion, aseoInventory, inventory, isAgroquimicosModule, isTallerModule, module, movements, tallerSubmodulo, toolsInventory]);

  const moduleInventory = useMemo(() => {
    const q = normalize(search);
    return moduleInventoryBase
      .filter((item) => {
        if (!q) return true;
        return normalize(`${item.codigo} ${item.codigoQr ?? ''} ${item.descripcion} ${item.referencia} ${item.categoria} ${item.subcategoria ?? ''} ${item.ubicacion ?? ''} ${item.caracteristica ?? ''} ${item.unidad}`).includes(q);
      })
      .sort((a, b) => {
        const codeOrder = compareCodes(a.codigo, b.codigo);
        if (codeOrder !== 0) return codeOrder;
        if (isTallerModule && statusOrder !== 'default') {
          const orderDiff = statusPriority(a, statusOrder) - statusPriority(b, statusOrder);
          if (orderDiff !== 0) return orderDiff;
        }
        if (isTallerModule) {
          return a.descripcion.localeCompare(b.descripcion)
            || compareCodes(a.codigo, b.codigo);
        }
        return a.descripcion.localeCompare(b.descripcion) || a.referencia.localeCompare(b.referencia);
      });
  }, [isTallerModule, moduleInventoryBase, search, statusOrder]);

  const moduleMovements = useMemo(() => {
    return movements
      .filter((movement) => moduleMatches(movement.modulo, module))
      .filter((movement) => !isTallerModule || movimientoPerteneceSubmoduloTaller(movement, tallerSubmodulo))
      .filter((movement) => !isAgroquimicosModule || movimientoPerteneceUbicacionAgroquimicos(movement, agroquimicosUbicacion))
      .sort(compareDate);
  }, [agroquimicosUbicacion, isAgroquimicosModule, isTallerModule, movements, module, tallerSubmodulo]);

  const occupiedUnitCards = useMemo(() => {
    if (!isTallerModule) return [];
    const occupiedTools = toolsInventory
      .filter((item) => !retiredToolCodes.has(normalizeToolCode(item.codigo)))
      .filter((item) => !tallerSubmodulo || coincideSubmoduloTaller(item.categoria, tallerSubmodulo))
      .filter((item) => (item.ocupados ?? 0) > 0);
    return expandTallerOccupiedUnits(occupiedTools, moduleMovements);
  }, [isTallerModule, moduleMovements, toolsInventory, tallerSubmodulo]);

  const occupiedGroups = useMemo(
    () => groupOccupiedBySubmodule(occupiedUnitCards),
    [occupiedUnitCards],
  );
  const totals = useMemo(
    () => buildTotals(moduleMovements, isTallerModule ? tallerSubmodulo : ''),
    [isTallerModule, moduleMovements, tallerSubmodulo],
  );
  const combustibleFuelStock = useMemo(
    () => (module === 'Combustible' ? combustibleStockByType(moduleInventoryBase) : null),
    [module, moduleInventoryBase],
  );
  const movementLimit = isTallerModule ? 100 : 24;
  const tallerMovementLabels = useMemo(
    () => (isTallerModule ? etiquetasMovimientoTaller(tallerSubmodulo) : null),
    [isTallerModule, tallerSubmodulo],
  );
  const isBodegaRojaView = isTallerModule && esBodegaRojaTaller(tallerSubmodulo);
  const allEntries = useMemo(
    () => moduleMovements.filter((movement) => movementIsEntry(movement, isTallerModule ? tallerSubmodulo : '')),
    [isTallerModule, moduleMovements, tallerSubmodulo],
  );
  const entryModalLimit = isTallerModule ? 200 : 120;
  const filteredMovementsForExport = useMemo(() => {
    const codeQuery = normalize(exitCode);
    const personQuery = normalize(exitPerson);
    const itemQuery = normalize(exitItem);

    return moduleMovements
      .filter((movement) => inDateRange(movement.fecha, exitDateFrom, exitDateTo))
      .filter((movement) => !codeQuery || normalize(movement.codigo).includes(codeQuery))
      .filter((movement) => !personQuery || normalize(movementPersonText(movement, users)).includes(personQuery))
      .filter((movement) => !itemQuery || normalize(movementItemText(movement)).includes(itemQuery));
  }, [exitCode, exitDateFrom, exitDateTo, exitItem, exitPerson, moduleMovements, users]);

  const filteredExits = useMemo(
    () => filteredMovementsForExport.filter((movement) => movementIsExit(movement, isTallerModule ? tallerSubmodulo : '')),
    [filteredMovementsForExport, isTallerModule, tallerSubmodulo],
  );

  async function exportarReporte() {
    if (!isDesktopApp) {
      setError('La exportación solo funciona en la aplicación de escritorio (.exe). Cierra el navegador y abre el ejecutable.');
      return;
    }

    if (moduleMovements.length === 0) {
      setError(`No hay movimientos para exportar en el módulo ${module}.`);
      return;
    }

    setExportando(true);
    setError('');
    try {
      const payload = crearReporteMovimientos({
        moduleName: module,
        tallerSubmodulo: isTallerModule ? tallerSubmodulo : '',
        movimientos: moduleMovements,
        usuarios: users,
        periodLabel: etiquetaPeriodoReporte(exitDateFrom, exitDateTo),
        exportDate: fechaExportacionReporte(),
        generatedBy: user.email || user.displayName || 'Usuario',
      });
      const result = await exportarReporteMovimientos(payload);
      if (result.canceled) return;
    } catch {
      setError('No se pudo generar el reporte Excel. Intenta de nuevo desde el ejecutable de escritorio.');
    } finally {
      setExportando(false);
    }
  }

  const exits = filteredExits.slice(0, movementLimit);
  const hasExitFilters = Boolean(exitDateFrom || exitDateTo || exitCode.trim() || exitPerson.trim() || exitItem.trim());
  const inventoryColumnCount = isTallerModule ? 10 : module === 'Agroquimicos' ? 11 : isLubricantesTallerModule ? 9 : 10;
  const totalSaldo = isTallerModule
    ? moduleInventory.reduce((sum, item) => sum + item.saldo, 0)
    : moduleInventory.reduce((sum, item) => sum + item.saldo, 0);
  const totalOcupados = isTallerModule
    ? moduleInventory.reduce((sum, item) => sum + (item.ocupados ?? 0), 0)
    : 0;
  const lowStock = isTallerModule
    ? moduleInventory.filter((item) => item.requiereQr || (item.ocupados ?? 0) > 0 || normalize(item.estado ?? '').includes('uso')).length
    : moduleInventory.filter((item) => item.saldo <= 3).length;
  const totalValuation = moduleInventory.reduce(
    (sum, item) => sum + (valuations[item.valuationId] ?? 0) * valuationQuantity(item),
    0,
  );
  const syncMode = !online ? 'offline' : usingCachedData ? 'cached' : 'online';
  const syncLabel = !online ? 'Sin internet' : usingCachedData ? 'Copia local' : 'En vivo';

  const activeAccent = moduleAccent(module);
  const moduleBlurb = moduleDescription(module);

  return (
    <main className={`app-shell ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <img src={logoSrc} alt={brandName} />
          <div className="brand-copy">
            <strong>{brandName}</strong>
            <span>Gestión de almacén</span>
          </div>
        </div>

        <nav className="module-list" aria-label="Módulos">
          {modules.map((entry) => (
            <button
              key={entry}
              type="button"
              data-module={entry}
              className={entry === module ? 'active' : ''}
              style={{ '--module-accent': moduleAccent(entry) } as CSSProperties}
              onClick={() => selectModule(entry)}
              title={entry}
            >
              <span className="module-icon-wrap">
                <img src={moduleIcon(entry)} alt="" aria-hidden="true" />
              </span>
              <span className="module-label">{entry}</span>
            </button>
          ))}
        </nav>

        <div className="session-card">
          <UserRound size={18} />
          <div className="session-copy">
            <span>Sesión</span>
            <strong>{user.email}</strong>
          </div>
        </div>

        <button className="logout-button" onClick={() => signOut(auth)}>
          <LogOut size={17} />
          <span>Salir</span>
        </button>
      </aside>

      <section className="workspace" data-module={module} style={{ '--module-accent': activeAccent } as CSSProperties}>
        <button
          type="button"
          className="sidebar-toggle"
          aria-label={sidebarCollapsed ? 'Expandir menú' : 'Colapsar menú'}
          onClick={() => setSidebarCollapsed((value) => !value)}
        >
          {sidebarCollapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
        </button>

        <section className="module-hero" aria-label={`Resumen del módulo ${module}`}>
          <div className="module-hero-icon">
            <img src={moduleIcon(module)} alt="" aria-hidden="true" />
          </div>
          <div className="module-hero-copy">
            <p className="eyebrow">{brandName} · Panel de escritorio</p>
            <h1>{module}</h1>
            <p className="module-hero-subtitle">{moduleBlurb}</p>
            <div className={`sync-status ${syncMode}`}>
              <span className="sync-dot" />
              <strong>{syncLabel}</strong>
              <span>Última sync: {formatSyncLabel(lastSync)}</span>
            </div>
          </div>
        </section>

        <header className="topbar">
          <div className="module-heading">
            <div>
              <p className="eyebrow">{isValuationModule ? 'Resumen general' : 'Consulta operativa'}</p>
              <h2 className="topbar-title">{isValuationModule ? inventoryValuationModule : 'Inventario y movimientos'}</h2>
            </div>
          </div>
          <div className={`toolbar ${isValuationModule ? 'valuation-toolbar' : ''}`}>
            {!isValuationModule && (
              <>
                <label className="search-box">
                  <Search size={18} />
                  <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar código, descripción, unidad o categoría" />
                </label>
                {isTallerModule && (
                  <label className="status-sort">
                    <span>Ordenar:</span>
                    <select value={statusOrder} onChange={(event) => setStatusOrder(event.target.value as StatusOrder)}>
                      <option value="default">Sin orden</option>
                      <option value="good-first">Buen estado primero</option>
                      <option value="maintenance-first">Mantenimiento primero</option>
                    </select>
                  </label>
                )}
                <button className="tool-button" type="button" onClick={() => setShowEntriesModal(true)}>
                  <ArrowDownLeft size={17} />
                  {tallerMovementLabels?.entradas ?? 'Entradas'} ({formatNumber(allEntries.length)})
                </button>
                {isTallerModule && (
                  <button className="tool-button" type="button" onClick={() => setShowOccupiedModal(true)}>
                    <AlertTriangle size={17} />
                    En uso ({formatNumber(occupiedUnitCards.length)})
                  </button>
                )}
                <button
                  className="tool-button"
                  type="button"
                  disabled={!isDesktopApp || exportando || moduleMovements.length === 0}
                  onClick={() => { void exportarReporte(); }}
                  title={isDesktopApp ? `Exportar movimientos del módulo ${module} (${nombreArchivoReporte(module)})` : 'Disponible solo en el ejecutable de escritorio'}
                >
                  <FileSpreadsheet size={17} />
                  {exportando ? 'Exportando...' : 'Exportar'}
                </button>
              </>
            )}
          </div>
        </header>

        {error && (
          <div className="alert-line">
            <AlertTriangle size={18} />
            {error}
          </div>
        )}

        {usingTallerFallback && (
          <div className="alert-line warning">
            <AlertTriangle size={18} />
            Sin conexión a Firestore: mostrando catálogo de respaldo de Herramientas Taller (49 ítems). Los demás submódulos no aparecen hasta reconectar.
          </div>
        )}

        {isTallerModule && tools.length === 0 && !usingTallerFallback && !loading && (
          <div className="alert-line warning">
            <AlertTriangle size={18} />
            No hay ítems en la colección herramientas de Firestore. Sincroniza el inventario desde la app Android o ejecuta el import JSON.
          </div>
        )}

        {isBodegaRojaView && tallerMovementLabels && (
          <div className="alert-line notice">
            <PackageCheck size={18} />
            {tallerMovementLabels.flujo}
          </div>
        )}

        {isValuationModule && (
          <InventoryValuationModule
            rows={valuationRows}
            moduleOptions={valuationModuleOptions}
            online={online}
            loading={loading}
            user={user}
            currentAverages={valuations}
            currentValuationIds={valuationDocumentIds}
            onEdit={(valuationId) => {
              const item = valuationInventory.find((entry) => entry.valuationId === valuationId);
              if (item) openValuationModal(item);
            }}
          />
        )}

        {combustibleFuelStock && (
          <section className="fuel-kpi-grid" aria-label="Stock de combustible por tipo">
            {FUEL_TYPES.map((tipo) => (
              <article key={tipo} className={`fuel-kpi-card balance-${balanceTone(combustibleFuelStock[tipo])}`}>
                <span>{tipo}</span>
                <strong>{formatNumber(combustibleFuelStock[tipo])}</strong>
                <small>galones en bodega</small>
              </article>
            ))}
          </section>
        )}

        {isTallerModule && (
          <div className="submodule-filter-row">
            <SubmoduleButtons
              submodules={TALLER_SUBMODULOS}
              selected={tallerSubmodulo}
              onSelect={selectTallerSubmodulo}
              ariaLabel="Submódulos de Taller"
              counts={Object.fromEntries(TALLER_SUBMODULOS.map((s) => {
                const items = toolsInventory.filter((item) => coincideSubmoduloTaller(item.categoria, s) && !retiredToolCodes.has(normalizeToolCode(item.codigo)));
                const total = items.length;
                if (esBodegaRojaTaller(s)) return [s, total];
                const occupied = items.filter((item) => (item.ocupados ?? 0) > 0).length;
                return [s, `${occupied}/${total}`];
              }))}
              allCount={toolsInventory.filter((item) => !retiredToolCodes.has(normalizeToolCode(item.codigo))).length}
            />
          </div>
        )}

        {isAgroquimicosModule && (
          <div className="submodule-filter-row">
            <SubmoduleButtons
              submodules={AGROQUIMICOS_UBICACIONES}
              selected={agroquimicosUbicacion}
              onSelect={selectAgroquimicosUbicacion}
              formatLabel={etiquetaUbicacionAgroquimicos}
              ariaLabel="Ubicaciones de agroquímicos"
              counts={Object.fromEntries(AGROQUIMICOS_UBICACIONES.map((ubicacion) => [
                ubicacion,
                agroquimicosInventoryBase.filter((item) => coincideUbicacionAgroquimicos(item.ubicacion ?? '', ubicacion)).length,
              ]))}
              allCount={agroquimicosInventoryBase.length}
            />
          </div>
        )}

        {!isValuationModule && (
        <section className="kpi-grid">
          <article>
            <PackageCheck size={20} />
            <span>Referencias</span>
            <strong>{moduleInventory.length}</strong>
          </article>
          <article>
            <ShieldCheck size={20} />
            <span>{isTallerModule ? 'Disponibles' : 'Saldo total'}</span>
            <strong>{formatNumber(totalSaldo)}</strong>
          </article>
          {isTallerModule ? (
            <button type="button" className="kpi-card kpi-clickable" onClick={() => setShowOccupiedModal(true)} title="Ver ítems en uso">
              <AlertTriangle size={20} />
              <span>En uso / alertas</span>
              <strong>{formatNumber(occupiedUnitCards.length)} / {lowStock}</strong>
            </button>
          ) : (
            <article>
              <AlertTriangle size={20} />
              <span>Alertas</span>
              <strong>{lowStock}</strong>
            </article>
          )}
          <article>
            <FileSpreadsheet size={20} />
            <span>Movimientos</span>
            <strong>{moduleMovements.length}</strong>
          </article>
          <article className="valuation-kpi">
            <CircleDollarSign size={20} />
            <span>Valor inventario</span>
            <strong>{formatCurrency(totalValuation)}</strong>
          </article>
        </section>
        )}

        {!isValuationModule && (
        <section className="dashboard-grid" key={`dashboard-${module}-${tallerSubmodulo || agroquimicosUbicacion || 'todos'}`}>
          <article className="panel inventory-panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Inventario actual</p>
                <h2>Control de saldos y valoración</h2>
              </div>
              <Eye size={20} />
            </div>

            <div className="table-wrap" ref={inventoryTableRef}>
              <table key={`${module}-${tallerSubmodulo || agroquimicosUbicacion || 'todos'}`} className={`inventory-table ${isTallerModule ? 'taller-table' : ''}`}>
                <thead>
                  <tr>
                    <th className="col-code">Código</th>
                    <th className="col-desc">Descripción</th>
                    {isTallerModule ? (
                      <>
                        <th className="numeric">Total</th>
                        <th className="numeric">Disponibles</th>
                        <th className="numeric">Ocupados</th>
                      </>
                    ) : module === 'Agroquimicos' ? (
                      <>
                        <th className="col-ref">Ubicación</th>
                        <th className="col-ref">Subcategoría</th>
                        <th className="numeric">Entradas</th>
                        <th className="numeric">Salidas</th>
                        <th className="numeric">Saldo</th>
                      </>
                    ) : isLubricantesTallerModule ? (
                      <>
                        <th className="numeric">Entradas</th>
                        <th className="numeric">Salidas</th>
                        <th className="numeric">Saldo</th>
                      </>
                    ) : (
                      <>
                        <th className="col-ref">{module === 'ASEO' ? 'Ubicación' : 'Referencia'}</th>
                        <th className="numeric">Entradas</th>
                        <th className="numeric">Salidas</th>
                        <th className="numeric">Saldo</th>
                      </>
                    )}
                    <th className="numeric col-valuation-unit">Valor unitario</th>
                    <th className="numeric col-valuation-total">Valor total</th>
                    <th className="col-unit">Unidad</th>
                    <th className="col-status">Estado</th>
                  </tr>
                </thead>
                <tbody key={`inventory-body-${module}-${tallerSubmodulo || agroquimicosUbicacion || 'todos'}`}>
                  {isTallerModule
                    ? moduleInventory.map((item) => {
                      const currentStatusValue = statusValueForItem(item);
                      const status = statusFor(item, currentStatusValue);
                      const nextStatus = normalize(currentStatusValue).includes('mant') ? 'Bueno' : 'Mantenimiento';
                      return (
                        <tr key={item.id}>
                          <td className="code col-code">{item.codigoQr ? item.codigoQr : item.codigo}</td>
                          <td className="col-desc">{item.descripcion}</td>
                          <td className="numeric">{formatNumber(item.total ?? item.saldo)}</td>
                          <td className={balanceClassName(item.saldo)}>{formatNumber(item.saldo)}</td>
                          <td className="numeric">{formatNumber(item.ocupados ?? 0)}</td>
                          {renderValuationCells(item)}
                          <td className="col-unit">{item.unidad}</td>
                          <td className="col-status">
                            <span className={`status ${status.className}`}>{status.label}</span>
                            <button
                              className="status-toggle-button"
                              type="button"
                              onClick={() => toggleTallerStatus(item)}
                              title={`Cambiar estado a ${nextStatus}`}
                            >
                              {`A ${nextStatus}`}
                            </button>
                          </td>
                        </tr>
                      );
                    })
                    : module === 'Agroquimicos'
                      ? moduleInventory.map((item) => {
                        const itemTotals = totalsForItem(item, totals);
                        const status = statusFor(item);
                        return (
                          <tr key={item.id}>
                            <td className="code col-code">{item.codigo}</td>
                            <td className="col-desc">{item.descripcion}</td>
                            <td className="col-ref">{item.ubicacion || 'Sin ubicación'}</td>
                            <td className="col-ref">{item.subcategoria || 'Sin subcategoría'}</td>
                            <td className="numeric">{formatNumber(itemTotals.entradas)}</td>
                            <td className="numeric">{formatNumber(itemTotals.salidas)}</td>
                            <td className={balanceClassName(item.saldo)}>{formatNumber(item.saldo)}</td>
                            {renderValuationCells(item)}
                            <td className="col-unit">{item.unidad}</td>
                            <td className="col-status"><span className={`status ${status.className}`}>{status.label}</span></td>
                          </tr>
                        );
                      }) : isLubricantesTallerModule
                      ? moduleInventory.map((item) => {
                        const itemTotals = totalsForItem(item, totals);
                        const status = statusFor(item);
                        return (
                          <tr key={item.id}>
                            <td className="code col-code">{item.codigo}</td>
                            <td className="col-desc">{item.descripcion}</td>
                            <td className="numeric">{formatNumber(itemTotals.entradas)}</td>
                            <td className="numeric">{formatNumber(itemTotals.salidas)}</td>
                            <td className={balanceClassName(item.saldo)}>{formatNumber(item.saldo)}</td>
                            {renderValuationCells(item)}
                            <td className="col-unit">{item.unidad}</td>
                            <td className="col-status"><span className={`status ${status.className}`}>{status.label}</span></td>
                          </tr>
                        );
                      })
                      : moduleInventory.map((item) => {
                        const itemTotals = totalsForItem(item, totals);
                        const status = statusFor(item);
                        return (
                          <tr key={item.id}>
                            <td className="code col-code">{item.codigo}</td>
                            <td className="col-desc">{item.descripcion}</td>
                            <td className="col-ref">{item.referencia}</td>
                            <td className="numeric">{formatNumber(itemTotals.entradas)}</td>
                            <td className="numeric">{formatNumber(itemTotals.salidas)}</td>
                            <td className={balanceClassName(item.saldo)}>{formatNumber(item.saldo)}</td>
                            {renderValuationCells(item)}
                            <td className="col-unit">{item.unidad}</td>
                            <td className="col-status"><span className={`status ${status.className}`}>{status.label}</span></td>
                          </tr>
                        );
                      })}
                  {!loading && moduleInventory.length === 0 && (
                    <tr>
                      <td colSpan={inventoryColumnCount} className="empty-cell">
                        <div className="empty-state">
                          <PackageCheck size={28} />
                          <strong>Sin referencias en este módulo</strong>
                          <span>Prueba otro filtro o sincroniza inventario desde la app Android.</span>
                        </div>
                      </td>
                    </tr>
                  )}
                  {loading && (
                    <tr>
                      <td colSpan={inventoryColumnCount} className="empty-cell">
                        <div className="loading-state">
                          <span className="loading-dot" />
                          <span>Cargando inventario desde Firestore...</span>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </article>

          <MovementPanel
            title={tallerMovementLabels?.salidas ?? 'Salidas'}
            icon={<ArrowUpRight size={19} />}
            movements={exits}
            tone="exit"
            showTallerDetails={isTallerModule}
            tallerSubmodulo={tallerSubmodulo}
            totalCount={filteredExits.length}
            limit={movementLimit}
            onEvidence={setEvidenceMovement}
            registeredBy={(movement) => userDisplayName(movement.responsableEntrega || movement.usuario, users)}
            emptyMessage={tallerMovementLabels?.salidasEmpty}
            controls={(
              <div className={`exit-filter-menu ${exitFiltersOpen ? 'open' : ''}`}>
                <div className="exit-filter-bar">
                  <button
                    className={`filter-toggle ${hasExitFilters ? 'active' : ''}`}
                    type="button"
                    aria-expanded={exitFiltersOpen}
                    onClick={() => setExitFiltersOpen((open) => !open)}
                  >
                    <SlidersHorizontal size={15} />
                    Filtros
                    <ChevronDown size={15} />
                  </button>
                  <span>{formatNumber(filteredExits.length)} {tallerMovementLabels?.salidasContador ?? 'salidas'}</span>
                </div>

                {exitFiltersOpen && (
                  <div className="exit-filters">
                    <label>
                      Desde
                      <input type="date" value={exitDateFrom} onChange={(event) => setExitDateFrom(event.target.value)} />
                    </label>
                    <label>
                      Hasta
                      <input type="date" value={exitDateTo} onChange={(event) => setExitDateTo(event.target.value)} />
                    </label>
                    <label className="wide">
                      Código
                      <input value={exitCode} onChange={(event) => setExitCode(event.target.value)} placeholder="Código interno o real" />
                    </label>
                    <label className="wide">
                      Persona
                      <input value={exitPerson} onChange={(event) => setExitPerson(event.target.value)} placeholder="Nombre o usuario" />
                    </label>
                    <label className="wide">
                      Item
                      <input value={exitItem} onChange={(event) => setExitItem(event.target.value)} placeholder="Código, nombre o talla" />
                    </label>
                    <div className="exit-filter-summary">
                      <button
                        type="button"
                        disabled={!hasExitFilters}
                        onClick={() => {
                          setExitDateFrom('');
                          setExitDateTo('');
                          setExitCode('');
                          setExitPerson('');
                          setExitItem('');
                        }}
                      >
                        <X size={14} />
                        Limpiar filtros
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          />
        </section>
        )}
      </section>
      {evidenceMovement && (
        <EvidenceModal
          movement={evidenceMovement}
          registeredBy={userDisplayName(evidenceMovement.usuario, users)}
          onClose={() => setEvidenceMovement(null)}
        />
      )}
      {showEntriesModal && (
        <EntriesModal
          module={module}
          movements={allEntries}
          totalCount={allEntries.length}
          limit={entryModalLimit}
          showTallerDetails={isTallerModule}
          tallerSubmodulo={tallerSubmodulo}
          entryTitle={tallerMovementLabels?.entradas ?? 'Entradas'}
          emptyMessage={tallerMovementLabels?.entradasEmpty}
          onEvidence={setEvidenceMovement}
          onClose={() => setShowEntriesModal(false)}
        />
      )}
      {showOccupiedModal && isTallerModule && (
        <OccupiedModal
          groups={occupiedGroups}
          totalCount={occupiedUnitCards.length}
          onClose={() => setShowOccupiedModal(false)}
        />
      )}
      {valuationEditItem && (
        <ValuationEditModal
          product={valuationEditItem.descripcion}
          code={valuationEditItem.codigoQr || valuationEditItem.codigo}
          unit={valuationEditItem.unidad}
          moduleName={valuationModuleForItem(valuationEditItem)}
          quantity={valuationQuantity(valuationEditItem)}
          value={valuationDrafts[valuationEditItem.valuationId] ?? String(valuations[valuationEditItem.valuationId] ?? 0)}
          saveState={valuationSaveStates[valuationEditItem.valuationId]}
          online={online}
          onChange={(value) => updateValuationDraft(valuationEditItem, value)}
          onSave={() => { void saveValuationModal(); }}
          onClose={closeValuationModal}
        />
      )}
      {/* Assign QR modal removed: items without code only show availability status */}
    </main>
  );
}

function movementDetailText(movement: Movement) {
  // For Combustible and other modules: show labor/frente as main detail
  const main = movement.labor || movement.frente || movement.cargo;
  if (!main) return '';
  // Only append cargo if it's different and useful
  if (movement.cargo && movement.cargo !== main) {
    return `${main} | ${movement.cargo}`;
  }
  return main;
}

function movementSubmoduleText(movement: Movement, tallerSubmodulo: string) {
  if (
    esBodegaRojaTaller(tallerSubmodulo) &&
    esTrasladoMovimiento(movement) &&
    esSalidaVistaTaller(movement, tallerSubmodulo)
  ) {
    return movement.submodulo ? `Destino: ${movement.submodulo}` : '';
  }
  if (movement.submodulo) return `Submódulo: ${movement.submodulo}`;
  if (movement.submoduloOrigen) return `Origen: ${movement.submoduloOrigen}`;
  if (movement.maquinaria) return `Submódulo: ${movement.maquinaria}`;
  return '';
}

function MovementList({
  movements,
  tone,
  onEvidence,
  registeredBy,
  showTallerDetails = false,
  tallerSubmodulo = '',
  emptyMessage,
}: {
  movements: Movement[];
  tone: 'entry' | 'exit';
  onEvidence?: (movement: Movement) => void;
  registeredBy?: (movement: Movement) => string;
  showTallerDetails?: boolean;
  tallerSubmodulo?: string;
  emptyMessage?: string;
}) {
  const defaultEmpty = tone === 'entry' ? 'Sin entradas registradas' : 'Sin salidas en este periodo';
  const defaultHint = tone === 'entry'
    ? 'Las entradas aparecerán aquí cuando se registren desde la app Android.'
    : 'Ajusta los filtros o espera nuevos movimientos de salida.';

  return (
    <div className="movement-list">
      {movements.map((movement) => (
        <div className="movement-row" key={movement.id}>
          <div>
            <strong>{movement.codigo || 'Sin código'}</strong>
            <span>{movement.descripcion}</span>
            {showTallerDetails && movement.tipo && <small>Tipo: {movement.tipo}</small>}
            {showTallerDetails && movementSubmoduleText(movement, tallerSubmodulo) && (
              <small>{movementSubmoduleText(movement, tallerSubmodulo)}</small>
            )}
            <small>{tone === 'entry' ? 'Registra' : 'Solicitante'}: {movement.solicitante || 'Sin responsable'}</small>

            {/* Horómetro y contexto operativo - especialmente importante para Combustible */}
            {movement.horometro && <small><strong>Horómetro:</strong> {movement.horometro}</small>}
            {(movement.labor || movement.frente) && (
              <small><strong>Labor/Frente:</strong> {[movement.labor, movement.frente].filter(Boolean).join(' / ')}</small>
            )}
            {movement.maquinaria && <small><strong>Maquinaria/Equipo:</strong> {movement.maquinaria}</small>}
            {movement.zona && !movement.frente && <small><strong>Zona:</strong> {movement.zona}</small>}
            {movementDetailText(movement) && !movement.labor && !movement.frente && (
              <small>Detalle: {movementDetailText(movement)}</small>
            )}

            {/* Debug visible en UI para Combustible cuando no se detecta horómetro/labor (temporal para descubrir los nombres de campos en Firestore) */}
            {movement.debugExtras && (
              <small style={{ color: '#c00', fontSize: '10px', background: '#fff3cd', padding: '1px 3px' }}>
                [DEBUG campos reales]: {movement.debugExtras}
              </small>
            )}

            {tone === 'exit' && <small className="movement-user">Registra: {registeredBy?.(movement) || movement.usuario || 'Sin usuario'}</small>}
          </div>
          <div className="movement-meta">
            <strong className="movement-qty">{formatNumber(movement.cantidad)}</strong>
            <span className="movement-qty">{movement.unidad || 'Unidad'}</span>
            <span>{movement.fecha || 'Sin fecha'}</span>
            <button
              className="evidence-button"
              type="button"
              title={movement.fotoUrl ? 'Ver evidencia' : 'Sin evidencia'}
              disabled={!movement.fotoUrl || !onEvidence}
              onClick={() => onEvidence?.(movement)}
            >
              <Camera size={14} />
              Evidencia
            </button>
          </div>
        </div>
      ))}
      {movements.length === 0 && (
        <div className="empty-list">
          <Inbox size={26} />
          <strong>{emptyMessage || defaultEmpty}</strong>
          <span>{emptyMessage ? 'Los movimientos aparecerán aquí cuando se registren desde la app Android.' : defaultHint}</span>
        </div>
      )}
    </div>
  );
}

function MovementPanel({
  title,
  icon,
  movements,
  tone,
  onEvidence,
  registeredBy,
  controls,
  showTallerDetails = false,
  tallerSubmodulo = '',
  emptyMessage,
  totalCount,
  limit,
}: {
  title: string;
  icon: ReactNode;
  movements: Movement[];
  tone: 'entry' | 'exit';
  onEvidence?: (movement: Movement) => void;
  registeredBy?: (movement: Movement) => string;
  controls?: ReactNode;
  showTallerDetails?: boolean;
  tallerSubmodulo?: string;
  emptyMessage?: string;
  totalCount?: number;
  limit?: number;
}) {
  const truncated = typeof totalCount === 'number' && typeof limit === 'number' && totalCount > limit;

  return (
    <article className={`panel movement-panel ${tone}`}>
      <div className="panel-header compact">
        <div>
          <p className="eyebrow">Actualizable</p>
          <h2>{title}</h2>
          {truncated && <small className="movement-truncated">Mostrando {limit} de {totalCount}</small>}
        </div>
        {icon}
      </div>
      {controls}
      <MovementList
        movements={movements}
        tone={tone}
        onEvidence={onEvidence}
        registeredBy={registeredBy}
        showTallerDetails={showTallerDetails}
        tallerSubmodulo={tallerSubmodulo}
        emptyMessage={emptyMessage}
      />
    </article>
  );
}

function EntriesModal({
  module,
  movements,
  totalCount,
  limit,
  showTallerDetails,
  tallerSubmodulo = '',
  entryTitle = 'Entradas',
  emptyMessage,
  onEvidence,
  onClose,
}: {
  module: string;
  movements: Movement[];
  totalCount: number;
  limit: number;
  showTallerDetails: boolean;
  tallerSubmodulo?: string;
  entryTitle?: string;
  emptyMessage?: string;
  onEvidence: (movement: Movement) => void;
  onClose: () => void;
}) {
  const visibleMovements = movements.slice(0, limit);
  const truncated = totalCount > limit;

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="entries-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`${entryTitle} de ${module}`}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="evidence-header">
          <div>
            <p className="eyebrow">{module} | Movimientos</p>
            <h2>{entryTitle} ({formatNumber(totalCount)})</h2>
            {truncated && <small className="movement-truncated">Mostrando {limit} de {totalCount}</small>}
          </div>
          <button className="icon-button" type="button" title="Cerrar" onClick={onClose}>
            <X size={18} />
          </button>
        </header>
        <div className="entries-modal-body">
          <MovementList
            movements={visibleMovements}
            tone="entry"
            onEvidence={onEvidence}
            showTallerDetails={showTallerDetails}
            tallerSubmodulo={tallerSubmodulo}
            emptyMessage={emptyMessage}
          />
        </div>
      </section>
    </div>
  );
}

function OccupiedModal({
  groups,
  totalCount,
  onClose,
}: {
  groups: OccupiedSubmoduleGroup[];
  totalCount: number;
  onClose: () => void;
}) {
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="occupied-modal"
        role="dialog"
        aria-modal="true"
        aria-label="Ítems en uso"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="evidence-header">
          <div>
            <p className="eyebrow">Taller | No disponibles</p>
            <h2>En uso ({formatNumber(totalCount)})</h2>
          </div>
          <button className="icon-button" type="button" title="Cerrar" onClick={onClose}>
            <X size={18} />
          </button>
        </header>

        <div className="occupied-modal-body">
          {groups.length === 0 && (
            <div className="occupied-empty">No hay ítems ocupados actualmente.</div>
          )}
          {groups.map((group) => (
            <section className="occupied-submodule-section" key={group.submodulo}>
              <header className="occupied-submodule-header">
                <h3>{group.submodulo}</h3>
                <span>{formatNumber(group.items.length)} en uso</span>
              </header>
              <div className="occupied-grid">
                {group.items.map((item) => (
                  <article className="occupied-card" key={item.id}>
                    <strong className="occupied-card-code">{item.codigo}</strong>
                    <span className="occupied-card-title">{item.descripcion}</span>
                    {item.subcategoria && <small>{item.subcategoria}</small>}
                    {item.caracteristica && <small>{item.caracteristica}</small>}
                    <small className="occupied-card-user">{item.solicitante}</small>
                    {item.unitTotal > 1 && (
                      <small className="occupied-card-unit">Unidad {item.unitIndex} de {item.unitTotal}</small>
                    )}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      </section>
    </div>
  );
}

function EvidenceModal({ movement, registeredBy, onClose }: { movement: Movement; registeredBy: string; onClose: () => void }) {
  const canPreview = canPreviewEvidence(movement.fotoUrl);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="evidence-modal" role="dialog" aria-modal="true" aria-label="Evidencia de salida" onClick={(event) => event.stopPropagation()}>
        <header className="evidence-header">
          <div>
            <p className="eyebrow">Evidencia</p>
            <h2>{movement.codigo || 'Salida'}</h2>
          </div>
          <button className="icon-button" type="button" title="Cerrar" onClick={onClose}>
            <X size={18} />
          </button>
        </header>

        <div className="evidence-body">
          {canPreview ? (
            <img src={movement.fotoUrl} alt={`Evidencia de ${movement.descripcion}`} referrerPolicy="no-referrer" />
          ) : (
            <div className="evidence-empty">
              <Camera size={30} />
              <span>Evidencia pendiente</span>
            </div>
          )}
        </div>

        <footer className="evidence-footer">
          <div>
            <strong>{movement.descripcion}</strong>
            <span>{movement.solicitante || movement.cargo || 'Sin responsable'} | {registeredBy || movement.usuario || 'Sin usuario'} | {movement.fecha || 'Sin fecha'}</span>
          </div>
          {canPreview && (
            <a className="evidence-link" href={movement.fotoUrl} target="_blank" rel="noreferrer">
              <ExternalLink size={15} />
              Abrir
            </a>
          )}
        </footer>
      </section>
    </div>
  );
}

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    return onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setChecking(false);
    });
  }, []);

  if (checking) {
    return <main className="loading-screen">Preparando panel...</main>;
  }

  if (!user) {
    return <LoginScreen onLogin={(email, password) => signInWithEmailAndPassword(auth, email, password).then(() => undefined)} />;
  }

  return <AppShell user={user} />;
}
