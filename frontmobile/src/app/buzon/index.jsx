import { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import CampoTexto from '../../components/ui/CampoTexto'
import Paginacion from '../../components/ui/Paginacion'
import Icono from '../../components/ui/Icono'
import { cambiarArchivado, cambiarDestacado, listarBuzon } from '../../services/buzonService'
import { fechaHoraBuzon, TIPOS_DOCUMENTO } from '../../utils/buzon'
import { colores, fuentes, radios, texto } from '../../theme/tokens'

const VISTAS = [['RECIBIDOS', 'Recibidos'], ['DESTACADOS', 'Destacados'], ['ARCHIVADOS', 'Archivados']]
const LECTURAS = [['', 'Todos'], ['false', 'No leídos'], ['true', 'Leídos']]

export default function Buzon() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [vista, setVista] = useState('RECIBIDOS')
  const [busqueda, setBusqueda] = useState('')
  const [tipo, setTipo] = useState('')
  const [leido, setLeido] = useState('')
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false)
  const [aplicados, setAplicados] = useState({ busqueda: '', tipo: '', leido: '' })
  const [pagina, setPagina] = useState(0)
  const [revision, setRevision] = useState(0)
  const [procesando, setProcesando] = useState(null)
  const [estado, setEstado] = useState({ cargando: true, datos: null, error: null })
  const actualizar = () => setRevision((valor) => valor + 1)

  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    setEstado((actual) => ({ ...actual, cargando: true, error: null }))
    listarBuzon({ vista, ...aplicados, pagina, tamanio: 10, signal: controlador.signal }).then((datos) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, datos, error: null })
    }).catch((error) => { if (!controlador.signal.aborted) setEstado((actual) => ({ ...actual, cargando: false, error })) })
    return () => controlador.abort()
  // La revisión fuerza una nueva consulta aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vista, aplicados, pagina, revision]))

  function buscar() { setPagina(0); setAplicados({ busqueda, tipo, leido }); setFiltrosAbiertos(false) }
  function limpiar() { setBusqueda(''); setTipo(''); setLeido(''); setPagina(0); setAplicados({ busqueda: '', tipo: '', leido: '' }) }
  function abrirFiltros() { setTipo(aplicados.tipo); setLeido(aplicados.leido); setFiltrosAbiertos(true) }
  function cerrarFiltros() { setTipo(aplicados.tipo); setLeido(aplicados.leido); setFiltrosAbiertos(false) }
  async function cambiar(item, accion) {
    setProcesando(item.idDocumento)
    try {
      if (accion === 'destacar') await cambiarDestacado(item.idDocumento, !item.destacado)
      else await cambiarArchivado(item.idDocumento, !item.archivado)
      actualizar()
    } catch (error) { setEstado((actual) => ({ ...actual, error })) }
    finally { setProcesando(null) }
  }

  const datos = estado.datos || { documentos: [], total: 0, sinLeer: 0, totalPaginas: 0 }
  const filtrosActivos = Number(Boolean(aplicados.tipo)) + Number(aplicados.leido !== '')
  const hayBusqueda = Boolean(aplicados.busqueda.trim() || filtrosActivos)
  return <View style={estilos.raiz}>
    <Cabecera titulo="Mi buzón" subtitulo={`${datos.sinLeer} mensajes sin leer`} onVolver={() => { if (router.canGoBack()) router.back(); else router.replace('/') }} derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar buzón" onPress={actualizar} />} />
    <FlatList data={datos.documentos} keyExtractor={(item) => String(item.idDocumento)} contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]} refreshing={estado.cargando} onRefresh={actualizar}
      ListHeaderComponent={<View style={estilos.cabeceraLista}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={estilos.vistas}>{VISTAS.map(([valor, etiqueta]) => <Pressable key={valor} accessibilityRole="tab" accessibilityLabel={valor === 'RECIBIDOS' ? `${etiqueta}, ${datos.sinLeer} sin leer` : etiqueta} accessibilityState={{ selected: vista === valor }} onPress={() => { setVista(valor); setPagina(0) }} style={[estilos.vista, vista === valor && estilos.vistaActiva]}><Text style={[estilos.vistaTexto, vista === valor && estilos.vistaTextoActivo]}>{etiqueta}</Text>{valor === 'RECIBIDOS' && datos.sinLeer > 0 ? <View style={[estilos.contador, vista === valor && estilos.contadorActivo]}><Text style={[estilos.contadorTexto, vista === valor && estilos.vistaTextoActivo]}>{datos.sinLeer}</Text></View> : null}</Pressable>)}</ScrollView>
        <View style={estilos.filtros}>
          <CampoTexto etiqueta="Buscar mensajes" placeholder="Título o contenido" value={busqueda} onChangeText={setBusqueda} returnKeyType="search" onSubmitEditing={buscar} />
          <View style={estilos.accionesFiltro}><Boton titulo="Buscar" onPress={buscar} style={estilos.flex} />
            <Boton titulo={`Filtros${filtrosActivos ? ` (${filtrosActivos})` : ''}`} icono="chevron-abajo" variante="secundario" onPress={abrirFiltros} style={estilos.flex} /></View>
          {hayBusqueda ? <View style={estilos.resumenFiltros}><Text style={[texto.pequeno, estilos.flex]}>{[
            aplicados.busqueda.trim() ? `“${aplicados.busqueda.trim()}”` : '', TIPOS_DOCUMENTO[aplicados.tipo],
            aplicados.leido === 'false' ? 'No leídos' : aplicados.leido === 'true' ? 'Leídos' : '',
          ].filter(Boolean).join(' · ')}</Text><Pressable accessibilityRole="button" onPress={limpiar} style={estilos.limpiar}><Text style={texto.etiqueta}>Limpiar</Text></Pressable></View> : null}
        </View>
        {estado.error ? <Aviso tipo="alerta" titulo="No pudimos actualizar el buzón" accion={<Boton titulo="Reintentar" onPress={actualizar} />}>{estado.error.message}</Aviso> : null}
        <View style={estilos.resultados}><Text style={texto.subtitulo}>{VISTAS.find(([valor]) => valor === vista)?.[1]}</Text><Text style={texto.pequeno} accessibilityLiveRegion="polite">{datos.total} {datos.total === 1 ? 'mensaje' : 'mensajes'}</Text></View>
      </View>}
      ListEmptyComponent={estado.cargando ? <ActivityIndicator color={colores.marino} /> : !estado.error && <Aviso titulo="No hay mensajes">Prueba con otros filtros o revisa otra carpeta.</Aviso>}
      renderItem={({ item }) => <View style={[estilos.documento, !item.leido && estilos.sinLeer]}>
        <Pressable accessibilityRole="button" accessibilityLabel={`${item.leido ? 'Leído' : 'Sin leer'}. ${item.titulo}`} onPress={() => router.push({ pathname: '/buzon/[id]', params: { id: String(item.idDocumento) } })} style={({ pressed }) => [estilos.principal, pressed && estilos.presionado]}>
          <View style={[estilos.iconoDocumento, !item.leido && estilos.iconoSinLeer]}><Icono nombre="documento" tamano={24} /></View>
          <View style={estilos.textos}><Text style={texto.pequeno}>{TIPOS_DOCUMENTO[item.tipo] || 'Documento'}</Text><Text style={[estilos.titulo, item.leido && estilos.tituloLeido]} numberOfLines={2}>{item.titulo}</Text>
            {!item.leido ? <View style={estilos.etiquetaSinLeer}><View style={estilos.puntoSinLeer} /><Text style={estilos.textoSinLeer}>Sin leer</Text></View> : null}</View><Icono nombre="chevron-derecha" tamano={18} /></Pressable>
        <View style={estilos.pieDocumento}><Text style={[texto.pequeno, estilos.flex]}>{fechaHoraBuzon(item.fechaEnvio)}</Text>
          <View style={estilos.accionesItem}><Accion icono="estrella" etiqueta={`${item.destacado ? 'Quitar destacado de' : 'Destacar'} ${item.titulo}`} activa={item.destacado} deshabilitada={procesando !== null} onPress={() => cambiar(item, 'destacar')} /><Accion icono="archivo" etiqueta={`${item.archivado ? 'Restaurar' : 'Archivar'} ${item.titulo}`} deshabilitada={procesando !== null} onPress={() => cambiar(item, 'archivar')} /></View>
        </View>
      </View>}
      ListFooterComponent={!estado.cargando && datos.total > 0 ? <Paginacion pagina={pagina} totalPaginas={Math.max(1, datos.totalPaginas)} onCambiar={setPagina} /> : null} />
    <Modal visible={filtrosAbiertos} transparent animationType="fade" onRequestClose={cerrarFiltros}>
      <View style={estilos.fondoModal}>
        <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel="Cerrar filtros" onPress={cerrarFiltros} />
        <View style={[estilos.modal, { paddingBottom: Math.max(bottom, 16) }]} accessibilityViewIsModal>
          <View style={estilos.resultados}><Text style={texto.subtitulo}>Filtrar mensajes</Text><Pressable style={estilos.accion} accessibilityRole="button" accessibilityLabel="Cerrar filtros" onPress={cerrarFiltros}><Icono nombre="cerrar" /></Pressable></View>
          <ScrollView contentContainerStyle={estilos.contenidoModal} keyboardShouldPersistTaps="handled">
            <Text style={texto.etiqueta}>Tipo de mensaje</Text>
            <View style={estilos.opcionesTipo}>{[['', 'Todos los tipos'], ...Object.entries(TIPOS_DOCUMENTO)].map(([valor, etiqueta]) => <Chip key={valor} activo={tipo === valor} etiqueta={etiqueta} onPress={() => setTipo(valor)} />)}</View>
            <Text style={texto.etiqueta}>Lectura</Text><View style={estilos.chips}>{LECTURAS.map(([valor, etiqueta]) => <Chip key={valor} activo={leido === valor} etiqueta={etiqueta} onPress={() => setLeido(valor)} />)}</View>
          </ScrollView>
          <Boton titulo="Aplicar filtros" onPress={buscar} />
        </View>
      </View>
    </Modal>
  </View>
}

