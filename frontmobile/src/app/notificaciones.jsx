import { useCallback, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Switch, Text, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../components/ui/Cabecera'
import Aviso from '../components/ui/Aviso'
import Boton from '../components/ui/Boton'
import { guardarRecordatorioVencimiento, obtenerPreferenciaAviso } from '../services/preferenciaAvisoService'
import { puedeActivar, textoDias, textoRecordatorio } from '../utils/preferenciasAvisos'
import { colores, espacio, fuentes, radios, sombra, texto } from '../theme/tokens'

// HU016 (RF-022): P12. El contribuyente consulta sus datos y decide si recibe el recordatorio de vencimiento.
export default function Notificaciones() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const [estado, setEstado] = useState({ cargando: true, datos: null, error: null })
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [revision, setRevision] = useState(0)

  useFocusEffect(useCallback(() => {
    const controlador = new AbortController()
    setEstado((actual) => ({ ...actual, cargando: true, error: null }))
    obtenerPreferenciaAviso({ signal: controlador.signal }).then((datos) => {
      if (!controlador.signal.aborted) setEstado({ cargando: false, datos, error: null })
    }).catch((error) => {
      if (!controlador.signal.aborted) setEstado((actual) => ({ ...actual, cargando: false, error }))
    })
    return () => controlador.abort()
  // La revisión fuerza una nueva consulta al reintentar aunque la pantalla conserve el foco.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision]))

  async function cambiarRecordatorio(activo) {
    if (!estado.datos || guardando) return
    setGuardando(true)
    setAviso(null)
    try {
      const actualizado = await guardarRecordatorioVencimiento(activo)
      setEstado((actual) => ({ ...actual, datos: actualizado }))
      setAviso({
        tipo: 'exito',
        titulo: activo ? 'Recordatorio activado' : 'Recordatorio desactivado',
        texto: activo
          ? `Recibirás un correo ${textoDias(actualizado.diasAnticipacion)}.`
          : 'No recibirás recordatorios de vencimiento por correo.',
      })
    } catch (error) {
      setAviso({
        tipo: 'critico',
        titulo: 'No se pudo guardar la preferencia',
        texto: error.message || 'Intenta nuevamente.',
      })
    } finally {
      setGuardando(false)
    }
  }

  const datos = estado.datos
  const volver = () => { if (router.canGoBack()) router.back(); else router.replace('/') }

  return (
    <View style={estilos.raiz}>
      <Cabecera titulo="Mis datos y notificaciones" subtitulo="Recordatorios de vencimiento" onVolver={volver} />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]}>
        {estado.cargando && !datos ? (
          <View style={estilos.centro} accessibilityRole="progressbar" accessibilityLabel="Consultando tus preferencias">
            <ActivityIndicator color={colores.marino} size="large" />
            <Text style={texto.secundario}>Consultando tus preferencias…</Text>
          </View>
        ) : estado.error && !datos ? (
          <Aviso tipo="alerta" titulo="No pudimos consultar tus preferencias"
            accion={<Boton titulo="Reintentar" onPress={() => setRevision((valor) => valor + 1)} />}>
            {estado.error.message}
          </Aviso>
        ) : datos ? (
          <>
            {estado.error && (
              <Aviso tipo="alerta" titulo="No pudimos actualizar tus preferencias"
                accion={<Boton titulo="Reintentar" onPress={() => setRevision((valor) => valor + 1)} />}>
                {estado.error.message}
              </Aviso>
            )}
            <View style={estilos.tarjeta}>
              <Text style={texto.subtitulo} accessibilityRole="header">Datos del contribuyente</Text>
              <Dato etiqueta="Nombre o razón social" valor={datos.nombreRazonSocial} />
              <Dato etiqueta="Documento" valor={`${datos.tipoDocumento || ''} ${datos.numeroDocumentoEnmascarado || ''}`.trim()} />
              <Dato etiqueta="Código de contribuyente" valor={datos.codigoContribuyente} />
            </View>

            <View style={estilos.tarjeta}>
              <Text style={texto.subtitulo} accessibilityRole="header">Correo para avisos</Text>
              <Dato etiqueta="Correo registrado" valor={datos.correo || 'Sin correo registrado'} />
              {!puedeActivar(datos) && (
                <Aviso tipo="alerta" titulo="Sin correo registrado">
                  Para recibir recordatorios, regulariza tu correo en la Municipalidad.
                </Aviso>
              )}
            </View>

            <View style={estilos.tarjeta}>
              <Text style={texto.subtitulo} accessibilityRole="header">Recordatorio de vencimiento</Text>
              <View style={estilos.filaSwitch}>
                <View style={estilos.textoSwitch}>
                  <Text style={estilos.etiqueta}>Recibir recordatorio por correo</Text>
                  <Text style={texto.secundario}>{textoRecordatorio(datos)}</Text>
                  <Text style={texto.pequeno}>Anticipación vigente: {textoDias(datos.diasAnticipacion)}.</Text>
                </View>
                <Switch
                  value={Boolean(datos.recordatorioVencimiento)}
                  onValueChange={cambiarRecordatorio}
                  disabled={guardando || estado.cargando || !puedeActivar(datos)}
                  accessibilityRole="switch"
                  accessibilityLabel="Recibir recordatorio de vencimiento"
                  accessibilityHint="Activa o desactiva el recordatorio por correo antes del vencimiento"
                  accessibilityState={{
                    checked: Boolean(datos.recordatorioVencimiento),
                    disabled: guardando || estado.cargando || !puedeActivar(datos),
                  }}
                  trackColor={{ false: colores.bordeFuerte, true: colores.marino }}
                  thumbColor={colores.blanco}
                  ios_backgroundColor={colores.bordeFuerte}
                />
              </View>
              {guardando && <Text style={texto.pequeno} accessibilityLiveRegion="polite">Guardando tu preferencia…</Text>}
            </View>

            {aviso && (
              <Aviso tipo={aviso.tipo} titulo={aviso.titulo}>{aviso.texto}</Aviso>
            )}

            <View style={estilos.info}>
              <Text style={estilos.infoTitulo}>Cómo funciona</Text>
              <Text style={texto.secundario}>
                La Municipalidad define la anticipación del aviso, entre 1 y 10 días. El recordatorio se envía
                únicamente al correo registrado.
              </Text>
              <Text style={texto.secundario}>
                Puedes activarlo o desactivarlo cuando quieras; el cambio se guarda al instante.
              </Text>
            </View>
          </>
        ) : null}
      </ScrollView>
    </View>
  )
}

function Dato({ etiqueta, valor }) {
  return (
    <View style={estilos.dato}>
      <Text style={estilos.datoEtiqueta}>{etiqueta}</Text>
      <Text style={estilos.datoValor}>{valor || '—'}</Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: espacio.lg, paddingTop: espacio.xl, gap: espacio.lg },
  centro: { alignItems: 'center', justifyContent: 'center', gap: espacio.md, paddingVertical: espacio.xxl },
  tarjeta: {
    gap: espacio.md,
    padding: espacio.lg,
    borderRadius: radios.lg,
    borderWidth: 1,
    borderColor: colores.borde,
    backgroundColor: colores.superficie,
    ...sombra,
  },
  dato: { gap: 2 },
  datoEtiqueta: { ...texto.pequeno, color: colores.textoAtenuado },
  datoValor: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.texto },
  filaSwitch: { flexDirection: 'row', alignItems: 'center', gap: espacio.lg, minHeight: 48 },
  textoSwitch: { flex: 1, gap: 4 },
  etiqueta: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc },
  info: {
    gap: espacio.sm,
    padding: espacio.lg,
    borderRadius: radios.lg,
    borderWidth: 1,
    borderColor: colores.infoBorde,
    borderLeftWidth: 5,
    borderLeftColor: colores.cian,
    backgroundColor: colores.azulSuave,
  },
  infoTitulo: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc },
})
