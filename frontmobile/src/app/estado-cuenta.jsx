import { useEffect, useRef, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
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

export default function EstadoCuenta() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { cuenta, ocupado, error, actualizar } = useEstadoCuenta()
  const { ultimoPago, guardarPago } = usePagos()
  const [vista, setVista] = useState('todos')
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
  const periodos = agruparPeriodos(filas)
  const anios = [...new Set(periodos.map((periodo) => periodo.anio).filter(Boolean))].sort().reverse()
  const visibles = periodos.filter((periodo) => (!anio || periodo.anio === anio)
    && (vista !== 'pendientes' || periodo.deuda > 0) && (vista !== 'pagos' || periodo.pagado > 0))
  const pendiente = ultimoPago?.estado === 'INICIADA' && !ultimoPago.descartado
  const avisoPago = errorPago ? mensajeErrorPago(errorPago) : null

  function irAlPago(pago) {
    router.push({ pathname: '/pagos/[id]', params: { id: String(pago.idTransaccion) } })
  }

  function actualizarCuenta() {
    setErrorPago(null)
    actualizar()
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
                <View style={estilos.tarjeta}>
                  <Text style={texto.pequeno}>Titular de la cuenta</Text>
                  <Text style={estilos.nombre}>{cuenta.contribuyente.nombre}</Text>
                  <Text style={texto.secundario}>Código {cuenta.contribuyente.codigo} · {cuenta.contribuyente.tipoDoc} {cuenta.contribuyente.doc}</Text>
                  <Text style={texto.pequeno}>Fecha de corte: {fecha(cuenta.fechaCorte)}</Text>
                  <View style={estilos.deuda}>
                    <Text style={texto.etiqueta}>Saldo pendiente total</Text>
                    <Text style={estilos.total}>{moneda(resumen.deuda)}</Text>
                  </View>
                  <View style={estilos.indicadores}>
                    <Indicador etiqueta="Emitido" valor={resumen.emitido} />
                    <Indicador etiqueta="Pagos aplicados" valor={resumen.pagado} />
                    <Indicador etiqueta="Deuda vencida" valor={resumen.vencido} />
                  </View>
                </View>
                {cuenta.desactualizado && (
                  <Aviso tipo="alerta" titulo="Última información disponible">
                    {`Se muestra una copia al ${fecha(cuenta.fechaCorte)}. Actualiza la cuenta antes de continuar con el pago.`}
                  </Aviso>
                )}
                {!cuenta.desactualizado && filas.length > 0 && resumen.deuda === 0 && (
                  <Aviso tipo="exito" titulo="No registras deuda pendiente">Todas las obligaciones de esta consulta están canceladas.</Aviso>
                )}
                {pendiente && (
                  <Aviso titulo="Tienes una operación pendiente">
                    {`Operación #${ultimoPago.idTransaccion}. Puedes retomarla para consultar su estado antes de confirmar.`}
                  </Aviso>
                )}
                {ultimoPago?.estado === 'CONFIRMADA' && (
                  <Aviso tipo="exito" titulo={`Pago registrado: ${ultimoPago.comprobante}`} accion={
                    <Boton titulo="Ver comprobante" variante="texto" icono="documento" onPress={() => irAlPago(ultimoPago)} />
                  }>{`Total pagado: ${moneda(ultimoPago.montoTotal)}.`}</Aviso>
                )}
                <View style={estilos.filtros}>
                  <Text style={texto.subtitulo} accessibilityRole="header">Detalle por periodo</Text>
                  <Filtro
                    etiqueta="Mostrar periodos"
                    valor={vista}
                    opciones={[
                      { valor: 'todos', nombre: 'Todos' },
                      { valor: 'pendientes', nombre: 'Con saldo' },
                      { valor: 'pagos', nombre: 'Con pagos' },
                    ]}
                    onCambiar={setVista}
                  />
                  {anios.length > 1 && <Filtro etiqueta="Año" valor={anio} opciones={[
                    { valor: '', nombre: 'Todos los años' }, ...anios.map((valor) => ({ valor, nombre: valor })),
                  ]} onCambiar={setAnio} />}
                  <Text style={texto.pequeno}>{visibles.length} periodos · Toca uno para ver sus conceptos.</Text>
                </View>
              </>
            )}
          </View>
        )}
        ListEmptyComponent={!ocupado && cuenta ? (
          <Aviso>{filas.length === 0 ? 'No tienes obligaciones registradas en esta cuenta.' : 'No hay periodos que coincidan con este filtro.'}</Aviso>
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

function Indicador({ etiqueta, valor }) {
  return <View style={estilos.indicador}><Text style={texto.pequeno}>{etiqueta}</Text><Text style={estilos.importe}>{moneda(valor)}</Text></View>
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
  tarjeta: { padding: 16, gap: 6, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, ...sombra },
  nombre: { fontFamily: fuentes.negrita, fontSize: 18, lineHeight: 26, color: colores.marinoOsc },
  deuda: { gap: 4, marginTop: 12 },
  total: { fontFamily: fuentes.negrita, fontSize: 34, lineHeight: 42, color: colores.marinoOsc, fontVariant: ['tabular-nums'] },
  indicadores: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, paddingTop: 12, marginTop: 6, borderTopWidth: 1, borderTopColor: colores.borde },
  indicador: { flexBasis: 100, flexGrow: 1, gap: 4 },
  importe: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc, fontVariant: ['tabular-nums'] },
  carga: { paddingVertical: 32, gap: 12, alignItems: 'center' },
  filtros: { gap: 10, paddingTop: 4 },
  opciones: { gap: 8, paddingVertical: 2 },
  opcion: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 16, paddingVertical: 10, borderRadius: radios.pill, borderWidth: 1, borderColor: colores.bordeFuerte, backgroundColor: colores.superficie },
  opcionActiva: { backgroundColor: colores.marino, borderColor: colores.marino },
  opcionPresionada: { opacity: 0.8 },
  opcionTexto: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 20, color: colores.marinoOsc },
  opcionTextoActivo: { color: colores.blanco },
  barra: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderTopColor: colores.borde, backgroundColor: colores.superficie },
  barraContenido: { gap: 10, width: '100%', maxWidth: 728, alignSelf: 'center' },
})
