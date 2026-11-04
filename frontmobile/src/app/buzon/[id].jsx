import { useEffect, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useLocalSearchParams, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import { abrirDocumento, descargarDocumento } from '../../services/buzonService'
import { fechaHoraBuzon, TIPOS_DOCUMENTO } from '../../utils/buzon'
import { colores, radios, texto } from '../../theme/tokens'

export default function Documento() {
  const { id } = useLocalSearchParams()
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [estado, setEstado] = useState({ documento: null, error: null })
  const [revision, setRevision] = useState(0)
  const [descargando, setDescargando] = useState(false)
  const [errorDescarga, setErrorDescarga] = useState(null)
  useEffect(() => {
    const controlador = new AbortController()
    abrirDocumento(id, { signal: controlador.signal }).then((documento) => {
      if (!controlador.signal.aborted) setEstado({ documento, error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setEstado({ documento: null, error })
    })
    return () => controlador.abort()
  }, [id, revision])
  async function descargar() {
    if (descargando) return
    setDescargando(true); setErrorDescarga(null)
    try { await descargarDocumento(id) } catch (error) { setErrorDescarga(error.message) }
    finally { setDescargando(false) }
  }
  const d = estado.documento
  return <View style={estilos.raiz}>
    <Cabecera titulo="Documento del buzón" onVolver={() => router.back()} />
    <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
      {!d && !estado.error && <ActivityIndicator color={colores.marino} />}
      {estado.error && <Aviso tipo="alerta" titulo="No se pudo abrir el documento" accion={<Boton titulo="Reintentar" onPress={() => { setEstado({ documento: null, error: null }); setRevision((n) => n + 1) }} />}>{estado.error.message}</Aviso>}
      {d && <View style={estilos.tarjeta}>
        <Text style={texto.pequeno}>{TIPOS_DOCUMENTO[d.tipo] || 'Documento'}</Text>
        <Text style={texto.subtitulo} accessibilityRole="header">{d.titulo}</Text>
        <Text style={texto.secundario}>Enviado: {fechaHoraBuzon(d.fechaEnvio)}</Text>
        <Text style={texto.secundario}>Primera lectura: {fechaHoraBuzon(d.fechaPrimeraLectura)}</Text>
        {d.contenido && <Text selectable style={texto.cuerpo}>{d.contenido}</Text>}
        {d.urlDescarga ? <Boton titulo="Abrir documento PDF" icono="documento" cargando={descargando} onPress={descargar} />
          : !d.contenido && <Aviso>El documento está registrado en tu buzón. Su archivo todavía no está disponible para descargar.</Aviso>}
        {errorDescarga && <Aviso tipo="alerta">{errorDescarga}</Aviso>}
      </View>}
    </ScrollView>
  </View>
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 16, width: '100%', maxWidth: 760, alignSelf: 'center' },
  tarjeta: { padding: 20, gap: 18, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg },
})
