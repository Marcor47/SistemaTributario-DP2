import { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
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

  function buscar() { setPagina(0); setAplicados({ busqueda, tipo, leido }) }
  function limpiar() { setBusqueda(''); setTipo(''); setLeido(''); setPagina(0); setAplicados({ busqueda: '', tipo: '', leido: '' }) }
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
  return <View style={estilos.raiz}>
    <Cabecera titulo="Mi buzón" subtitulo={`${datos.sinLeer} mensajes sin leer`} onVolver={() => router.back()} derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar buzón" onPress={actualizar} />} />
    <FlatList data={datos.documentos} keyExtractor={(item) => String(item.idDocumento)} contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]} refreshing={estado.cargando} onRefresh={actualizar}
      ListHeaderComponent={<View style={estilos.cabeceraLista}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={estilos.vistas}>{VISTAS.map(([valor, etiqueta]) => <Pressable key={valor} accessibilityRole="tab" accessibilityState={{ selected: vista === valor }} onPress={() => { setVista(valor); setPagina(0) }} style={[estilos.vista, vista === valor && estilos.vistaActiva]}><Text style={[estilos.vistaTexto, vista === valor && estilos.vistaTextoActivo]}>{etiqueta}{valor === 'RECIBIDOS' && datos.sinLeer ? ` (${datos.sinLeer})` : ''}</Text></Pressable>)}</ScrollView>
        <View style={estilos.filtros}><CampoTexto etiqueta="Buscar" placeholder="Título o contenido" value={busqueda} onChangeText={setBusqueda} returnKeyType="search" onSubmitEditing={buscar} />
          <Text style={texto.etiqueta}>Tipo de mensaje</Text><ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={estilos.chips}>{[['', 'Todos'], ...Object.entries(TIPOS_DOCUMENTO)].map(([valor, etiqueta]) => <Chip key={valor} activo={tipo === valor} etiqueta={etiqueta} onPress={() => setTipo(valor)} />)}</ScrollView>
          <Text style={texto.etiqueta}>Lectura</Text><View style={estilos.chips}>{LECTURAS.map(([valor, etiqueta]) => <Chip key={valor} activo={leido === valor} etiqueta={etiqueta} onPress={() => setLeido(valor)} />)}</View>
          <View style={estilos.accionesFiltro}><Boton titulo="Buscar" onPress={buscar} style={estilos.flex} /><Boton titulo="Limpiar" variante="secundario" onPress={limpiar} style={estilos.flex} /></View>
        </View>
        {estado.error ? <Aviso tipo="alerta" titulo="No pudimos actualizar el buzón" accion={<Boton titulo="Reintentar" onPress={actualizar} />}>{estado.error.message}</Aviso> : null}
        <View><Text style={texto.subtitulo}>{VISTAS.find(([valor]) => valor === vista)?.[1]}</Text><Text style={texto.secundario}>{datos.total} {datos.total === 1 ? 'mensaje' : 'mensajes'}</Text></View>
      </View>}
      ListEmptyComponent={estado.cargando ? <ActivityIndicator color={colores.marino} /> : !estado.error && <Aviso titulo="No hay mensajes">Prueba con otros filtros o revisa otra carpeta.</Aviso>}
      renderItem={({ item }) => <View style={[estilos.documento, !item.leido && estilos.sinLeer]}>
        <Pressable accessibilityRole="button" accessibilityLabel={`${item.leido ? 'Leído' : 'Sin leer'}. ${item.titulo}`} onPress={() => router.push({ pathname: '/buzon/[id]', params: { id: String(item.idDocumento) } })} style={({ pressed }) => [estilos.principal, pressed && estilos.presionado]}><Icono nombre="documento" tamano={28} /><View style={estilos.textos}><Text style={texto.pequeno}>{TIPOS_DOCUMENTO[item.tipo] || 'Documento'}</Text><Text style={estilos.titulo}>{item.titulo}</Text><Text style={texto.pequeno}>{fechaHoraBuzon(item.fechaEnvio)}</Text></View><Icono nombre="chevron-derecha" /></Pressable>
        <View style={estilos.accionesItem}><Accion icono="estrella" etiqueta={item.destacado ? 'Quitar destacado' : 'Destacar'} activa={item.destacado} deshabilitada={procesando === item.idDocumento} onPress={() => cambiar(item, 'destacar')} /><Accion icono="archivo" etiqueta={item.archivado ? 'Restaurar' : 'Archivar'} deshabilitada={procesando === item.idDocumento} onPress={() => cambiar(item, 'archivar')} /></View>
      </View>}
      ListFooterComponent={!estado.cargando && datos.total > 0 ? <Paginacion pagina={pagina} totalPaginas={Math.max(1, datos.totalPaginas)} onCambiar={setPagina} /> : null} />
  </View>
}

function Chip({ etiqueta, activo, onPress }) { return <Pressable accessibilityRole="radio" accessibilityState={{ checked: activo }} onPress={onPress} style={[estilos.chip, activo && estilos.chipActivo]}><Text style={[estilos.chipTexto, activo && estilos.chipTextoActivo]}>{etiqueta}</Text></Pressable> }
function Accion({ icono, etiqueta, activa, deshabilitada, onPress }) { return <Pressable accessibilityRole="button" accessibilityLabel={etiqueta} accessibilityState={{ selected: activa, disabled: deshabilitada }} disabled={deshabilitada} onPress={onPress} style={({ pressed }) => [estilos.accion, activa && estilos.accionActiva, pressed && estilos.presionado]}><Icono nombre={icono} tamano={18} color={activa ? colores.marino : colores.textoSecundario} /><Text style={estilos.accionTexto}>{etiqueta}</Text></Pressable> }

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo }, contenido: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center', flexGrow: 1 }, cabeceraLista: { gap: 16, marginBottom: 4 }, vistas: { gap: 8 }, vista: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 18, borderWidth: 1, borderColor: colores.bordeFuerte, borderRadius: radios.pill, backgroundColor: colores.superficie }, vistaActiva: { backgroundColor: colores.marino, borderColor: colores.marino }, vistaTexto: { fontFamily: fuentes.negrita, color: colores.marinoOsc }, vistaTextoActivo: { color: colores.blanco },
  filtros: { gap: 10, padding: 16, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, backgroundColor: colores.superficie }, chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 }, chip: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 14, borderRadius: radios.pill, borderWidth: 1, borderColor: colores.bordeFuerte, backgroundColor: colores.superficie }, chipActivo: { backgroundColor: colores.azulSuave, borderColor: colores.marino }, chipTexto: { fontFamily: fuentes.regular, fontSize: 15, color: colores.marinoOsc }, chipTextoActivo: { fontFamily: fuentes.negrita }, accionesFiltro: { flexDirection: 'row', gap: 8 }, flex: { flex: 1 },
  documento: { borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg, backgroundColor: colores.superficie, overflow: 'hidden' }, sinLeer: { borderLeftWidth: 4, borderLeftColor: colores.cian }, principal: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16 }, presionado: { backgroundColor: colores.azulSuave }, textos: { flex: 1, gap: 4 }, titulo: { fontFamily: fuentes.negrita, color: colores.marinoOsc, fontSize: 17, lineHeight: 23 }, accionesItem: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, padding: 10, borderTopWidth: 1, borderTopColor: colores.borde }, accion: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: 12, borderRadius: radios.md }, accionActiva: { backgroundColor: colores.azulSuave }, accionTexto: { fontFamily: fuentes.negrita, color: colores.marinoOsc, fontSize: 14 },
})