function Chip({ etiqueta, activo, onPress }) { return <Pressable accessibilityRole="radio" accessibilityState={{ checked: activo }} onPress={onPress} style={[estilos.chip, activo && estilos.chipActivo]}><Text style={[estilos.chipTexto, activo && estilos.chipTextoActivo]}>{etiqueta}</Text>{activo ? <Icono nombre="check" tamano={16} /> : null}</Pressable> }
function Accion({ icono, etiqueta, activa, deshabilitada, onPress }) { return <Pressable accessibilityRole="button" accessibilityLabel={etiqueta} accessibilityState={{ selected: Boolean(activa), disabled: deshabilitada }} disabled={deshabilitada} onPress={onPress} style={({ pressed }) => [estilos.accion, activa && estilos.accionActiva, pressed && estilos.presionado]}><Icono nombre={icono} tamano={20} color={activa ? colores.marino : colores.textoSecundario} /></Pressable> }

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center', flexGrow: 1 },
  cabeceraLista: { gap: 18, marginBottom: 4 },
  vistas: { gap: 4, padding: 4, flexGrow: 1, backgroundColor: '#E4ECF5', borderRadius: radios.lg },
  vista: { flex: 1, minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: 10, borderRadius: radios.md },
  vistaActiva: { backgroundColor: colores.marino },
  vistaTexto: { fontFamily: fuentes.negrita, fontSize: 14, color: colores.marinoOsc },
  vistaTextoActivo: { color: colores.blanco },
  contador: { minWidth: 20, height: 20, paddingHorizontal: 5, borderRadius: radios.pill, alignItems: 'center', justifyContent: 'center', backgroundColor: colores.borde },
  contadorActivo: { backgroundColor: '#526598' },
  contadorTexto: { fontFamily: fuentes.negrita, fontSize: 11, color: colores.marinoOsc },
  filtros: { gap: 12, padding: 16, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, backgroundColor: colores.superficie },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, paddingHorizontal: 14, borderRadius: radios.md, borderWidth: 1, borderColor: colores.borde, backgroundColor: colores.superficie },
  chipActivo: { backgroundColor: colores.azulSuave, borderColor: colores.marino },
  chipTexto: { flexShrink: 1, fontFamily: fuentes.regular, fontSize: 14, color: colores.marinoOsc },
  chipTextoActivo: { fontFamily: fuentes.negrita },
  accionesFiltro: { flexDirection: 'row', gap: 8 },
  flex: { flex: 1 },
  resumenFiltros: { flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: 1, borderTopColor: colores.borde, paddingTop: 8 },
  limpiar: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 8 },
  resultados: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  documento: { borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, backgroundColor: colores.superficie, overflow: 'hidden' },
  sinLeer: { borderLeftWidth: 3, borderLeftColor: colores.cian },
  principal: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, paddingBottom: 8 },
  presionado: { backgroundColor: colores.azulSuave },
  iconoDocumento: { width: 44, height: 48, borderRadius: radios.md, backgroundColor: colores.superficieTenue, alignItems: 'center', justifyContent: 'center' },
  iconoSinLeer: { backgroundColor: colores.azulSuave },
  textos: { flex: 1, minWidth: 0, gap: 5 },
  titulo: { fontFamily: fuentes.negrita, color: colores.marinoOsc, fontSize: 16, lineHeight: 22 },
  tituloLeido: { fontFamily: fuentes.regular },
  etiquetaSinLeer: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  puntoSinLeer: { width: 6, height: 6, borderRadius: 3, backgroundColor: colores.marino },
  textoSinLeer: { ...texto.pequeno, color: colores.marino, fontSize: 11, fontFamily: fuentes.negrita },
  pieDocumento: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, paddingLeft: 16, paddingRight: 8, paddingBottom: 8 },
  accionesItem: { flexDirection: 'row', gap: 4 },
  accion: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radios.md },
  accionActiva: { backgroundColor: colores.azulSuave },
  fondoModal: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 20, backgroundColor: colores.sombraModal },
  modal: { width: '100%', maxWidth: 440, maxHeight: '85%', padding: 20, gap: 16, borderRadius: radios.xl, backgroundColor: colores.superficie },
  contenidoModal: { gap: 12, paddingBottom: 8 },
  opcionesTipo: { gap: 8 },
})
