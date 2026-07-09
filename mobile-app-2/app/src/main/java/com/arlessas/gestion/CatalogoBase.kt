package com.arlessas.gestion

/**
 * Catálogo base simplificado. 
 * Se han eliminado los productos específicos para evitar duplicados con Firestore.
 * Solo se mantienen las categorías y subcategorías estructurales.
 */
object CatalogoBase {
    val data = mapOf(
        "Dotación" to mapOf(
            "Parte Superior" to mapOf(
                "CAMISA POLO MANGA LARGA" to listOf("S", "M", "L", "XL", "XXL"),
                "CAMISA POLO MANGA LARGA COLOR HUESO" to listOf("L"),
                "CAMISA FORMAL MANGA LARGA BLANCA" to listOf("M", "L"),
                "CHAQUETA JEAN HOMBRE" to listOf("M", "L", "XL", "XXL"),
                "CHAQUETA JEAN DAMA" to listOf("S", "M", "L"),
            ),
            "Parte Inferior" to mapOf(
                "PANTALON JEAN HOMBRE" to listOf("28", "30", "32", "34", "36", "38", "40", "42"),
                "PANTALON JEAN DAMA" to listOf("6", "8", "10", "12", "14")
            ),
            "Conjunto" to mapOf(
                "CONJUNTO VESTUARIO DE COCINA" to listOf("M", "L", "XL")
            ),
            "Calzado" to mapOf(
                "BOTA MATERIAL CAÑA ALTA" to listOf("36", "37", "38", "39", "40", "41", "42", "43"),
                "ZAPATO PUNTA DE ACERO" to listOf("38", "39", "41"),
                "BOTA DE CAUCHO PUNTA DE ACERO" to listOf("36", "37", "38", "39", "40", "41", "43"),
                "ZAPATILLA ANTI DESLIZANTE" to listOf("36", "38", "39", "40")
            )
        ),
        "Combustible" to mapOf(
            "Combustibles" to mapOf(
                "Líquidos" to listOf("Gasolina", "ACPM", "Urea")
            )
        ),
        "Consumibles" to mapOf(
            "Oficina y Empaque" to mapOf(),
            "Mecánica y Rodamientos" to mapOf(),
            "Ferretería y Tornillería" to mapOf(),
            "Plomería y Riego" to mapOf(),
            "Filtros y Bombas" to mapOf(),
            "Aseo y Cafetería" to mapOf(),
            "Electricidad" to mapOf()
        ),
        "Químico" to mapOf(
            "Fertilizantes Químicos" to mapOf(
                "SULFEX MAGNESIO" to listOf("GRAMO"),
                "SULFEX MANGANESO" to listOf("GRAMO"),
                "82 -SULCAMAG *50KG" to listOf("GRAMO"),
                "COSMOQUEL CALCIO" to listOf("GRAMO"),
                "82 - FERTIQUEL Mg" to listOf("ML"),
                "82 - FERTIAMINO" to listOf("ML"),
                "82 - N300" to listOf("ML"),
                "82 - PRIMORDIAL PK" to listOf("ML"),
                "82 - ZINCNERGIA" to listOf("ML"),
                "82 - COMPLEX" to listOf("GRAMO"),
                "ACTIPHYL CRECIPHYL" to listOf("ML"),
                "82 - YARA GRADO PALMERO" to listOf("GRAMO"),
                "CIPLEX" to listOf("ML"),
                "COSMOQUEL MANGANESO" to listOf("GRAMO"),
                "INDUPLANT" to listOf("ML"),
                "COSMOQUEL MAGNESIO" to listOf("GRAMO"),
                "COSMOQUEL ZINC" to listOf("GRAMO"),
                "REBROTE" to listOf("GRAMO"),
                "ACTIPLANT" to listOf("ML"),
                "STIMPLEX" to listOf("ML"),
                "LUCTUS" to listOf("ML"),
                "MOLIB K" to listOf("GRAMO"),
                "98 - Nutriquel Menores" to listOf("ML"),
                "Suprazime" to listOf("ML"),
                "Microckel Calcio Boro" to listOf("ML"),
                "82 - FOLCAMAG" to listOf("ML"),
                "82 - VIOSURF PH" to listOf("ML"),
                "ACTIPHYL GREENMIX" to listOf("ML"),
                "EQUILIBRIO PENTAX SC" to listOf("GRAMO"),
                "Sulfato de Amonio" to listOf("GRAMO"),
                "PHYTOPLAN ZINC" to listOf("ML"),
                "UREA" to listOf("GRAMO"),
                "MICORRIZA" to listOf("GRAMO"),
                "90 - Sulfato de Zinc" to listOf("ML"),
                "90 - Sulfato de magnesio" to listOf("GRAMO"),
                "Hydroflex Inicio" to listOf("GRAMO"),
                "90 - BORO FOLIAR" to listOf("ML"),
                "NITRATO DE POTASIO" to listOf("GRAMO"),
                "BORO GRANULADO" to listOf("GRAMO"),
                "KELATEX CALCIO" to listOf("GRAMO"),
                "KELATEX MAGNESIO" to listOf("GRAMO"),
                "DAP GRANULADO" to listOf("GRAMO"),
                "CLORURO DE POTASIO" to listOf("GRAMO"),
                "ENGROSE" to listOf("ML"),
                "SULFEX COBRE" to listOf("GRAMO"),
                "PRECISAGRO" to listOf("GRAMO"),
                "CALCIO FOLIAR - Smart Calcio" to listOf("ML"),
                "NITRATO DE POTASIO - AMISOL" to listOf("GRAMO"),
                "90 - 11-30-10" to listOf("GRAMO")
            ),
            "Fertilizantes Orgánicos" to mapOf(
                "ZING ORGANIC" to listOf("GRAMO"),
                "98 - SUSTRATO VIVERO" to listOf("GRAMO"),
                "82 - INOCULANTE KAPTER" to listOf("ML")
            ),
            "Fungicidas e Insecticidas" to mapOf(
                "YODOSÁFER SL" to listOf("ML"),
                "DIPEL WG" to listOf("ML"),
                "L'EcoMix EW" to listOf("ML"),
                "ANTRACOL 70 WP" to listOf("GRAMO"),
                "KUMULUS DF" to listOf("GRAMO"),
                "98 - TACHIGAREN 30 SL" to listOf("ML"),
                "EXALT 60 SC" to listOf("ML"),
                "KARATE ZEON 5%" to listOf("ML"),
                "PREDOSTAR" to listOf("GRAMO"),
                "98 - AMISTAR TOP SC" to listOf("ML"),
                "98 - OXICRON 58.8%" to listOf("GRAMO"),
                "Nemacyl" to listOf("GRAMO"),
                "PREVALOR" to listOf("ML"),
                "HAWKER PLUS" to listOf("ML"),
                "ETHREL" to listOf("ML"),
                "ACIDO INDOLBUTILICO" to listOf("GRAMO"),
                "ACIDO GIBERELICO" to listOf("GRAMO"),
                "ANTAGEN" to listOf("ML"),
                "SOMMER" to listOf("ML"),
                "PROMALINA" to listOf("ML"),
                "PROGIBB" to listOf("GRAMO")
            ),
            "Coadyuvantes y Aditivos" to mapOf(
                "TRANSFER ADHEX" to listOf("ML"),
                "FLUYEX" to listOf("ML"),
                "TRANSFER IONIC" to listOf("ML"),
                "SILICONEX" to listOf("ML"),
                "HYDROSEP" to listOf("ML"),
                "ACIDIFICANTE LIQUIDO" to listOf("ML"),
                "VARSOL ARTEMISA" to listOf("ML"),
                "ALCOHOL ANTISEPTICO" to listOf("ML")
            ),
            "Lubricantes y Grasas" to mapOf(
                "ACEITE CELERITY 2T" to listOf("ML"),
                "ACEITE MOBIL SUPER 10W-30" to listOf("ML"),
                "ACEITE MOBIL SUPER 20W-50" to listOf("ML"),
                "ACEITE MOBIL 15W-40" to listOf("ML"),
                "LUBRICANTE STARMAX 80W90" to listOf("ML"),
                "ACEITE GEAR OIL DILVEG" to listOf("ML"),
                "ACEITE MOBIL 424" to listOf("ML"),
                "GRASA LUBRICANTE AUPRO" to listOf("GRAMO"),
                "GRASA MULTIBEG CHASIS" to listOf("GRAMO"),
                "GRASA MULTI BEG LITIO" to listOf("GRAMO"),
                "ACEITE MULTIUSOS ROYAL CONDOR" to listOf("ML"),
                "MULTIUSOS LUBRICANTE W40" to listOf("ML"),
                "ACEITE MULTIUSOS LICAVIR" to listOf("ML"),
                "MULTIUSOS LUBRICANTE AB-80" to listOf("ML"),
                "abro ab80" to listOf("UNIDAD")
            ),
            "Aseo y Taller" to mapOf(
                "QUITAGRASA JABON LIQUIDO AXION" to listOf("ML"),
                "QUITAGRASA JABON LIQUIDO MISTERMUSCULO" to listOf("ML"),
                "DESENGRASANTE INDUSTRIAL MIDIA" to listOf("ML"),
                "QUITAGRASA JABON LIQUIDO EASY OFF" to listOf("ML"),
                "JABON LIQUIDO CAPIBEL" to listOf("ML"),
                "CLOROX" to listOf("ML"),
                "FABULOSO" to listOf("ML"),
                "SAMPIC" to listOf("ML"),
                "solucion Vulcanizante centauro" to listOf("UNIDAD"),
                "soldamax pvc pavco wavir" to listOf("UNIDAD"),
                "vipal" to listOf("UNIDAD"),
                "masilla poliester rosada" to listOf("UNIDAD"),
                "soldadura liquida" to listOf("UNIDAD"),
                "limpiamax limpiador pavco" to listOf("UNIDAD"),
                "uni-80 unifer" to listOf("UNIDAD"),
                "uduke limpiacontacto" to listOf("UNIDAD"),
                "perfomax limpia contaxto" to listOf("UNIDAD"),
                "PINTURA ASFALTICA PERMOVARETO" to listOf("ML")
            )
        ),
        "EPP" to mapOf(
            "Protección Cabeza y Rostro" to mapOf(
                "COFIA" to listOf("Unidad"),
                "SOMBRERO TIPO SAFARY" to listOf("Unidad"),
                "CASCO ZAFARY" to listOf("Unidad"),
                "CASCO BLANCO (ALTURAS)" to listOf("Unidad"),
                "VISOR EN POLICARBONATO" to listOf("Unidad"),
                "VISOR MALLA GUADAÑA" to listOf("Unidad"),
                "CASQUETE PORTAVISOR" to listOf("Unidad")
            ),
            "Protección Visual" to mapOf(
                "GAFAS LENTE OSCURO" to listOf("Unidad"),
                "GAFAS LENTE CLARO" to listOf("Unidad"),
                "MONOGAFAS" to listOf("Unidad"),
                "CARETA PARA SOLDAR Y LENTE FOTOSENCIBLE" to listOf("Unidad")
            ),
            "Protección Respiratoria" to mapOf(
                "MASCARA RESPIRADOR MEDIA CARA" to listOf("Par"),
                "FILTROS PARA MASCARA 6003" to listOf("Par"),
                "FILTROS PARA MASCARA 2097" to listOf("Unidad"),
                "RESPIRADOR KN95" to listOf("Unidad"),
                "TAPABOCAS DESECHABLE" to listOf("Unidad"),
                "TAPABOCAS INDUSTRIAL NEGRO" to listOf("Unidad")
            ),
            "Protección Manual (Guantes)" to mapOf(
                "GUANTES DE NITRILO TL" to listOf("Par"),
                "GUANTE LARGO USO VETERINARIO" to listOf("Par"),
                "GUANTE NITRILO PUÑO ABIERTO" to listOf("Par"),
                "GUANTE INDUSTRIAL NITRILO CORTO" to listOf("Par"),
                "GUANTE INDUSTRIAL NITRILO LARGO" to listOf("Par"),
                "GUANTE NITRILO DOMESTICO" to listOf("Par"),
                "GUANTES DE VAQUETA CORTO" to listOf("Par"),
                "GUANTE ANTIIMPACTO" to listOf("Par"),
                "GUANTE ANTICORTE" to listOf("Par"),
                "GUANTE KIM- NITRILO ARENADO" to listOf("Par"),
                "GUANTE PARA SOLDAR" to listOf("Unidad"),
                "GUANTE DE MANIPULACION DE ALIMENTOS" to listOf("Par")
            ),
            "Protección Auditiva" to mapOf(
                "PROTECTOR AUDITIVO INSERCIÓN" to listOf("Unidad"),
                "PROTECTOR AUDITIVO COPA" to listOf("Unidad")
            ),
            "Cuerpo y Extremidades" to mapOf(
                "CANILLERA CORTA" to listOf("Par"),
                "MANGAS EN CARNAZA" to listOf("Par"),
                "POLAINA EN CARNAZA" to listOf("Unidad"),
                "DELANTAL DE CARNAZA" to listOf("Unidad"),
                "DELANTAL PARA USO INDUSTRIAL" to listOf("Unidad"),
                "DELANTAL GUADAÑADOR" to listOf("Unidad"),
                "OVEROL ANTIFUIDO INDUSTRIAL" to listOf("Unidad"),
                "OVEROL DESCARTABLE" to listOf("Unidad"),
                "OVEROL DRIL PARA APICULTURA" to listOf("Unidad"),
                "OVEROL DRIL TALLER" to listOf("Unidad"),
                "ARNES DE GUADAÑA" to listOf("Unidad"),
                "IMPERMEABLE" to listOf("Unidad")
            )
        )
    )
}
