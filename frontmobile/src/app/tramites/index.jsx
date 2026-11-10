import { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Icono from '../../components/ui/Icono'
import EtiquetaEstado from '../../components/solicitudPredio/EtiquetaEstado'
import { PREDIOS, TRAMITES } from '../../components/solicitudPredio/catalogos'
import { listarSolicitudes } from '../../services/solicitudPredioService'
import { fechaHoraBuzon } from '../../utils/buzon'
import { ordenarSolicitudes, textoAdjuntos } from '../../utils/solicitudPredio'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

// HU047: el contribuyente consulta el estado de sus solicitudes de registro o traspaso de predio.
export default function MisTramites() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, solicitudes: null, error: null })
  const actualizar = () => { setEstado((e) => ({ ...e, cargando: true })); setRevision((n) => n + 1) }

  // Se vuelve a consultar al recuperar el foco: el estado puede cambiar mientras el contribuyente está en otra pantalla.
  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    listarSolicitudes({ signal: controlador.signal }).then((lista) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, solicitudes: ordenarSolicitudes(lista), error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, solicitudes: null, error })
    })
    return () => controlador.abort()
  // La revisión vuelve a consultar aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision]))

  const { cargando, solicitudes, error } = estado
  return (
    <View style={estilos.raiz}>
      <Cabecera
        titulo="Mis trámites de predio"
        subtitulo="Estado de tus solicitudes de registro o traspaso"
        onVolver={() => router.back()}
        derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar trámites" onPress={actualizar} />}
      />
      <FlatList
        data={solicitudes ?? []}
        keyExtractor={(s) => String(s.idSolicitud)}
        contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}
        refreshControl={<RefreshControl refreshing={cargando && Boolean(solicitudes)} onRefresh={actualizar} />}
        ListHeaderComponent={error ? (
          <Aviso tipo="alerta" titulo="No pudimos consultar tus trámites" accion={<Boton titulo="Reintentar" onPress={actualizar} />}>
            {error.message}
          </Aviso>
        ) : null}
        ListEmptyComponent={cargando ? (
          <ActivityIndicator color={colores.marino} accessibilityLabel="Cargando tus trámites" style={estilos.cargando} />
        ) : !error ? (
          <View style={estilos.vacio}>
            <Aviso titulo="Aún no tienes solicitudes">
              Cuando envíes una solicitud de registro o traspaso de predio, podrás ver aquí su avance.
            </Aviso>
            <Boton titulo="Presentar una solicitud" icono="documento-mas" onPress={() => router.push('/solicitud-predio')} grande />
          </View>
        ) : null}
        renderItem={({ item }) => <Tarjeta solicitud={item} onPress={() => router.push({ pathname: '/tramites/[id]', params: { id: String(item.idSolicitud) } })} />}
      />
    </View>
  )
}

function Tarjeta({ solicitud, onPress }) {
  const tramite = TRAMITES.find((t) => t.valor === solicitud.tipoTramite)
  const predio = PREDIOS.find((p) => p.valor === solicitud.tipoPredio)
  const titulo = tramite?.titulo ?? 'Solicitud de predio'
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${titulo}, solicitud ${solicitud.idSolicitud}. Ver estado`}
      style={({ pressed }) => [estilos.tarjeta, pressed && estilos.presionada]}
    >
      <View style={estilos.fila}>
        <Text style={estilos.numero}>N.º {solicitud.idSolicitud}</Text>
        <EtiquetaEstado estado={solicitud.estado} />
      </View>
      <Text style={estilos.titulo}>{titulo}</Text>
      <Text style={texto.secundario}>
        {[predio ? `Predio ${predio.titulo.toLowerCase()}` : null, solicitud.codigoPredio ? `Código ${solicitud.codigoPredio}` : null]
          .filter(Boolean).join(' · ')}
      </Text>
      <View style={estilos.fila}>
        <Text style={texto.pequeno}>Enviada: {fechaHoraBuzon(solicitud.fechaEnvio)}</Text>
        <Text style={texto.pequeno}>{textoAdjuntos(solicitud.adjuntos?.length)}</Text>
        <Icono nombre="chevron-derecha" color={colores.textoAtenuado} />
      </View>
    </Pressable>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 12, width: '100%', maxWidth: 760, alignSelf: 'center', flexGrow: 1 },
  cargando: { marginTop: 32 },
  vacio: { gap: 16 },
  tarjeta: {
    gap: 6, padding: 16, backgroundColor: colores.superficie, borderRadius: radios.lg,
    borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  presionada: { backgroundColor: colores.azulSuave, borderColor: colores.marino },
  fila: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' },
  numero: { fontFamily: fuentes.negrita, fontSize: 14, color: colores.textoSecundario },
  titulo: { fontFamily: fuentes.negrita, fontSize: 18, lineHeight: 24, color: colores.marinoOsc },
})
