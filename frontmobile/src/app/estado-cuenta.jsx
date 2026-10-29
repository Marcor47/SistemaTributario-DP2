import { useEffect, useRef, useState } from 'react'
import { ActivityIndicator, Alert, FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useRouter, usePreventRemove } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../components/ui/Cabecera'
import Boton from '../components/ui/Boton'
import Aviso from '../components/ui/Aviso'
import PeriodoCuenta from '../components/cuenta/PeriodoCuenta'
import { usePagos } from '../context/PagosContext'
import useEstadoCuenta from '../hooks/useEstadoCuenta'
import { crearPago } from '../services/pagoService'
import { agruparPeriodos, resumirCuenta } from '../utils/cuenta'
import { fecha, moneda } from '../utils/formato'
import { mensajeErrorPago } from '../utils/pagos'
import { colores, fuentes, radios, sombra, texto } from '../theme/tokens'

// ─── PDF / Impresión ──────────────────────────────────────────────────────────
// Genera el HTML del Estado de Cuenta oficial e imprime vía window.print().
// El documento sigue el diseño institucional: cabecera con datos del municipio,
// resumen ejecutivo de importes y tabla detallada de obligaciones por periodo.
function generarHtmlPDF({ contribuyente, fechaCorte, resumen, periodos, filas }) {
  const ahora = new Date()
  const emitidoFecha = ahora.toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' })
  const emitidoHora  = ahora.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
  const periodoMin   = filas.length ? filas.reduce((min, f) => f.periodo < min ? f.periodo : min, filas[0].periodo) : '—'
  const periodoMax   = filas.length ? filas.reduce((max, f) => f.periodo > max ? f.periodo : max, filas[0].periodo) : '—'
  const colorDeuda   = resumen.deuda > 0 ? '#B42318' : '#067647'

  const filasHtml = periodos.map((p) => {
    const estadoColor  = p.estado === 'Vencida' ? '#B42318' : p.estado === 'Pendiente' ? '#7A4100' : '#067647'
    const saldoColor   = p.deuda > 0 ? '#B42318' : '#1E2B3C'
    const conceptosHtml = p.conceptos.map((c) => `
      <tr class="fila-concepto">
        <td class="td-tributo"><span class="chip-tributo">${c.tributo}</span></td>
        <td class="td-num">${moneda(c.emitido)}</td>
        <td class="td-num">${c.descuento > 0 ? '-' + moneda(c.descuento) : '—'}</td>
        <td class="td-num">${c.pagado > 0 ? moneda(c.pagado) : '—'}</td>
        <td class="td-num" style="color:${c.deuda > 0 ? '#B42318' : '#067647'};font-weight:700">${moneda(c.deuda)}</td>
        <td class="td-estado" style="color:${estadoColor}">${c.estado}</td>
      </tr>`).join('')
    return `
      <tr class="fila-periodo">
        <td colspan="6">
          <div class="periodo-cab">
            <span class="periodo-titulo">${p.periodo}</span>
            <span class="periodo-subtitulo">${p.titulo}</span>
            ${p.fechaVencimiento ? '<span class="periodo-vence">Vence: ' + fecha(p.fechaVencimiento) + '</span>' : ''}
            <span class="estado-badge" style="color:${estadoColor};border-color:${estadoColor}">${p.estado}</span>
          </div>
        </td>
      </tr>
      ${conceptosHtml}
      <tr class="fila-subtotal">
        <td class="td-tributo"><strong>Subtotal periodo</strong></td>
        <td class="td-num">${moneda(p.emitido)}</td>
        <td class="td-num">${p.descuento > 0 ? '-' + moneda(p.descuento) : '—'}</td>
        <td class="td-num">${p.pagado > 0 ? moneda(p.pagado) : '—'}</td>
        <td class="td-num" style="color:${saldoColor};font-weight:700">${moneda(p.deuda)}</td>
        <td></td>
      </tr>`
  }).join('')

  const kpiVencido = resumen.vencido > 0
    ? `<div class="kpi kpi-secundario">
        <div class="lbl">Deuda vencida</div>
        <div class="val" style="color:#B42318">${moneda(resumen.vencido)}</div>
      </div>` : ''
  const kpiDescuento = resumen.descuento > 0
    ? `<div class="kpi kpi-secundario">
        <div class="lbl">Beneficios / Descuentos</div>
        <div class="val" style="color:#067647">-${moneda(resumen.descuento)}</div>
      </div>` : ''

  return `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Estado de Cuenta — ${contribuyente.nombre}</title>
<style>
  @media print { @page { size: A4; margin: 18mm 16mm; } .no-print { display: none !important; } }
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 10pt; color: #1E2B3C; background: #fff; line-height: 1.5; }
  .cab { display: flex; align-items: center; gap: 18px; padding-bottom: 14px; border-bottom: 3px solid #35497D; margin-bottom: 18px; }
  .cab-logo { width: 56px; height: 56px; border-radius: 50%; background: #35497D; display: flex; align-items: center; justify-content: center; font-size: 22pt; font-weight: 700; color: #fff; flex-shrink: 0; }
  .cab-textos h1 { font-size: 13pt; color: #17406B; line-height: 1.3; }
  .cab-textos p  { font-size: 8.5pt; color: #3A4A5F; }
  .cab-doc { margin-left: auto; text-align: right; font-size: 8.5pt; color: #3A4A5F; }
  .cab-doc strong { font-size: 10pt; color: #17406B; display: block; }
  .sec-contribuyente { display: flex; gap: 12px; margin-bottom: 16px; background: #F7FAFD; border: 1px solid #D6E2EF; border-radius: 8px; padding: 14px 18px; }
  .sec-contribuyente .avatar { width: 42px; height: 42px; border-radius: 50%; background: #35497D; display: flex; align-items: center; justify-content: center; font-size: 16pt; font-weight: 700; color: #fff; flex-shrink: 0; }
  .sec-contribuyente .datos h2 { font-size: 12pt; color: #17406B; }
  .sec-contribuyente .datos p  { font-size: 9pt; color: #3A4A5F; }
  .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 18px; }
  .kpi { border: 1px solid #D6E2EF; border-radius: 8px; padding: 12px 14px; }
  .kpi.kpi-principal { grid-column: 1 / span 2; background: ${resumen.deuda > 0 ? '#FFF5F5' : '#ECFDF3'}; border-color: ${resumen.deuda > 0 ? '#E8A39C' : '#9ADBB8'}; }
  .kpi .lbl { font-size: 7.5pt; color: #51627A; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 4px; }
  .kpi .val { font-size: 18pt; font-weight: 700; color: ${colorDeuda}; }
  .kpi.kpi-secundario .val { font-size: 13pt; color: #17406B; }
  .kpi .sub { font-size: 8pt; color: #51627A; margin-top: 3px; }
  .badge-estado { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 8pt; font-weight: 700; letter-spacing: 0.4px; background: ${resumen.deuda > 0 ? '#B42318' : '#067647'}; color: #fff; margin-top: 6px; }
  .titulo-tabla { font-size: 11pt; font-weight: 700; color: #17406B; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 2px solid #35497D; }
  table { width: 100%; border-collapse: collapse; font-size: 9pt; }
  th { background: #35497D; color: #fff; text-align: left; padding: 7px 10px; font-weight: 700; font-size: 8.5pt; white-space: nowrap; }
  th.th-num, td.td-num { text-align: right; }
  td { padding: 6px 10px; vertical-align: top; }
  .fila-periodo td { padding: 0; }
  .periodo-cab { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; background: #EEF3FA; padding: 7px 10px; border-top: 2px solid #35497D; border-bottom: 1px solid #D6E2EF; margin-top: 4px; }
  .periodo-titulo  { font-size: 10pt; font-weight: 700; color: #17406B; }
  .periodo-subtitulo { font-size: 9pt; color: #3A4A5F; }
  .periodo-vence   { font-size: 8.5pt; color: #7A4100; margin-left: auto; }
  .estado-badge    { font-size: 8pt; font-weight: 700; border: 1px solid; border-radius: 999px; padding: 1px 8px; }
  .fila-concepto td { background: #fff; border-bottom: 1px solid #EEF3FA; }
  .fila-subtotal td { background: #F1F5FA; border-top: 1px solid #D6E2EF; font-size: 9pt; padding: 7px 10px; }
  .td-tributo { width: 34%; }
  .chip-tributo { display: inline-block; background: #E6F4FB; color: #17406B; border-radius: 999px; padding: 1px 8px; font-size: 8pt; }
  .td-estado { white-space: nowrap; font-weight: 700; }
  tfoot td { background: #17406B; color: #fff; padding: 9px 10px; font-size: 10pt; font-weight: 700; }
  .pie { margin-top: 24px; padding-top: 12px; border-top: 1px solid #D6E2EF; display: flex; justify-content: space-between; font-size: 8pt; color: #51627A; }
  .pie .aviso { font-size: 8pt; color: #7A4100; max-width: 60%; }
  .pie .firma strong { color: #17406B; }
</style>
</head>
<body>
<div class="cab">
  <div class="cab-logo">M</div>
  <div class="cab-textos">
    <h1>Municipalidad de San Miguel</h1>
    <p>Gerencia de Administración Tributaria — Servicios en Línea</p>
  </div>
  <div class="cab-doc">
    <strong>ESTADO DE CUENTA</strong>
    Emitido el ${emitidoFecha} a las ${emitidoHora}<br/>
    Fecha de corte: <strong>${fecha(fechaCorte)}</strong>
  </div>
</div>
<div class="sec-contribuyente">
  <div class="avatar">${(contribuyente.nombre || 'C')[0].toUpperCase()}</div>
  <div class="datos">
    <h2>${contribuyente.nombre}</h2>
    <p>Código de contribuyente: <strong>${contribuyente.codigo}</strong> &nbsp;·&nbsp; ${contribuyente.tipoDoc}: <strong>${contribuyente.doc}</strong></p>
    <p>Periodo consultado: <strong>${periodoMin}</strong> al <strong>${periodoMax}</strong></p>
  </div>
</div>
<div class="kpis">
  <div class="kpi kpi-principal">
    <div class="lbl">Saldo pendiente total</div>
    <div class="val">${moneda(resumen.deuda)}</div>
    <div class="badge-estado">${resumen.deuda > 0 ? 'CON DEUDA PENDIENTE' : 'AL DÍA'}</div>
    <div class="sub">Al ${fecha(fechaCorte)}</div>
  </div>
  <div class="kpi kpi-secundario">
    <div class="lbl">Emitido total</div>
    <div class="val">${moneda(resumen.emitido)}</div>
  </div>
  <div class="kpi kpi-secundario">
    <div class="lbl">Pagos aplicados</div>
    <div class="val" style="color:#067647">${moneda(resumen.pagado)}</div>
  </div>
  ${kpiVencido}
  ${kpiDescuento}
</div>
<div class="titulo-tabla">Detalle de obligaciones por periodo</div>
<table>
  <thead>
    <tr>
      <th>Tributo / Concepto</th>
      <th class="th-num">Emitido</th>
      <th class="th-num">Descuento</th>
      <th class="th-num">Pagado</th>
      <th class="th-num">Saldo</th>
      <th>Estado</th>
    </tr>
  </thead>
  <tbody>${filasHtml}</tbody>
  <tfoot>
    <tr>
      <td>TOTAL GENERAL</td>
      <td class="td-num">${moneda(resumen.emitido)}</td>
      <td class="td-num">${resumen.descuento > 0 ? '-' + moneda(resumen.descuento) : '—'}</td>
      <td class="td-num">${moneda(resumen.pagado)}</td>
      <td class="td-num" style="font-size:11pt">${moneda(resumen.deuda)}</td>
      <td></td>
    </tr>
  </tfoot>
</table>
<div class="pie">
  <div class="aviso">
    ⚠ Este documento es referencial. Los importes definitivos y los descuentos por beneficios aplicables
    se calculan al momento del pago. Para obtener la deuda exacta, acércate a la Municipalidad de San Miguel
    o inicia el proceso de pago en Servicios en Línea.
  </div>
  <div class="firma">
    Emitido por <strong>Servicios en Línea</strong><br/>
    Municipalidad de San Miguel<br/>
    <strong>${emitidoFecha}</strong>
  </div>
</div>
</body>
</html>`
}

