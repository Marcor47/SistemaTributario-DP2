import { useCallback, useMemo, useRef, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import CampoTexto from '../../components/ui/CampoTexto'
import Paginacion from '../../components/ui/Paginacion'
import Icono from '../../components/ui/Icono'
import { listarMisReclamos, registrarReclamo } from '../../services/reclamoService'
import { ESTADOS_RECLAMO, fechaHoraLocal, fechaHoraVisible, ordenarReclamos, TIPOS_RECLAMO, validarReclamo } from '../../utils/reclamos'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

const TAMANIO = 10
const NUEVO = () => ({ tipoReclamo: '', referencia: '', fechaHoraIncidente: fechaHoraLocal(), comentario: '' })

export default function Reclamos() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const lista = useRef(null)
  const [reclamos, setReclamos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [formulario, setFormulario] = useState(false)
  const [datos, setDatos] = useState(NUEVO)
  const [errores, setErrores] = useState({})
  const [enviando, setEnviando] = useState(false)
  const [confirmacion, setConfirmacion] = useState('')
  const [revision, setRevision] = useState(0)

  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    setCargando(true); setError(null)
    listarMisReclamos({ signal: controlador.signal }).then((respuesta) => {
      if (!controlador.signal.aborted) { setReclamos(ordenarReclamos(respuesta)); setCargando(false) }
    }).catch((e) => { if (!controlador.signal.aborted) { setError(e.message); setCargando(false) } })
    return () => controlador.abort()
  // La revisión fuerza una nueva consulta aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision]))

  const totalPaginas = Math.max(1, Math.ceil(reclamos.length / TAMANIO))
  const visibles = useMemo(() => reclamos.slice(pagina * TAMANIO, (pagina + 1) * TAMANIO), [reclamos, pagina])
  function cambiar(campo, valor) { setDatos((actual) => ({ ...actual, [campo]: valor })); setErrores((actual) => ({ ...actual, [campo]: undefined })) }
  async function enviar() {
    const encontrados = validarReclamo(datos)
    setErrores(encontrados); setConfirmacion('')
    if (Object.keys(encontrados).length) return
    setEnviando(true)
    try {
      const creado = await registrarReclamo(datos)
      setDatos(NUEVO()); setFormulario(false); setPagina(0)
      setConfirmacion(`Reclamo ${creado.numeroReclamo} enviado correctamente.`)
      setRevision((valor) => valor + 1)
    } catch (e) { setErrores(e.errores || { general: e.message }) }
    finally { setEnviando(false) }
  }

  const encabezado = <View style={estilos.encabezadoLista}>
    {confirmacion ? <Aviso tipo="exito" titulo="Reclamo registrado">{confirmacion}</Aviso> : null}
    {formulario ? <View style={estilos.formulario}>
      <View style={estilos.formularioCabecera}><View style={estilos.flex}><Text style={texto.subtitulo}>Registrar reclamo</Text><Text style={texto.secundario}>Describe el incidente. La Municipalidad comunicará el estado y la respuesta, sin mostrar derivaciones internas.</Text></View>
        <Pressable accessibilityRole="button" accessibilityLabel="Cerrar formulario" onPress={() => setFormulario(false)} style={estilos.iconoCerrar}><Icono nombre="cerrar" /></Pressable></View>
      <Text style={texto.etiqueta}>Tipo de reclamo</Text>
      <View style={estilos.opciones}>{Object.entries(TIPOS_RECLAMO).map(([valor, etiqueta]) => <Pressable key={valor} accessibilityRole="radio" accessibilityState={{ checked: datos.tipoReclamo === valor }} onPress={() => cambiar('tipoReclamo', valor)} style={[estilos.opcion, datos.tipoReclamo === valor && estilos.opcionActiva]}><Text style={[estilos.opcionTexto, datos.tipoReclamo === valor && estilos.opcionTextoActivo]}>{etiqueta}</Text></Pressable>)}</View>
      {errores.tipoReclamo ? <Text style={estilos.error}>{errores.tipoReclamo}</Text> : null}
      <CampoTexto etiqueta="Fecha y hora del incidente" ayuda="Formato: AAAA-MM-DDTHH:mm" value={datos.fechaHoraIncidente} onChangeText={(valor) => cambiar('fechaHoraIncidente', valor)} error={errores.fechaHoraIncidente} autoCapitalize="none" />
      <CampoTexto etiqueta="Referencia opcional" ayuda={`${datos.referencia.length}/150 caracteres`} value={datos.referencia} onChangeText={(valor) => cambiar('referencia', valor)} error={errores.referencia} maxLength={150} />
      <CampoTexto etiqueta="Comentario" ayuda={`${datos.comentario.length}/2000 caracteres`} value={datos.comentario} onChangeText={(valor) => cambiar('comentario', valor)} error={errores.comentario} maxLength={2000} multiline numberOfLines={5} textAlignVertical="top" />
      {errores.general ? <Aviso tipo="alerta">{errores.general}</Aviso> : null}
      <Boton titulo="Enviar reclamo" tituloCargando="Enviando reclamo" cargando={enviando} onPress={enviar} grande />
    </View> : <View style={estilos.resumen}><View style={estilos.flex}><Text style={texto.subtitulo}>Mis reclamos</Text><Text style={texto.secundario}>Consulta el estado y abre cada reclamo para ver su respuesta.</Text></View><Boton titulo="Registrar reclamo" icono="documento-mas" onPress={() => { setFormulario(true); setConfirmacion(''); lista.current?.scrollToOffset({ offset: 0, animated: true }) }} /></View>}
    {error ? <Aviso tipo="alerta" titulo="No pudimos consultar tus reclamos" accion={<Boton titulo="Reintentar" onPress={() => setRevision((valor) => valor + 1)} />}>{error}</Aviso> : null}
  </View>

  return <View style={estilos.raiz}>
    <Cabecera titulo="Mis reclamos" subtitulo="Registro y consulta" onVolver={() => router.back()} derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar reclamos" onPress={() => setRevision((valor) => valor + 1)} />} />
    <FlatList ref={lista} data={visibles} keyExtractor={(item) => String(item.idReclamo)} contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]} ListHeaderComponent={encabezado}
      ListEmptyComponent={cargando ? <ActivityIndicator color={colores.marino} /> : !error && <Aviso titulo="Aún no tienes reclamos">Cuando necesites reportar un incidente, usa “Registrar reclamo”.</Aviso>}
      renderItem={({ item }) => { const estado = ESTADOS_RECLAMO[item.estado] || { etiqueta: item.estado, tono: 'neutro' }; return <Pressable accessibilityRole="button" accessibilityLabel={`Reclamo ${item.numeroReclamo}, ${estado.etiqueta}`} onPress={() => router.push({ pathname: '/reclamos/[id]', params: { id: String(item.idReclamo) } })} style={({ pressed }) => [estilos.reclamo, pressed && estilos.presionado]}><View style={estilos.flex}><Text style={estilos.numero}>{item.numeroReclamo}</Text><Text style={texto.secundario}>{TIPOS_RECLAMO[item.tipoReclamo] || item.tipoReclamo} · {fechaHoraVisible(item.fechaRegistro)}</Text></View><View style={[estilos.estado, estilos[`estado_${estado.tono}`]]}><Text style={estilos.estadoTexto}>{estado.etiqueta}</Text></View><Icono nombre="chevron-derecha" /></Pressable> }}
      ListFooterComponent={!cargando && reclamos.length ? <Paginacion pagina={pagina} totalPaginas={totalPaginas} onCambiar={(valor) => { setPagina(valor); lista.current?.scrollToOffset({ offset: 0, animated: true }) }} /> : null} />
  </View>
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo }, contenido: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center', flexGrow: 1 }, encabezadoLista: { gap: 16, marginBottom: 4 },
  formulario: { gap: 14, padding: 18, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, ...sombra }, formularioCabecera: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' }, flex: { flex: 1 }, iconoCerrar: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radios.md },
  opciones: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 }, opcion: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 16, borderWidth: 1, borderColor: colores.bordeFuerte, borderRadius: radios.pill, backgroundColor: colores.superficie }, opcionActiva: { backgroundColor: colores.marino, borderColor: colores.marino }, opcionTexto: { fontFamily: fuentes.negrita, color: colores.marinoOsc }, opcionTextoActivo: { color: colores.blanco }, error: { fontFamily: fuentes.regular, color: colores.critico, fontSize: 14 },
  resumen: { gap: 12, padding: 18, borderRadius: radios.lg, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde }, reclamo: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 16, minHeight: 82, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg }, presionado: { backgroundColor: colores.azulSuave }, numero: { fontFamily: fuentes.negrita, fontSize: 17, color: colores.marinoOsc }, estado: { paddingHorizontal: 9, paddingVertical: 5, borderRadius: radios.pill }, estado_neutro: { backgroundColor: colores.fondo }, estado_aviso: { backgroundColor: colores.advertenciaFondo }, estado_exito: { backgroundColor: colores.exitoFondo }, estado_critico: { backgroundColor: colores.criticoFondo }, estadoTexto: { fontFamily: fuentes.negrita, fontSize: 12, color: colores.marinoOsc },
})
