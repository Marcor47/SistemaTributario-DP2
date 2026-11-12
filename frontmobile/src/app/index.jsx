import { useCallback, useState } from 'react'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { Image } from 'expo-image'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera, { BotonCabecera } from '../components/ui/Cabecera'
import Icono from '../components/ui/Icono'
import { useSesion } from '../context/SesionContext'
import { listarBuzon } from '../services/buzonService'
import { colores, fuentes, radios, sombra, texto } from '../theme/tokens'

// Servicios del contribuyente. Cada historia agrega aquí su acceso.
const SERVICIOS = [
  {
    ruta: '/buzon',
    icono: 'documento',
    titulo: 'Mi buzón',
    descripcion: 'Recibe tus documentos y las respuestas a tus solicitudes.',
  },
  {
    ruta: '/reclamos',
    icono: 'reclamo',
    titulo: 'Mis reclamos',
    descripcion: 'Registra un reclamo y consulta su estado y respuesta.',
  },
  {
    ruta: '/estado-cuenta',
    icono: 'tarjeta',
    titulo: 'Estado de cuenta y pagos',
    descripcion: 'Consulta tus obligaciones y paga todo tu saldo pendiente.',
  },
  {
    ruta: '/solicitud-predio',
    icono: 'documento-mas',
    titulo: 'Registro o traspaso de predio',
    descripcion: 'Envía tu solicitud y adjunta los documentos desde tu celular.',
  },
  {
    ruta: '/tramites',
    icono: 'reloj',
    titulo: 'Estado de mis trámites de predio',
    descripcion: 'Consulta el avance de tus solicitudes de registro o traspaso.',
  },
  {
    ruta: '/constancia',
    icono: 'documento',
    titulo: 'Constancia de No Adeudo',
    descripcion: 'Solicita una constancia y firma la solicitud desde tu celular.',
  },
]

export default function Inicio() {
  const router = useRouter()
  const { sesion, salir } = useSesion()
  const { bottom } = useSafeAreaInsets()
  const [saliendo, setSaliendo] = useState(false)
  const [sinLeer, setSinLeer] = useState(0)
  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    listarBuzon({ tamanio: 1, signal: controlador.signal }).then((datos) => setSinLeer(datos.sinLeer)).catch(() => {})
    return () => controlador.abort()
  }, []))

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
        derecha={<View style={estilos.accionesCabecera}>
          <View><BotonCabecera icono="campana" etiqueta={`Abrir buzón, ${sinLeer} mensajes sin leer`} onPress={() => router.push('/buzon')} />
            {sinLeer > 0 && <View style={estilos.contador}><Text style={estilos.contadorTexto}>{sinLeer > 99 ? '99+' : sinLeer}</Text></View>}
          </View>
          <BotonCabecera icono="salir" etiqueta="Cerrar sesión" onPress={cerrarSesion} />
        </View>}
      />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
        <Text style={texto.subtitulo} accessibilityRole="header">Servicios en línea</Text>
        <Text style={[texto.secundario, estilos.desc]}>Elige el servicio que deseas utilizar.</Text>
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
              {s.ruta === '/buzon' && sinLeer > 0 ? <View style={estilos.badge}><Text style={estilos.badgeTexto}>{sinLeer} sin leer</Text></View> : null}
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
  accionesCabecera: { flexDirection: 'row', alignItems: 'center' },
  contador: { position: 'absolute', top: 1, right: 1, minWidth: 20, height: 20, paddingHorizontal: 5, borderRadius: 10, alignItems: 'center', justifyContent: 'center', backgroundColor: colores.critico, borderWidth: 2, borderColor: colores.marino },
  contadorTexto: { fontFamily: fuentes.negrita, color: colores.blanco, fontSize: 11 },
  badge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: radios.pill, backgroundColor: colores.azulSuave },
  badgeTexto: { fontFamily: fuentes.negrita, color: colores.marinoOsc, fontSize: 12 },
})