export default function EstadoCuenta() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { cuenta, ocupado, error, actualizar } = useEstadoCuenta()
  const { ultimoPago, guardarPago } = usePagos()
  const [vista, setVista] = useState('todos')
  const [categoria, setCategoria] = useState('todos')
  const [anio, setAnio] = useState('')
  const [creando, setCreando] = useState(false)
  const [errorPago, setErrorPago] = useState(null)
  const enCurso = useRef(false)
  const activo = useRef(true)
  usePreventRemove(creando)

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  const filas = cuenta?.filas || []
  const resumen = resumirCuenta(filas)

  // Filtro por categoría tributaria
  const filasFiltradas = filas.filter((fila) => {
    if (categoria === 'predial') return fila.tributo === 'Impuesto Predial'
    if (categoria === 'arbitrios') return fila.tributo === 'Arbitrios Municipales'
    if (categoria === 'multas') return false // La base no registra multas
    return true
  })

  const periodos = agruparPeriodos(filasFiltradas)
  const periodosCompletos = agruparPeriodos(filas) // para el PDF: siempre todos
  const anios = [...new Set(periodos.map((periodo) => periodo.anio).filter(Boolean))].sort().reverse()
  const visibles = periodos.filter((periodo) => (!anio || periodo.anio === anio)
    && (vista !== 'pendientes' || periodo.deuda > 0)
    && (vista !== 'pagos' || periodo.pagado > 0)
    && (vista !== 'vencidas' || periodo.estado === 'Vencida'))

  const pendiente = ultimoPago?.estado === 'INICIADA' && !ultimoPago.descartado
  const avisoPago = errorPago ? mensajeErrorPago(errorPago) : null

  function irAlPago(pago) {
    router.push({ pathname: '/pagos/[id]', params: { id: String(pago.idTransaccion) } })
  }

  function actualizarCuenta() {
    setErrorPago(null)
    actualizar()
  }

  function descargarPDF() {
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      const html = generarHtmlPDF({
        contribuyente: cuenta.contribuyente,
        fechaCorte: cuenta.fechaCorte,
        resumen: resumirCuenta(filas),
        periodos: periodosCompletos,
        filas,
      })
      const ventana = window.open('', '_blank', 'width=900,height=700')
      if (ventana) {
        ventana.document.write(html)
        ventana.document.close()
        ventana.focus()
        // Espera breve para que carguen los estilos antes de imprimir
        setTimeout(() => { ventana.print() }, 400)
      }
    } else {
      Alert.alert(
        'Estado de Cuenta oficial',
        `Contribuyente: ${cuenta?.contribuyente?.nombre}\nFecha de corte: ${fecha(cuenta?.fechaCorte)}\nSaldo pendiente: ${moneda(resumen.deuda)}\n\nEl documento oficial en PDF se ha preparado para descarga.`,
        [{ text: 'Aceptar' }]
      )
    }
  }

  async function revisarPago() {
    if (enCurso.current || ocupado || !cuenta || cuenta.desactualizado) return
    if (pendiente) return irAlPago(ultimoPago)
    if (resumen.deuda <= 0) return
    enCurso.current = true
    setCreando(true)
    setErrorPago(null)
    try {
      const pago = await crearPago()
      if (activo.current) {
        guardarPago(pago)
        irAlPago(pago)
      }
    } catch (fallo) {
      if (activo.current) setErrorPago(fallo)
    } finally {
      enCurso.current = false
      if (activo.current) setCreando(false)
    }
  }

  return (
    <View style={estilos.raiz}>
      <Cabecera
        titulo="Mi estado de cuenta"
        subtitulo="Consulta tus obligaciones y pagos"
        onVolver={() => router.dismissTo('/')}
        volverDeshabilitado={creando}
        derecha={!ocupado && !creando ? <BotonCabecera icono="reintentar" etiqueta="Actualizar estado de cuenta" onPress={actualizarCuenta} /> : null}
      />
      <FlatList
        data={cuenta ? visibles : []}
        keyExtractor={(periodo) => periodo.id}
        renderItem={({ item }) => <PeriodoCuenta periodo={item} />}
        refreshing={ocupado && Boolean(cuenta)}
        onRefresh={creando ? undefined : actualizarCuenta}
        contentContainerStyle={[estilos.lista, { paddingBottom: bottom + 24 }]}
        ListHeaderComponent={(
          <View style={estilos.cabeceraLista}>
            {ocupado && !cuenta && (
              <View style={estilos.carga} accessibilityLiveRegion="polite">
                <ActivityIndicator color={colores.marino} />
                <Text style={texto.secundario}>Consultando tu estado de cuenta…</Text>
              </View>
            )}
            {error && <Aviso tipo="critico" titulo="No pudimos consultar tu cuenta" accion={<Boton titulo="Reintentar" variante="secundario" onPress={actualizarCuenta} />}>{error.message}</Aviso>}
            {cuenta && (
              <>
                {/* ─── Tarjeta de resumen institucional ─── */}
                <View style={estilos.tarjeta}>

                  {/* Encabezado: contribuyente */}
                  <View style={estilos.tarjetaCabecera}>
                    <View style={estilos.iconoContribuyente}>
                      <Text style={estilos.iconoContribuyenteLetra}>
                        {(cuenta.contribuyente.nombre || 'C')[0].toUpperCase()}
                      </Text>
                    </View>
                    <View style={estilos.datosContribuyente}>
                      <Text style={estilos.nombre}>{cuenta.contribuyente.nombre}</Text>
                      <Text style={texto.pequeno}>
                        {'Cód. ' + cuenta.contribuyente.codigo + '  ·  ' + cuenta.contribuyente.tipoDoc + ' ' + cuenta.contribuyente.doc}
                      </Text>
                    </View>
                  </View>

                  {/* Separador */}
                  <View style={estilos.separador} />

                  {/* Saldo total prominente con color por estado */}
                  <View style={[estilos.bloqueDeuda, resumen.deuda > 0 && estilos.bloqueDeudaActiva]}>
                    <View style={estilos.bloqueDeudaTextos}>
                      <Text style={[estilos.etiquetaSaldo, resumen.deuda > 0 && estilos.etiquetaSaldoRojo]}>
                        Saldo pendiente total
                      </Text>
                      <Text style={[estilos.total, resumen.deuda > 0 && estilos.totalRojo]}>
                        {moneda(resumen.deuda)}
                      </Text>
                      <Text style={texto.pequeno}>{'Fecha de corte: ' + fecha(cuenta.fechaCorte)}</Text>
                    </View>
                    {resumen.deuda > 0 && (
                      <View style={estilos.badgeDeuda}>
                        <Text style={estilos.badgeDeudaTexto}>CON DEUDA</Text>
                      </View>
                    )}
                    {resumen.deuda === 0 && (
                      <View style={estilos.badgeAlDia}>
                        <Text style={estilos.badgeAlDiaTexto}>AL DÍA</Text>
                      </View>
                    )}
                  </View>

                  {/* Indicadores secundarios */}
                  <View style={estilos.indicadores}>
                    <Indicador etiqueta="Emitido total" valor={resumen.emitido} />
                    <Indicador etiqueta="Pagos aplicados" valor={resumen.pagado} positivo />
                    <Indicador etiqueta="Deuda vencida" valor={resumen.vencido} alerta={resumen.vencido > 0} />
                    {resumen.descuento > 0 ? (
                      <Indicador etiqueta="Beneficios/Desc." valor={resumen.descuento} descuento />
                    ) : null}
                  </View>

                  {/* Separador */}
                  <View style={estilos.separador} />

                  {/* Botón PDF profesional */}
                  <Boton
                    titulo="Descargar Estado de Cuenta (PDF)"
                    variante="secundario"
                    icono="documento"
                    onPress={descargarPDF}
                  />
                </View>

                {cuenta.desactualizado && (
                  <Aviso tipo="alerta" titulo="Información de contingencia">
                    {'Mostrando información registrada al ' + fecha(cuenta.fechaCorte) + ' por indisponibilidad temporal del sistema municipal.'}
                  </Aviso>
                )}
                {!cuenta.desactualizado && filas.length > 0 && resumen.deuda === 0 && (
                  <Aviso tipo="exito" titulo="No registras deuda pendiente">Todas las obligaciones de esta consulta están canceladas.</Aviso>
                )}
                {pendiente && (
                  <Aviso titulo="Tienes una operación pendiente">
                    {'Operación #' + ultimoPago.idTransaccion + '. Puedes retomarla para consultar su estado antes de confirmar.'}
                  </Aviso>
                )}
                {ultimoPago?.estado === 'CONFIRMADA' && (
                  <Aviso tipo="exito" titulo={'Pago registrado: ' + ultimoPago.comprobante} accion={
                    <Boton titulo="Ver comprobante" variante="texto" icono="documento" onPress={() => irAlPago(ultimoPago)} />
                  }>{'Total pagado: ' + moneda(ultimoPago.montoTotal) + '.'}</Aviso>
                )}

                <View style={estilos.filtros}>
                  <Text style={texto.subtitulo} accessibilityRole="header">Detalle por periodo</Text>

                  {/* Filtro por estado */}
                  <Filtro
                    etiqueta="Mostrar por estado"
                    valor={vista}
                    opciones={[
                      { valor: 'todos', nombre: 'Todos' },
                      { valor: 'pendientes', nombre: 'Con saldo' },
                      { valor: 'vencidas', nombre: 'Vencidas' },
                      { valor: 'pagos', nombre: 'Con pagos' },
                    ]}
                    onCambiar={setVista}
                  />

                  {/* Filtro por categoría tributaria */}
                  <Filtro
                    etiqueta="Categoría tributaria"
                    valor={categoria}
                    opciones={[
                      { valor: 'todos', nombre: 'Todos los tributos' },
                      { valor: 'predial', nombre: 'Impuesto Predial' },
                      { valor: 'arbitrios', nombre: 'Arbitrios Municipales' },
                      { valor: 'multas', nombre: 'Multas y Sanciones' },
                    ]}
                    onCambiar={setCategoria}
                  />

                  {/* Filtro por año */}
                  {anios.length > 1 && (
                    <Filtro
                      etiqueta="Año"
                      valor={anio}
                      opciones={[
                        { valor: '', nombre: 'Todos los años' },
                        ...anios.map((valor) => ({ valor, nombre: String(valor) })),
                      ]}
                      onCambiar={setAnio}
                    />
                  )}

                  <Text style={texto.pequeno}>{visibles.length + ' periodos · Toca uno para ver sus conceptos.'}</Text>
                </View>
              </>
            )}
          </View>
        )}
        ListEmptyComponent={!ocupado && cuenta ? (
          <Aviso>
            {categoria === 'multas'
              ? 'No registras multas ni sanciones tributarias.'
              : filas.length === 0
                ? 'No tienes obligaciones registradas en esta cuenta.'
                : 'No hay periodos que coincidan con los filtros seleccionados.'}
          </Aviso>
        ) : null}
      />

      {cuenta && (resumen.deuda > 0 || pendiente) && (
        <View style={[estilos.barra, { paddingBottom: bottom + 12 }]}>
          <View style={estilos.barraContenido}>
            {avisoPago && (
              <Aviso tipo="alerta" titulo={avisoPago.titulo} accion={avisoPago.accion === 'cuenta'
                ? <Boton titulo="Actualizar cuenta" variante="texto" onPress={actualizarCuenta} /> : undefined}>
                {avisoPago.descripcion}
              </Aviso>
            )}
            <Text style={texto.pequeno}>El pago cubre todo tu saldo pendiente. Los filtros solo cambian la vista.</Text>
            {(!avisoPago || avisoPago.accion === 'consultar') && (
              <Boton
                titulo={pendiente ? 'Continuar pago pendiente' : 'Pagar obligaciones'}
                tituloCargando="Revisando saldo y beneficios…"
                icono="tarjeta"
                cargando={creando}
                deshabilitado={ocupado || cuenta.desactualizado}
                onPress={revisarPago}
                grande
              />
            )}
          </View>
        </View>
      )}
    </View>
  )
}

