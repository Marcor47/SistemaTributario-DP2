import { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Icono from '../../components/ui/Icono'
import { listarBuzon } from '../../services/buzonService'
import { fechaHoraBuzon, filtrarDocumentos, TIPOS_DOCUMENTO } from '../../utils/buzon'
import { colores, fuentes, radios, texto } from '../../theme/tokens'

export default function Buzon() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [filtro, setFiltro] = useState('')
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, datos: null, error: null })
  const actualizar = () => { setEstado((e) => ({ ...e, cargando: true })); setRevision((n) => n + 1) }
  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    listarBuzon({ signal: controlador.signal }).then((datos) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, datos, error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, datos: null, error })
    })
    return () => controlador.abort()
  // La revisión vuelve a consultar el buzón aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision]))
  const documentos = filtrarDocumentos(estado.datos?.documentos || [], filtro)
  return <View style={estilos.raiz}>
    <Cabecera titulo="Mi buzón" subtitulo="Documentos y respuestas de la Municipalidad" onVolver={() => router.back()}
      derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar buzón" onPress={actualizar} />} />
    <FlatList data={documentos} keyExtractor={(d) => String(d.idDocumento)}
      contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}
      refreshControl={<RefreshControl refreshing={estado.cargando} onRefresh={actualizar} />}
      ListHeaderComponent={<View style={estilos.cabecera}>
        <Text style={texto.subtitulo}>{estado.datos ? `${estado.datos.sinLeer} sin leer · ${estado.datos.total} ${estado.datos.total === 1 ? 'documento' : 'documentos'}` : 'Tus documentos'}</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={estilos.filtros}>
          {[['', 'Todos'], ['SIN_LEER', 'Sin leer'], ...Object.entries(TIPOS_DOCUMENTO)].map(([valor, etiqueta]) => <Pressable
            key={valor} accessibilityRole="button" accessibilityState={{ selected: filtro === valor }} onPress={() => setFiltro(valor)}
            style={[estilos.filtro, filtro === valor && estilos.filtroActivo]}>
            <Text style={[texto.pequeno, filtro === valor && estilos.textoActivo]}>{etiqueta}</Text>
          </Pressable>)}
        </ScrollView>
        {estado.error && <Aviso tipo="alerta" titulo="No pudimos consultar tu buzón" accion={<Boton titulo="Reintentar" onPress={actualizar} />}>{estado.error.message}</Aviso>}
      </View>}
      ListEmptyComponent={estado.cargando ? <ActivityIndicator color={colores.marino} /> : !estado.error && <Aviso titulo="Sin documentos">{filtro ? 'No hay documentos para este filtro.' : 'Aquí recibirás constancias, estados de cuenta y respuestas a tus solicitudes.'}</Aviso>}
      renderItem={({ item }) => <Pressable accessibilityRole="button" accessibilityLabel={`${item.leido ? 'Leído' : 'Sin leer'}. ${item.titulo}`}
        onPress={() => router.push({ pathname: '/buzon/[id]', params: { id: String(item.idDocumento) } })}
        style={[estilos.documento, !item.leido && estilos.sinLeer]}>
        <Icono nombre="documento" tamano={28} />
        <View style={estilos.textos}>
          <Text style={texto.pequeno}>{TIPOS_DOCUMENTO[item.tipo] || 'Documento'}</Text>
          <Text style={estilos.titulo}>{item.titulo}</Text>
          <Text style={texto.pequeno}>Enviado: {fechaHoraBuzon(item.fechaEnvio)}</Text>
          <Text style={texto.pequeno}>{item.leido ? `Primera lectura: ${fechaHoraBuzon(item.fechaPrimeraLectura)}` : 'Sin leer'}</Text>
        </View><Icono nombre="chevron-derecha" />
      </Pressable>} />
  </View>
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center', flexGrow: 1 },
  cabecera: { gap: 16, marginBottom: 8 }, filtros: { gap: 8 },
  filtro: { borderWidth: 1, borderColor: colores.borde, borderRadius: radios.pill, padding: 12, backgroundColor: colores.superficie },
  filtroActivo: { backgroundColor: colores.marino }, textoActivo: { color: colores.blanco },
  documento: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, backgroundColor: colores.superficie },
  sinLeer: { borderLeftWidth: 4, borderLeftColor: colores.cian }, textos: { flex: 1, gap: 5 },
  titulo: { fontFamily: fuentes.negrita, color: colores.marinoOsc, fontSize: 17, lineHeight: 23 },
})
