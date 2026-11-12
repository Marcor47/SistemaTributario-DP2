import { useEffect, useMemo, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useLocalSearchParams, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import { cancelarReclamo, listarMisReclamos } from '../../services/reclamoService'
import { ESTADOS_RECLAMO, fechaHoraVisible, TIPOS_RECLAMO } from '../../utils/reclamos'
import { colores, radios, texto } from '../../theme/tokens'

export default function DetalleReclamo() {
  const { id } = useLocalSearchParams()
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [estado, setEstado] = useState({ cargando: true, reclamo: null, error: null })
  const [confirmando, setConfirmando] = useState(false)
  const [cancelando, setCancelando] = useState(false)
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controlador = new AbortController()
    setEstado({ cargando: true, reclamo: null, error: null })
    listarMisReclamos({ signal: controlador.signal }).then((reclamos) => {
      const reclamo = reclamos.find((item) => String(item.idReclamo) === String(id))
      if (!controlador.signal.aborted) setEstado(reclamo ? { cargando: false, reclamo, error: null } : { cargando: false, reclamo: null, error: 'El reclamo no existe o no pertenece a tu cuenta.' })
    }).catch((error) => { if (!controlador.signal.aborted) setEstado({ cargando: false, reclamo: null, error: error.message }) })
    return () => controlador.abort()
  }, [id, revision])
  const reclamo = estado.reclamo
  const estadoVisible = useMemo(() => reclamo ? ESTADOS_RECLAMO[reclamo.estado] || { etiqueta: reclamo.estado } : null, [reclamo])
  async function cancelar() {
    setCancelando(true)
    try { await cancelarReclamo(id); setConfirmando(false); setRevision((valor) => valor + 1) }
    catch (error) { setEstado((actual) => ({ ...actual, error: error.message })) }
    finally { setCancelando(false) }
  }
  return <View style={estilos.raiz}><Cabecera titulo="Detalle del reclamo" onVolver={() => router.back()} />
    <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
      {estado.cargando ? <ActivityIndicator color={colores.marino} /> : null}
      {estado.error ? <Aviso tipo="alerta" titulo="No se pudo consultar el reclamo" accion={<Boton titulo="Reintentar" onPress={() => setRevision((valor) => valor + 1)} />}>{estado.error}</Aviso> : null}
      {reclamo ? <View style={estilos.tarjeta}>
        <Text style={texto.pequeno}>{TIPOS_RECLAMO[reclamo.tipoReclamo] || reclamo.tipoReclamo}</Text><Text style={texto.titulo}>{reclamo.numeroReclamo}</Text>
        <Aviso titulo={`Estado: ${estadoVisible.etiqueta}`}>{reclamo.estado === 'APROBADO_CON_RESPUESTA' ? 'La respuesta oficial ya está disponible.' : reclamo.estado === 'CANCELADO' ? 'Este reclamo fue cancelado.' : 'La Municipalidad continúa procesando tu reclamo.'}</Aviso>
        <Dato etiqueta="Registrado" valor={fechaHoraVisible(reclamo.fechaRegistro)} /><Dato etiqueta="Incidente" valor={fechaHoraVisible(reclamo.fechaHoraIncidente)} />
        {reclamo.referencia ? <Dato etiqueta="Referencia" valor={reclamo.referencia} /> : null}<Dato etiqueta="Comentario" valor={reclamo.comentario} />
        <Text style={texto.subtitulo}>Respuesta</Text>{reclamo.respuesta ? <Aviso tipo="exito">{reclamo.respuesta}{reclamo.fechaRespuesta ? `\nRespondido: ${fechaHoraVisible(reclamo.fechaRespuesta)}` : ''}</Aviso> : <Text style={texto.secundario}>Aún no hay una respuesta oficial.</Text>}
        {(reclamo.estado === 'ENVIADO' || reclamo.estado === 'EN_REVISION') && (!confirmando ? <Boton titulo="Cancelar reclamo" variante="peligro" onPress={() => setConfirmando(true)} /> : <Aviso tipo="alerta" titulo="¿Cancelar este reclamo?" accion={<View style={estilos.acciones}><Boton titulo="Sí, cancelar" variante="peligro" cargando={cancelando} onPress={cancelar} /><Boton titulo="Volver" variante="secundario" deshabilitado={cancelando} onPress={() => setConfirmando(false)} /></View>}>Después de cancelarlo ya no podrá ser atendido.</Aviso>)}
      </View> : null}
    </ScrollView>
  </View>
}

function Dato({ etiqueta, valor }) { return <View style={estilos.dato}><Text style={texto.etiqueta}>{etiqueta}</Text><Text selectable style={texto.cuerpo}>{valor}</Text></View> }
const estilos = StyleSheet.create({ raiz: { flex: 1, backgroundColor: colores.fondo }, contenido: { padding: 16, gap: 16, width: '100%', maxWidth: 760, alignSelf: 'center' }, tarjeta: { padding: 20, gap: 18, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg }, dato: { gap: 4, borderBottomWidth: 1, borderBottomColor: colores.borde, paddingBottom: 12 }, acciones: { gap: 8, marginTop: 12 } })
