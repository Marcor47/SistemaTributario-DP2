# Segmentación de contribuyentes con K-means (HU030, RF-043)

Agrupa a los contribuyentes por **cómo pagan**, sin reglas ni etiquetas propias (D-32). Corre dentro del backend, en
CPU, sobre el oro (`analitica`, solo seudónimos): no hay otro servicio ni GPU. El resultado queda en **borrador** y
sigue el flujo de HU031: la jefatura lo aprueba con `POST /api/segmentos/aprobaciones` y recién entonces se publica
como filtro del dashboard y de la plataforma analítica. No hay pantalla para calcular; solo API.

| Pieza | Dónde |
| --- | --- |
| Algoritmo (k-means++ y Lloyd, silueta) | `ia/KMeans.java` |
| Variables, elección de k y registro | `ia/SegmentacionModeloService.java` |
| API | `POST /api/segmentos/calculos` en `ia/SegmentoController.java` |
| Pruebas | `ia/KMeansTest.java`, `ia/SegmentacionModeloTest.java` |
| Exploración (gráficos, comparación de variantes) | `scripts/segmentacion_explorar.py` |

## Qué calcula

De la última carga del ETL con hechos (`ps_etl_cobranza`), por contribuyente con al menos una cuota vencida:

| Variable | Definición | Transformación |
| --- | --- | --- |
| `pctATiempo` | % de cuotas vencidas en los últimos 3 años pagadas a más tardar en su vencimiento | — |
| `diasAtraso` | promedio de (fecha de pago − vencimiento) en esas cuotas; las impagas cuentan hasta el corte. Negativo = paga antes | signo · ln(1 + \|x\|) |
| `deudaVencidaPct` | insoluto de las cuotas vencidas impagas sobre lo emitido en ellas, todos los años | ln(1 + x) |
| `antiguedadDeudaAnios` | años desde el vencimiento de la cuota impaga más antigua (0 si no debe) | ln(1 + x) |
| `mesesSinPago` | meses desde el último pago (120 si no hay pagos) | ln(1 + x) |

Después estandariza (media 0, desviación 1) y prueba K-means con k = 2 a 8 (20 reinicios, semilla fija). Elige el k
de **mejor silueta** entre los que dejan cada segmento con al menos 3 contribuyentes y el 2 % del total. Los segmentos
se numeran del que más paga a tiempo al que menos (`S1`, `S2`, …) y el nombre se arma con las dos variables en que
el segmento más se aleja del promedio (p. ej. `S1 · paga adelantado, sin deuda vencida`). La descripción y
`variablesCaracteristicas` traen las medianas en unidades reales, el centro estandarizado y, como dato descriptivo,
predios, peso del Impuesto Predial y pagos en línea.

Número de predios y peso del Impuesto Predial **no** entran al modelo: en la exploración empeoraron todas las
variantes (describen tamaño, no comportamiento). El segmento binario actual tampoco: solo se copia en cada asignación
para compararlo (hoy viene vacío de SAMNET).

## Registro y trazabilidad (RNF-014)

Cada corrida crea un `modelo_ia` (`kmeans-comportamiento-pago`, formato `JSON`) cuyo `hash_integridad` es el SHA-256
del modelo: medias, desviaciones, centros, transformación, parámetros y lote. La versión es `L<lote>-<hash>`. La
`traza_ia` (inalterable) guarda la entrada (lote, corte, contribuyentes, quién lo pidió) y la salida (el modelo, la
silueta de cada k y los tamaños).

- **Misma carga, mismos parámetros → mismo modelo:** responde `200` con los segmentos que ya existen, sin duplicar.
- **Corrida nueva:** borra los borradores anteriores de este modelo que nadie aprobó (la traza queda) y crea los
  nuevos (`201`). Lo aprobado, publicado o retirado no se toca.

**Perfil `local` (demostración):** al iniciar, `SegmentacionDemoLocal` calcula la segmentación y, si queda en
borrador, la aprueba como `gerencia` por el mismo camino de HU031. Así el filtro «Segmento» de la plataforma analítica,
el dashboard y el mapa muestran los segmentos K-means en cada máquina de desarrollo (los segmentos fijos de la semilla
`DEMO_HU011` quedan retirados). Se apaga con `tributario.segmentacion.publicar-al-iniciar: false`. En producción no
existe: la jefatura aprueba con `POST /api/segmentos/aprobaciones`.

## API