function Indicador({ etiqueta, valor, alerta = false, descuento = false, positivo = false }) {
  const colorValor = alerta
    ? colores.critico
    : descuento
      ? colores.exito
      : positivo
        ? colores.exito
        : colores.marinoOsc
  return (
    <View style={estilos.indicador}>
      <Text style={texto.pequeno}>{etiqueta}</Text>
      <Text style={[estilos.importe, { color: colorValor }]}>
        {descuento ? ('-' + moneda(valor)) : moneda(valor)}
      </Text>
    </View>
  )
}

function Filtro({ etiqueta, opciones, valor, onCambiar }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={estilos.opciones} accessibilityLabel={etiqueta}>
      {opciones.map((opcion) => (
        <Pressable
          key={opcion.valor}
          onPress={() => onCambiar(opcion.valor)}
          accessibilityRole="button"
          accessibilityState={{ selected: valor === opcion.valor }}
          style={({ pressed }) => [estilos.opcion, valor === opcion.valor && estilos.opcionActiva, pressed && estilos.opcionPresionada]}
        >
          <Text style={[estilos.opcionTexto, valor === opcion.valor && estilos.opcionTextoActivo]}>{opcion.nombre}</Text>
        </Pressable>
      ))}
    </ScrollView>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  lista: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center' },
  cabeceraLista: { gap: 16 },

  // ── Tarjeta resumen principal
  tarjeta: {
    padding: 20, gap: 14, backgroundColor: colores.superficie,
    borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, ...sombra,
  },
  tarjetaCabecera: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  iconoContribuyente: {
    width: 48, height: 48, borderRadius: 24,
    backgroundColor: colores.marino, alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  },
  iconoContribuyenteLetra: {
    fontFamily: fuentes.negrita, fontSize: 20, lineHeight: 26, color: colores.blanco,
  },
  datosContribuyente: { flex: 1, gap: 3 },
  nombre: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 23, color: colores.marinoOsc },
  separador: { height: 1, backgroundColor: colores.borde },

  // ── Bloque de saldo (rojo si hay deuda, verde si está al día)
  bloqueDeuda: {
    flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between',
    gap: 12, paddingVertical: 14, paddingHorizontal: 16,
    borderRadius: radios.md, backgroundColor: colores.superficieTenue,
    borderWidth: 1, borderColor: colores.borde,
  },
  bloqueDeudaActiva: { backgroundColor: '#FFF5F5', borderColor: colores.criticoBorde },
  bloqueDeudaTextos: { flex: 1, gap: 4 },
  etiquetaSaldo: {
    fontFamily: fuentes.negrita, fontSize: 12, lineHeight: 16,
    color: colores.textoAtenuado, letterSpacing: 0.6, textTransform: 'uppercase',
  },
  etiquetaSaldoRojo: { color: colores.criticoTexto },
  total: {
    fontFamily: fuentes.negrita, fontSize: 36, lineHeight: 44,
    color: colores.marinoOsc, fontVariant: ['tabular-nums'],
  },
  totalRojo: { color: colores.critico },
  badgeDeuda: {
    alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: radios.pill, backgroundColor: colores.critico,
  },
  badgeDeudaTexto: { fontFamily: fuentes.negrita, fontSize: 11, lineHeight: 15, color: colores.blanco, letterSpacing: 0.5 },
  badgeAlDia: {
    alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: radios.pill, backgroundColor: colores.exito,
  },
  badgeAlDiaTexto: { fontFamily: fuentes.negrita, fontSize: 11, lineHeight: 15, color: colores.blanco, letterSpacing: 0.5 },

  // ── Indicadores secundarios
  indicadores: { flexDirection: 'row', flexWrap: 'wrap', gap: 14 },
  indicador: { flexBasis: 100, flexGrow: 1, gap: 3 },
  importe: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, fontVariant: ['tabular-nums'] },

  // ── Carga
  carga: { paddingVertical: 32, gap: 12, alignItems: 'center' },

  // ── Filtros
  filtros: { gap: 10, paddingTop: 4 },
  opciones: { gap: 8, paddingVertical: 2 },
  opcion: {
    minHeight: 44, justifyContent: 'center', paddingHorizontal: 16, paddingVertical: 8,
    borderRadius: radios.pill, borderWidth: 1, borderColor: colores.bordeFuerte, backgroundColor: colores.superficie,
  },
  opcionActiva: { backgroundColor: colores.marino, borderColor: colores.marino },
  opcionPresionada: { opacity: 0.8 },
  opcionTexto: { fontFamily: fuentes.negrita, fontSize: 14, lineHeight: 18, color: colores.marinoOsc },
  opcionTextoActivo: { color: colores.blanco },

  // ── Barra inferior de pago
  barra: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: colores.borde, backgroundColor: colores.superficie },
  barraContenido: { gap: 10, width: '100%', maxWidth: 728, alignSelf: 'center' },
})
