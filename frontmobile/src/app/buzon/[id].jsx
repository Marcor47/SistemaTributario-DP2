import { useEffect, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useLocalSearchParams, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../../components/ui/Cabecera'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import { abrirDocumento, cambiarArchivado, cambiarDestacado, descargarDocumento, marcarNoLeido } from '../../services/buzonService'
import { fechaHoraBuzon, TIPOS_DOCUMENTO } from '../../utils/buzon'
import { colores, radios, texto } from '../../theme/tokens'

export default function Documento() {
  const { id } = useLocalSearchParams()
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [estado, setEstado] = useState({ documento: null, error: null })
  const [revision, setRevision] = useState(0)
  const [procesando, setProcesando] = useState('')
  useEffect(() => {
    const controlador = new AbortController()
    abrirDocumento(id, { signal: controlador.signal }).then((documento) => { if (!controlador.signal.aborted) setEstado({ documento, error: null }) }).catch((error) => { if (!controlador.signal.aborted) setEstado({ documento: null, error }) })
    return () => controlador.abort()
  }, [id, revision])
  async function ejecutar(nombre, accion) {
    setProcesando(nombre); setEstado((actual) => ({ ...actual, error: null }))
    try { await accion(); if (nombre === 'no-leido') router.back(); else setRevision((valor) => valor + 1) }
    catch (error) { setEstado((actual) => ({ ...actual, error })) }
    finally { setProcesando('') }
  }
  const d = estado.documento
  return <View style={estilos.raiz}><Cabecera titulo="Mensaje del buzón" onVolver={() => router.back()} />
    <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
      {!d && !estado.error ? <ActivityIndicator color={colores.marino} /> : null}
      {estado.error ? <Aviso tipo="alerta" titulo="No se pudo completar la acción" accion={<Boton titulo="Reintentar" onPress={() => setRevision((valor) => valor + 1)} />}>{estado.error.message}</Aviso> : null}
      {d ? <View style={estilos.tarjeta}><Text style={texto.pequeno}>{TIPOS_DOCUMENTO[d.tipo] || 'Documento'}</Text><Text style={texto.titulo}>{d.titulo}</Text><Text style={texto.secundario}>Enviado: {fechaHoraBuzon(d.fechaEnvio)}</Text><Text style={texto.secundario}>Primera lectura: {fechaHoraBuzon(d.fechaPrimeraLectura)}</Text>
        {d.contenido ? <Text selectable style={texto.cuerpo}>{d.contenido}</Text> : null}
        <View style={estilos.acciones}><Boton titulo={d.destacado ? 'Quitar destacado' : 'Destacar'} icono="estrella" variante="secundario" cargando={procesando === 'destacar'} onPress={() => ejecutar('destacar', () => cambiarDestacado(id, !d.destacado))} /><Boton titulo={d.archivado ? 'Restaurar' : 'Archivar'} icono="archivo" variante="secundario" cargando={procesando === 'archivar'} onPress={() => ejecutar('archivar', () => cambiarArchivado(id, !d.archivado))} /><Boton titulo="Marcar como no leído" variante="texto" cargando={procesando === 'no-leido'} onPress={() => ejecutar('no-leido', () => marcarNoLeido(id))} /></View>
        {d.urlDescarga ? <Boton titulo="Abrir documento PDF" icono="documento" cargando={procesando === 'descarga'} onPress={() => ejecutar('descarga', () => descargarDocumento(id))} /> : !d.contenido ? <Aviso>El documento está registrado, pero todavía no tiene un archivo disponible.</Aviso> : null}
      </View> : null}
    </ScrollView>
  </View>
}

const estilos = StyleSheet.create({ raiz: { flex: 1, backgroundColor: colores.fondo }, contenido: { padding: 16, gap: 16, width: '100%', maxWidth: 760, alignSelf: 'center' }, tarjeta: { padding: 20, gap: 18, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.lg }, acciones: { gap: 8 } })