`POST /api/segmentos/calculos` (sin cuerpo; permiso `ETL:GESTIONAR`, rol Responsable de datos; token CSRF).
Responde el modelo, el lote, k, la silueta, la evaluación de cada k (`silueta`, `inercia`, `tamanoMinimo`, `valido`)
y los segmentos en borrador. Errores `422`: `SIN_CARGA_ETL`, `DATOS_INSUFICIENTES`, `SEGMENTACION_SIN_K_VALIDO`.

Configuración en `application.yml`, `tributario.segmentacion`: `k-minimo`, `k-maximo`, `tamano-minimo`,
`tamano-minimo-porcentaje`, `reinicios`, `semilla`, `ventana-anios`, `muestra-silueta` y `tras-etl`
(`SEGMENTACION_TRAS_ETL=true` recalcula el borrador después de cada lote que publique el backend; apagado por defecto).

## Mapa de segmentos (plataforma analítica)

Pestaña **Mapa de segmentos** en la plataforma analítica (`frontweb`, `components/reporteria/MapaSegmentos.jsx`),
visible con `SEGMENTOS:CONSULTAR`. No usa los filtros del cubo ni se guarda en las vistas.

- Cada contribuyente es un punto en el plano de las dos primeras **componentes principales** de sus cinco variables
  estandarizadas (se calculan con el modelo y se guardan en `asignacion_segmento.coord_x/coord_y`, V40), con el color
  de su segmento (paleta fija de 8, uno por segmento).
- **Etiquetas** arriba: una por segmento con su tamaño; al elegir una, las demás se atenúan.
- **Clic en un punto:** ficha con las cinco variables del contribuyente frente a la mediana de su segmento.
- **Búsqueda** por código, documento o parte del nombre (sin tildes): marca los resultados con un anillo, lleva el
  zoom al elegido y abre su ficha. Rueda para acercar, arrastrar para mover, «Ver todo» para volver.
- Con miles de puntos usa el modo de gran volumen de ECharts (canvas). Los puntos viajan como
  `[idAsignacion, segmento, x, y]`, sin seudónimos ni nombres.

**Datos personales (RNF-018):** el mapa no lleva nombres. El nombre de la ficha y la búsqueda solo existen para quien
tiene `CONTRIBUYENTES:CONSULTAR` (Gerencia y Analista de Rentas); cada ficha con nombre y cada búsqueda quedan en
`seguridad.registro_auditoria` como `REIDENTIFICAR`. Sin ese permiso la ficha muestra solo el seudónimo y los datos
de pago.

| Ruta (`SEGMENTOS:CONSULTAR`) | Respuesta |
| --- | --- |
| `GET /api/segmentos/mapa[?idModelo=]` | Modelo (estado, lote, corte, varianza explicada), variables, segmentos y puntos. Sin `idModelo`: la segmentación publicada o, si no hay, el último borrador. `404 MAPA_NO_DISPONIBLE` si no hay ninguna. |
| `GET /api/segmentos/mapa/asignaciones/{id}` | Ficha: valores, medianas del segmento y, con permiso, código y nombre. |
| `GET /api/segmentos/mapa/busqueda?q=` | Hasta 20 resultados; requiere además `CONTRIBUYENTES:CONSULTAR` (`403`), mínimo 3 caracteres. |

El Responsable de datos calcula la segmentación pero no ve el mapa (no tiene `SEGMENTOS:CONSULTAR`).

## Resultados con los datos de desarrollo

- **`local` (39 contribuyentes con historia, lote del 2026-11-04):** k = 7, silueta 0,75. Coincide con la exploración en
  Python (mismas asignaciones). Comparado con los perfiles de pago que la semilla asignó a cada contribuyente ficticio
  (el modelo no los ve), el índice de Rand ajustado es 0,86: cinco perfiles caen cada uno en su segmento y tres
  contribuyentes que pagaban a tiempo hasta 2025 y dejaron de pagar en 2026 forman un segmento propio.
- **Tests (7 contribuyentes):** k = 2.

Costo: con 39 contribuyentes, milisegundos. Con un distrito completo (~45 mil) K-means sigue en segundos en CPU; la
silueta se calcula sobre una muestra de 5000.

Pendiente: confirmar las variables con Rentas (RF-043 pide las mismas que los segmentos binarios actuales, que SAMNET
no entrega) y elegir k con los datos reales. Sin pantalla: la jefatura aprueba por API hasta que el front lo integre.
