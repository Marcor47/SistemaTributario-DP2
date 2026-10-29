import { useEffect, useState } from 'react'
import { BackHandler, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useLocalSearchParams, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Boton from '../../components/ui/Boton'
import Icono from '../../components/ui/Icono'
import { ESTADOS, PREDIOS, TRAMITES } from '../../components/solicitudPredio/catalogos'
import { formatearTamano } from '../../services/adjuntos'
import { obtenerSolicitud } from '../../services/solicitudPredioService'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

// fechaEnvio llega como LocalDateTime sin zona ("2026-10-29T10:15:30"); se muestra tal cual, sin convertir.
function formatearFecha(valor) {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(valor || '')
  return m ? `${m[3]}/${m[2]}/${m[1]}, ${m[4]}:${m[5]}` : null
}

export default function SolicitudEnviada() {
  const router = useRouter()
  const { top, bottom } = useSafeAreaInsets()
  const params = useLocalSearchParams()
  // El POST ya respondió 201; aquí solo se consulta el detalle registrado para mostrar los adjuntos.
  const [solicitud, setSolicitud] = useState(null)

  useEffect(() => {
    let activo = true
    if (params.id) {
      obtenerSolicitud(params.id).then((s) => activo && setSolicitud(s)).catch(() => {})
    }
    return () => { activo = false }
  }, [params.id])

  function irAlInicio() {
    if (router.canGoBack()) router.back()
    else router.replace('/')
  }

  // Volver atrás no debe regresar al formulario ya enviado.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { irAlInicio(); return true })
    return () => sub.remove()
  })

  const tramite = TRAMITES.find((t) => t.valor === (solicitud?.tipoTramite ?? params.tramite))
  const predio = PREDIOS.find((p) => p.valor === (solicitud?.tipoPredio ?? params.predio))
  const fecha = formatearFecha(solicitud?.fechaEnvio ?? params.fecha)
  const estado = ESTADOS[solicitud?.estado ?? 'EN_REVISION']

  return (
    <View style={estilos.raiz}>
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingTop: top + 32 }]}>
        <View style={estilos.exito}>
          <Icono nombre="check" color={colores.blanco} tamano={44} trazo={3} />
        </View>
        <Text style={[texto.titulo, estilos.centro]} accessibilityRole="header">Solicitud enviada</Text>
        <Text style={[texto.secundario, estilos.centro, estilos.desc]}>
          Recibimos tu solicitud y tus documentos. Te informaremos el resultado de la revisión.
        </Text>

        <View style={estilos.tarjeta}>
          <View style={estilos.numero}>
            <Text style={estilos.numeroEtiqueta}>N.º de solicitud</Text>
            <Text style={estilos.numeroValor} selectable>{params.id}</Text>
            <View style={estilos.estado}>
              <Icono nombre="reloj" color={colores.advertencia} tamano={16} />
              <Text style={estilos.estadoTexto}>{estado}</Text>
            </View>
          </View>
          <Dato etiqueta="Trámite" valor={tramite?.titulo} />
          <Dato etiqueta="Tipo de predio" valor={predio?.titulo} />
          {solicitud?.codigoPredio ? <Dato etiqueta="Código del predio" valor={solicitud.codigoPredio} /> : null}
          {fecha ? <Dato etiqueta="Fecha de envío" valor={fecha} /> : null}
          {solicitud?.adjuntos?.length ? (
            <View style={estilos.dato}>
              <Text style={estilos.datoEtiqueta}>Documentos recibidos</Text>
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
      </ScrollView>

      <View style={[estilos.barra, { paddingBottom: bottom + 12 }]}>
        <Boton titulo="Volver al inicio" icono="casa" onPress={irAlInicio} grande />
      </View>
    </View>
  )
}

function Dato({ etiqueta, valor }) {
  if (!valor) return null
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
  contenido: { padding: 16, paddingBottom: 32, alignItems: 'stretch' },
  exito: {
    alignSelf: 'center', width: 88, height: 88, borderRadius: 44, marginBottom: 20, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colores.exito, borderWidth: 6, borderColor: colores.exitoBorde,
  },
  centro: { textAlign: 'center' },
  desc: { marginTop: 8, marginBottom: 24, paddingHorizontal: 8 },
  tarjeta: {
    padding: 16, gap: 4, backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  numero: {
    alignItems: 'center', gap: 4, paddingVertical: 16, marginBottom: 8, borderRadius: radios.md,
    backgroundColor: colores.azulSuave, borderLeftWidth: 5, borderLeftColor: colores.cian,
  },
  numeroEtiqueta: { fontFamily: fuentes.negrita, fontSize: 14, color: colores.textoSecundario },
  numeroValor: { fontFamily: fuentes.negrita, fontSize: 34, lineHeight: 40, color: colores.marinoOsc },
  estado: {
    flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 4, marginTop: 4,
    borderRadius: radios.pill, backgroundColor: colores.advertenciaFondo, borderWidth: 1, borderColor: colores.advertenciaBorde,
  },
  estadoTexto: { fontFamily: fuentes.negrita, fontSize: 14, color: colores.advertencia },
  dato: { paddingVertical: 10, borderTopWidth: 1, borderTopColor: colores.borde, gap: 4 },
  datoEtiqueta: { fontFamily: fuentes.negrita, fontSize: 13, color: colores.textoAtenuado, textTransform: 'uppercase', letterSpacing: 0.6 },
  datoValor: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 24, color: colores.marinoOsc },
  adjunto: { flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 32 },
  barra: { paddingHorizontal: 16, paddingTop: 12, backgroundColor: colores.superficie, borderTopWidth: 1, borderTopColor: colores.borde },
})
