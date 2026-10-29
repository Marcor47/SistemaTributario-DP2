import { useState } from 'react'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { Image } from 'expo-image'
import { useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../components/ui/Cabecera'
import Icono from '../components/ui/Icono'
import { useSesion } from '../context/SesionContext'
import { colores, fuentes, radios, sombra, texto } from '../theme/tokens'

// Servicios del contribuyente. Cada historia agrega aquí su acceso.
const SERVICIOS = [
  {
    ruta: '/solicitud-predio',
    icono: 'documento-mas',
    titulo: 'Registro o traspaso de predio',
    descripcion: 'Envía tu solicitud y adjunta los documentos desde tu celular.',
  },
]

export default function Inicio() {
  const router = useRouter()
  const { sesion, salir } = useSesion()
  const { bottom } = useSafeAreaInsets()
  const [saliendo, setSaliendo] = useState(false)

  async function cerrarSesion() {
    if (saliendo) return
    setSaliendo(true)
    try {
      await salir()
    } catch {
      setSaliendo(false)
    }
  }

  return (
    <View style={estilos.raiz}>
      <Cabecera
        titulo="Te damos la bienvenida"
        subtitulo={`Contribuyente ${sesion.usuario?.codigoContribuyente ?? ''}`}
        izquierda={<Image source={require('../../assets/logo-san-miguel-circulo.png')} style={estilos.logo} accessible={false} />}
        derecha={<BotonCabecera icono="salir" etiqueta="Cerrar sesión" onPress={cerrarSesion} />}
      />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
        <Text style={texto.subtitulo} accessibilityRole="header">Trámites en línea</Text>
        <Text style={[texto.secundario, estilos.desc]}>Elige el trámite que deseas realizar.</Text>
        <View style={estilos.lista}>
          {SERVICIOS.map((s) => (
            <Pressable
              key={s.ruta}
              onPress={() => router.push(s.ruta)}
              accessibilityRole="button"
              accessibilityLabel={s.titulo}
              accessibilityHint={s.descripcion}
              style={({ pressed }) => [estilos.servicio, pressed && estilos.servicioPresionado]}
            >
              <View style={estilos.servicioIcono}>
                <Icono nombre={s.icono} color={colores.marino} tamano={26} />
              </View>
              <View style={estilos.servicioTextos}>
                <Text style={estilos.servicioTitulo}>{s.titulo}</Text>
                <Text style={texto.secundario}>{s.descripcion}</Text>
              </View>
              <Icono nombre="chevron-derecha" color={colores.textoAtenuado} />
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  logo: { width: 44, height: 44, borderRadius: 22, backgroundColor: colores.blanco, marginRight: 4 },
  contenido: { padding: 16, paddingTop: 24 },
  desc: { marginTop: 4, marginBottom: 16 },
  lista: { gap: 12 },
  servicio: {
    flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16, minHeight: 88,
    backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  servicioPresionado: { backgroundColor: colores.azulSuave, borderColor: colores.marino },
  servicioIcono: {
    width: 52, height: 52, borderRadius: radios.lg, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colores.azulSuave, borderLeftWidth: 4, borderLeftColor: colores.cian,
  },
  servicioTextos: { flex: 1, gap: 2 },
  servicioTitulo: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 23, color: colores.marinoOsc },
})
