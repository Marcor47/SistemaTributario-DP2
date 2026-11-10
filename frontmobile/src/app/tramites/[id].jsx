import { useCallback, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Icono from '../../components/ui/Icono'
import EtiquetaEstado from '../../components/solicitudPredio/EtiquetaEstado'
import { PREDIOS, TRAMITES } from '../../components/solicitudPredio/catalogos'
import { formatearTamano } from '../../services/adjuntos'
import { obtenerSolicitud } from '../../services/solicitudPredioService'
import { fechaHoraBuzon } from '../../utils/buzon'
import { infoEstado, observacionRechazo, tieneResultado } from '../../utils/solicitudPredio'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

// HU047: detalle del estado de una solicitud propia. Si fue rechazada, muestra la observación registrada.
export default function DetalleTramite() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { id } = useLocalSearchParams()
  const [revision, setRevision] = useState(0)
  const [estado, setEstado] = useState({ cargando: true, solicitud: null, error: null })
  const actualizar = () => { setEstado((e) => ({ ...e, cargando: true, error: null })); setRevision((n) => n + 1) }

  useFocusEffect(useCallback(() => {
    let activo = true
    obtenerSolicitud(id).then((solicitud) => {
      if (activo) setEstado({ cargando: false, solicitud, error: null })
    }).catch((error) => {
      if (activo) setEstado({ cargando: false, solicitud: null, error })
    })
    return () => { activo = false }
  // La revisión vuelve a consultar aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, revision]))

  const { cargando, solicitud, error } = estado
  const info = infoEstado(solicitud?.estado)
  const observacion = observacionRechazo(solicitud)
  const tramite = TRAMITES.find((t) => t.valor === solicitud?.tipoTramite)
  const predio = PREDIOS.find((p) => p.valor === solicitud?.tipoPredio)

  return (
    <View style={estilos.raiz}>
      <Cabecera
        titulo={`Solicitud N.º ${id}`}
        subtitulo={tramite?.titulo ?? 'Registro o traspaso de predio'}
        onVolver={() => router.back()}
        derecha={<BotonCabecera icono="reintentar" etiqueta="Actualizar estado" onPress={actualizar} />}
      />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
        {cargando && !solicitud ? (
          <ActivityIndicator color={colores.marino} accessibilityLabel="Cargando la solicitud" style={estilos.cargando} />
        ) : null}

        {error ? (
          <Aviso
            tipo="alerta"
            titulo={error.estado === 404 ? 'No encontramos esta solicitud' : 'No pudimos consultar la solicitud'}
            accion={error.estado === 404 ? <Boton titulo="Ver mis trámites" onPress={() => router.replace('/tramites')} /> : <Boton titulo="Reintentar" onPress={actualizar} />}
          >
            {error.message}
          </Aviso>
        ) : null}

        {solicitud ? (
          <>
            <View style={estilos.estado}>
              <Text style={estilos.estadoEtiqueta}>Estado actual</Text>
              <EtiquetaEstado estado={solicitud.estado} />
            </View>

            {info.descripcion ? <Text style={texto.cuerpo}>{info.descripcion}</Text> : null}

            {observacion ? (
              <Aviso tipo="critico" titulo="Observación de la Municipalidad">{observacion}</Aviso>
            ) : solicitud.estado === 'RECHAZADA' ? (
              <Aviso tipo="info" titulo="Detalle del rechazo">
                Aún no hay una observación registrada para esta solicitud. Revisa la respuesta en tu buzón.
              </Aviso>
            ) : null}

            <View style={estilos.tarjeta}>
              <Dato etiqueta="Trámite" valor={tramite?.titulo} />
              <Dato etiqueta="Tipo de predio" valor={predio?.titulo} />
              <Dato etiqueta="Código del predio" valor={solicitud.codigoPredio} />
              <Dato etiqueta="Fecha de envío" valor={fechaHoraBuzon(solicitud.fechaEnvio)} />
              {solicitud.adjuntos?.length ? (
                <View style={estilos.dato}>
                  <Text style={estilos.datoEtiqueta}>Documentos enviados</Text>
                  {solicitud.adjuntos.map((a) => (
                    <View key={a.id} style={estilos.adjunto}>
                      <Icono nombre={a.tipo === 'PDF' ? 'documento' : 'imagen'} color={colores.marino} tamano={18} />
                      <Text style={[texto.secundario, estilos.flex]} numberOfLines={1} ellipsizeMode="middle">{a.nombre}</Text>
                      <Text style={texto.pequeno}>{formatearTamano(a.tamano)}</Text>
                    </View>
                  ))}
                </View>
              ) : null}
            </View>

            {tieneResultado(solicitud) ? (
              <Boton titulo="Ver respuesta en mi buzón" icono="documento" onPress={() => router.push('/buzon')} grande />
            ) : null}
            {solicitud.estado === 'RECHAZADA' ? (
              <Boton titulo="Presentar una nueva solicitud" icono="documento-mas" onPress={() => router.push('/solicitud-predio')} grande />
            ) : null}
          </>
        ) : null}
      </ScrollView>
    </View>
  )
}

function Dato({ etiqueta, valor }) {
  if (!valor || valor === '—') return null
  return (
    <View style={estilos.dato}>
      <Text style={estilos.datoEtiqueta}>{etiqueta}</Text>
      <Text style={estilos.datoValor}>{valor}</Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  flex: { flex: 1 },
  contenido: { padding: 16, gap: 16, width: '100%', maxWidth: 760, alignSelf: 'center' },
  cargando: { marginTop: 32 },
  estado: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' },
  estadoEtiqueta: { fontFamily: fuentes.negrita, fontSize: 13, color: colores.textoAtenuado, textTransform: 'uppercase', letterSpacing: 0.6 },
  tarjeta: {
    padding: 16, gap: 4, backgroundColor: colores.superficie, borderRadius: radios.lg,
    borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  dato: { paddingVertical: 10, borderTopWidth: 1, borderTopColor: colores.borde, gap: 4 },
  datoEtiqueta: { fontFamily: fuentes.negrita, fontSize: 13, color: colores.textoAtenuado, textTransform: 'uppercase', letterSpacing: 0.6 },
  datoValor: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 24, color: colores.marinoOsc },
  adjunto: { flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 32 },
})
