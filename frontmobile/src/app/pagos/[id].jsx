import { useEffect, useRef, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useLocalSearchParams, usePreventRemove, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../../components/ui/Cabecera'
import Boton from '../../components/ui/Boton'
import Aviso from '../../components/ui/Aviso'
import Icono from '../../components/ui/Icono'
import ResumenPago from '../../components/pagos/ResumenPago'
import ModalPagoConfirmado from '../../components/pagos/ModalPagoConfirmado'
import { usePagos } from '../../context/PagosContext'
import { confirmarYRecuperar, consultarPago } from '../../services/pagoService'
import { mensajeErrorPago } from '../../utils/pagos'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

const ESTADOS = { INICIADA: 'Pendiente de confirmación', CONFIRMADA: 'Confirmada', RECHAZADA: 'Rechazada', EXPIRADA: 'Expirada' }

export default function Pago() {
  const parametros = useLocalSearchParams()
  const id = Array.isArray(parametros.id) ? parametros.id[0] : parametros.id
  return <Operacion key={id} id={id} />
}

function Operacion({ id }) {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { guardarPago, descartarPago } = usePagos()
  const [resultado, setResultado] = useState({ pago: null, cargando: true, error: null })
  const [confirmando, setConfirmando] = useState(false)
  const [errorConfirmacion, setErrorConfirmacion] = useState(null)
  const [revision, setRevision] = useState(0)
  const [modal, setModal] = useState(false)
  const enCurso = useRef(false)
  const activo = useRef(true)
  usePreventRemove(confirmando)

  useEffect(() => {
    activo.current = true
    const controlador = new AbortController()
    consultarPago(id, { signal: controlador.signal })
      .then((pago) => {
        if (!controlador.signal.aborted) {
          guardarPago(pago)
          setResultado({ pago, cargando: false, error: null })
        }
      })
      .catch((error) => {
        if (!controlador.signal.aborted) setResultado({ pago: null, cargando: false, error })
      })
    return () => { activo.current = false; controlador.abort() }
  }, [id, revision, guardarPago])

  function consultarEstado() {
    if (enCurso.current) return
    setResultado({ pago: null, cargando: true, error: null })
    setErrorConfirmacion(null)
    setRevision((valor) => valor + 1)
  }

  async function confirmar() {
    if (enCurso.current || resultado.pago?.estado !== 'INICIADA' || resultado.pago?.dePrueba !== true
      || resultado.error || errorConfirmacion) return
    enCurso.current = true
    setConfirmando(true)
    setErrorConfirmacion(null)
    try {
      const recuperado = await confirmarYRecuperar(id)
      if (activo.current) {
        guardarPago(recuperado.pago)
        setResultado({ pago: recuperado.pago, cargando: false, error: null })
        setErrorConfirmacion(recuperado.error)
        if (recuperado.pago.estado === 'CONFIRMADA') setModal(true)
      }
    } catch (error) {
      if (activo.current) setErrorConfirmacion(error)
    } finally {
      enCurso.current = false
      if (activo.current) setConfirmando(false)
    }
  }

  const { pago, cargando } = resultado
  const error = resultado.error || errorConfirmacion
  const aviso = error ? mensajeErrorPago(error) : null
  const confirmado = pago?.estado === 'CONFIRMADA'
  const cerrado = pago && ['RECHAZADA', 'EXPIRADA'].includes(pago.estado)
  const puedeConfirmar = pago?.estado === 'INICIADA' && pago.dePrueba === true && !error

  function volverCuenta() {
    if (enCurso.current) return
    if (aviso?.accion === 'cuenta' || aviso?.accion === 'ventanilla') descartarPago(id)
    setModal(false)
    router.dismissTo('/estado-cuenta')
  }

  return (
    <View style={estilos.raiz}>
      <Cabecera titulo={confirmado ? 'Comprobante de pago' : 'Pago en línea'} subtitulo="Municipalidad de San Miguel" onVolver={volverCuenta} volverDeshabilitado={confirmando} />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
        {pago?.dePrueba && <View style={estilos.badge}><Text style={estilos.badgeTexto}>Simulación</Text></View>}
        <View style={estilos.tarjeta}>
          <Text style={texto.subtitulo} accessibilityRole="header">{confirmado ? 'Tu pago quedó registrado' : 'Revisa tu pago'}</Text>
          {cargando && <View style={estilos.carga}><ActivityIndicator color={colores.marino} /><Text style={texto.secundario}>Consultando la operación…</Text></View>}
          {aviso && (
            <Aviso tipo="alerta" titulo={aviso.titulo} accion={aviso.accion === 'consultar'
              ? <Boton titulo="Consultar estado de esta operación" variante="secundario" onPress={consultarEstado} deshabilitado={confirmando} /> : undefined}>
              {aviso.descripcion}
            </Aviso>
          )}
          {pago && (
            <>
              <View style={estilos.referencia}>
                <Text style={texto.pequeno}>Operación #{pago.idTransaccion}</Text>
                <Text style={[estilos.estado, confirmado && estilos.estadoConfirmado]}>{ESTADOS[pago.estado]}</Text>
              </View>
              {confirmado && (
                <View style={estilos.comprobante}>
                  <Icono nombre="check-circulo" color={colores.exito} tamano={28} />
                  <View style={estilos.comprobanteTexto}>
                    <Text style={texto.pequeno}>N.º de comprobante</Text>
                    <Text style={estilos.numero} selectable>{pago.comprobante}</Text>
                  </View>
                </View>
              )}
              <ResumenPago pago={pago} />
              <Text style={texto.secundario}>
                {confirmado ? `El pago incluyó ${pago.obligaciones.length} obligaciones.` : `El pago cubrirá el saldo completo de ${pago.obligaciones.length} obligaciones.`}
              </Text>
              {pago.montoDescuento > 0 && <Text style={texto.pequeno}>Se incluyen los beneficios verificados por la Municipalidad.</Text>}
              {confirmado && <Boton titulo="Ver resumen del pago" variante="secundario" icono="documento" onPress={() => setModal(true)} />}
              {cerrado && <Aviso tipo="alerta">Esta orden está cerrada. Consulta tu cuenta antes de iniciar otro pago.</Aviso>}
              {!confirmado && pago.dePrueba !== true && <Aviso tipo="alerta">La pasarela de esta operación no está disponible en la app.</Aviso>}
              {puedeConfirmar && <Boton
                titulo="Confirmar pago"
                tituloCargando="Verificando el pago…"
                icono="tarjeta"
                cargando={confirmando}
                onPress={confirmar}
                grande
              />}
              {confirmando && <Text style={texto.pequeno} accessibilityLiveRegion="polite">Espera mientras se verifica y registra el resultado de esta operación.</Text>}
            </>
          )}
        </View>
        <Boton titulo="Volver a mi estado de cuenta" icono="flecha-izquierda" variante={confirmado ? 'primario' : 'secundario'} onPress={volverCuenta} deshabilitado={confirmando} />
      </ScrollView>
      {confirmado && <ModalPagoConfirmado pago={pago} abierto={modal} onCerrar={() => setModal(false)} onVolver={volverCuenta} />}
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 16, width: '100%', maxWidth: 760, alignSelf: 'center' },
  tarjeta: { padding: 20, gap: 20, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, ...sombra },
  badge: { alignSelf: 'flex-end', paddingHorizontal: 12, paddingVertical: 4, borderRadius: radios.pill, backgroundColor: colores.advertenciaFondo },
  badgeTexto: { fontFamily: fuentes.negrita, fontSize: 13, lineHeight: 18, color: colores.advertencia },
  carga: { gap: 12, alignItems: 'center', paddingVertical: 24 },
  referencia: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  estado: { ...texto.pequeno, fontFamily: fuentes.negrita, color: colores.advertencia },
  estadoConfirmado: { color: colores.exito },
  comprobante: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, borderRadius: radios.md, backgroundColor: colores.exitoFondo },
  comprobanteTexto: { flex: 1, gap: 4 },
  numero: { fontFamily: fuentes.negrita, fontSize: 22, lineHeight: 28, color: colores.exito },
})
