import { useCallback, useEffect, useRef, useState } from 'react'
import { BackHandler, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Cabecera from '../../components/ui/Cabecera'
import CampoTexto from '../../components/ui/CampoTexto'
import Icono from '../../components/ui/Icono'
import HojaAdjuntar from '../../components/solicitudPredio/HojaAdjuntar'
import OpcionTarjeta from '../../components/solicitudPredio/OpcionTarjeta'
import Pasos from '../../components/solicitudPredio/Pasos'
import TarjetaRequisito from '../../components/solicitudPredio/TarjetaRequisito'
import { PREDIOS, TRAMITES } from '../../components/solicitudPredio/catalogos'
import { formatearTamano, seleccionarArchivo } from '../../services/adjuntos'
import { MAXIMO_ADJUNTOS, enviarSolicitud, obtenerRequisitos } from '../../services/solicitudPredioService'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

const PASOS = ['Trámite', 'Predio', 'Documentos', 'Revisar']

// Errores del backend que se corrigen en un paso anterior.
const PASO_POR_ERROR = {
  PREDIO_REQUERIDO: 1,
  REQUISITOS_FALTANTES: 2,
  ADJUNTOS_INVALIDOS: 2,
  REQUISITO_INVALIDO: 2,
  ARCHIVO_INVALIDO: 2,
  TIPO_ARCHIVO_INVALIDO: 2,
}

export default function SolicitudPredio() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const scroll = useRef(null)
  const origenPendiente = useRef(null)

  const [paso, setPaso] = useState(0)
  const [tipoTramite, setTipoTramite] = useState(null)
  const [tipoPredio, setTipoPredio] = useState(null)
  const [codigoPredio, setCodigoPredio] = useState('')
  const [requisitos, setRequisitos] = useState({ estado: 'inactivo', clave: null, lista: [], error: '' })
  const [adjuntos, setAdjuntos] = useState({})
  const [erroresAdjunto, setErroresAdjunto] = useState({})
  const [errores, setErrores] = useState({})
  const [hoja, setHoja] = useState({ visible: false, requisito: null })
  const [enviando, setEnviando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState(null)

  const hayAdjuntos = Object.keys(adjuntos).length > 0
  const tramite = TRAMITES.find((t) => t.valor === tipoTramite)
  const predio = PREDIOS.find((p) => p.valor === tipoPredio)

  const irAPaso = useCallback((n) => {
    setPaso(n)
    scroll.current?.scrollTo({ y: 0, animated: false })
  }, [])

  const volver = useCallback(() => {
    if (enviando) return true
    if (paso > 0) {
      irAPaso(paso - 1)
      return true
    }
    router.back()
    return true
  }, [enviando, paso, irAPaso, router])

  // El botón atrás de Android retrocede un paso antes de salir del trámite.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', volver)
    return () => sub.remove()
  }, [volver])

  const cargarRequisitos = useCallback(async (tramiteSel, predioSel) => {
    const clave = `${tramiteSel}|${predioSel}`
    setRequisitos({ estado: 'cargando', clave, lista: [], error: '' })
    try {
      const lista = await obtenerRequisitos(tramiteSel, predioSel)
      setRequisitos({ estado: 'listo', clave, lista, error: '' })
    } catch (err) {
      setRequisitos({ estado: 'error', clave, lista: [], error: err.message })
    }
  }, [])

  // Cambiar el trámite o el tipo de predio cambia los documentos requeridos.
  function limpiarDocumentos() {
    setAdjuntos({})
    setErroresAdjunto({})
    setRequisitos({ estado: 'inactivo', clave: null, lista: [], error: '' })
  }

  function elegirTramite(valor) {
    if (valor === tipoTramite) return
    setTipoTramite(valor)
    setErrores({})
    limpiarDocumentos()
  }

  function elegirPredio(valor) {
    if (valor === tipoPredio) return
    setTipoPredio(valor)
    setErrores((e) => ({ ...e, predio: undefined }))
    limpiarDocumentos()
  }

  function validar(n) {
    const e = {}
    if (n === 0 && !tipoTramite) e.tramite = 'Elige el tipo de trámite para continuar.'
    if (n === 1) {
      if (!tipoPredio) e.predio = 'Elige el tipo de predio para continuar.'
      if (tipoTramite === 'TRASPASO' && !codigoPredio.trim()) e.codigo = 'Ingresa el código del predio que se traspasa.'
    }
    if (n === 2) {
      if (requisitos.estado !== 'listo') {
        e.documentos = 'Espera a que se carguen los documentos requeridos.'
      } else {
        const faltan = {}
        requisitos.lista.filter((r) => r.obligatorio && !adjuntos[r.id]).forEach((r) => {
          faltan[r.id] = 'Adjunta este documento obligatorio.'
        })
        setErroresAdjunto(faltan)
        const total = Object.keys(adjuntos).length
        if (Object.keys(faltan).length) {
          e.documentos = Object.keys(faltan).length === 1
            ? 'Falta 1 documento obligatorio.'
            : `Faltan ${Object.keys(faltan).length} documentos obligatorios.`
        } else if (total === 0) {
          e.documentos = 'Adjunta al menos un documento.'
        } else if (total > MAXIMO_ADJUNTOS) {
          e.documentos = `Puedes adjuntar como máximo ${MAXIMO_ADJUNTOS} documentos.`
        }
      }
    }
    setErrores(e)
    return Object.keys(e).length === 0
  }

  function continuar() {
    if (!validar(paso)) {
      scroll.current?.scrollTo({ y: 0, animated: true })
      return
    }
    const siguiente = paso + 1
    if (siguiente === 2 && requisitos.clave !== `${tipoTramite}|${tipoPredio}`) {
      cargarRequisitos(tipoTramite, tipoPredio)
    }
    setErrorEnvio(null)
    irAPaso(siguiente)
  }

  async function adjuntar(origen, requisito) {
    try {
      const r = await seleccionarArchivo(origen)
      if (!r) return
      if (r.error) {
        setErroresAdjunto((e) => ({ ...e, [requisito.id]: r.error }))
        return
      }
      setAdjuntos((a) => ({ ...a, [requisito.id]: r.archivo }))
      setErroresAdjunto((e) => ({ ...e, [requisito.id]: undefined }))
      setErrores((e) => ({ ...e, documentos: undefined }))
    } catch {
      setErroresAdjunto((e) => ({ ...e, [requisito.id]: 'No se pudo abrir el archivo. Intenta nuevamente.' }))
    }
  }

  // iOS no abre el selector mientras la hoja sigue en pantalla: espera a onDismiss.
  // En web el selector debe abrirse dentro del mismo toque, y en Android puede abrirse de inmediato.
  function elegirOrigen(origen) {
    const requisito = hoja.requisito
    setHoja((h) => ({ ...h, visible: false }))
    if (Platform.OS === 'ios') {
      origenPendiente.current = { origen, requisito }
    } else {
      adjuntar(origen, requisito)
    }
  }

  function alCerrarHoja() {
    const pendiente = origenPendiente.current
    origenPendiente.current = null
    if (pendiente) adjuntar(pendiente.origen, pendiente.requisito)
  }

  function quitar(requisito) {
    setAdjuntos((a) => {
      const copia = { ...a }
      delete copia[requisito.id]
      return copia
    })
  }

  async function enviar() {
    if (enviando) return
    setEnviando(true)
    setErrorEnvio(null)
    try {
      const lista = requisitos.lista.filter((r) => adjuntos[r.id]).map((r) => ({ idRequisito: r.id, archivo: adjuntos[r.id] }))
      const respuesta = await enviarSolicitud({ tipoTramite, tipoPredio, codigoPredio, adjuntos: lista })
      router.replace({
        pathname: '/solicitud-predio/enviada',
        params: { id: String(respuesta.idSolicitud), tramite: tipoTramite, predio: tipoPredio, fecha: respuesta.fechaEnvio ?? '' },
      })
    } catch (err) {
      setEnviando(false)
      // Un 401 lo atiende la sesión: Stack.Protected lleva al login.
      if (err.estado === 401) return
      const destino = PASO_POR_ERROR[err.codigo]
      if (destino === 1) setErrores({ codigo: err.message })
      if (err.codigo === 'REQUISITO_INVALIDO') cargarRequisitos(tipoTramite, tipoPredio)
      setErrorEnvio({ codigo: err.codigo, mensaje: err.message })
      if (destino != null) irAPaso(destino)
      else scroll.current?.scrollTo({ y: 0, animated: true })
    }
  }

  const avisoEnvio = errorEnvio ? (
    <Aviso tipo="critico" titulo="No se pudo enviar la solicitud" style={estilos.bloque}>{errorEnvio.mensaje}</Aviso>
  ) : null

  return (
    <View style={estilos.raiz}>
      <Cabecera titulo="Registro o traspaso de predio" onVolver={volver}>
        <Pasos pasos={PASOS} actual={paso} />
      </Cabecera>

      <KeyboardAvoidingView style={estilos.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView ref={scroll} contentContainerStyle={estilos.contenido} keyboardShouldPersistTaps="handled">
          {paso === 0 && (
            <View accessibilityRole="radiogroup">
              <Text style={texto.subtitulo} accessibilityRole="header">¿Qué trámite deseas realizar?</Text>
              <Text style={[texto.secundario, estilos.desc]}>Te pediremos los documentos según el trámite que elijas.</Text>
              {errores.tramite ? <Aviso tipo="critico" style={estilos.bloque}>{errores.tramite}</Aviso> : null}
              <View style={estilos.opciones}>
                {TRAMITES.map((t) => (
                  <OpcionTarjeta key={t.valor} {...t} seleccionada={tipoTramite === t.valor} onPress={() => elegirTramite(t.valor)} />
                ))}
              </View>
            </View>
          )}

          {paso === 1 && (
            <View>
              <Text style={texto.subtitulo} accessibilityRole="header">¿Qué tipo de predio es?</Text>
              <Text style={[texto.secundario, estilos.desc]}>Elige la opción que mejor describe tu predio.</Text>
              {errores.predio ? <Aviso tipo="critico" style={estilos.bloque}>{errores.predio}</Aviso> : null}
              {hayAdjuntos ? (
                <Aviso tipo="alerta" style={estilos.bloque}>Si cambias el tipo de predio, tendrás que adjuntar de nuevo los documentos.</Aviso>
              ) : null}
              <View style={estilos.opciones} accessibilityRole="radiogroup">
                {PREDIOS.map((p) => (
                  <OpcionTarjeta key={p.valor} {...p} compacta seleccionada={tipoPredio === p.valor} onPress={() => elegirPredio(p.valor)} />
                ))}
              </View>
              <View style={[estilos.tarjeta, estilos.bloqueArriba]}>
                <CampoTexto
                  etiqueta={tipoTramite === 'TRASPASO' ? 'Código del predio' : 'Código del predio (opcional)'}
                  value={codigoPredio}
                  onChangeText={(v) => { setCodigoPredio(v); if (errores.codigo) setErrores((e) => ({ ...e, codigo: undefined })) }}
                  error={errores.codigo}
                  ayuda={tipoTramite === 'TRASPASO'
                    ? 'Es obligatorio para el traspaso. Ejemplo: 014502-01.'
                    : 'Escríbelo si tu predio ya tiene un código. Ejemplo: 014502-01.'}
                  placeholder="014502-01"
                  autoCapitalize="characters"
                  autoCorrect={false}
                  maxLength={12}
                  returnKeyType="done"
                />
              </View>
            </View>
          )}

          {paso === 2 && (
            <View>
              <Text style={texto.subtitulo} accessibilityRole="header">Adjunta tus documentos</Text>
              <Text style={[texto.secundario, estilos.desc]}>
                {tramite?.titulo} · predio {predio?.titulo.toLowerCase()}. Usa PDF, JPG o PNG de hasta 10 MB.
              </Text>
              {errorEnvio ? avisoEnvio : null}
              {errores.documentos ? <Aviso tipo="critico" style={estilos.bloque}>{errores.documentos}</Aviso> : null}

              {requisitos.estado === 'cargando' && (
                <View style={estilos.opciones} accessibilityLabel="Cargando documentos requeridos">
                  {[0, 1, 2].map((i) => <View key={i} style={estilos.esqueleto} />)}
                </View>
              )}

              {requisitos.estado === 'error' && (
                <Aviso
                  tipo="critico"
                  titulo="No pudimos cargar los documentos requeridos"
                  accion={<Boton titulo="Reintentar" icono="reintentar" variante="secundario" onPress={() => cargarRequisitos(tipoTramite, tipoPredio)} style={estilos.reintentar} />}
                >
                  {requisitos.error}
                </Aviso>
              )}

              {requisitos.estado === 'listo' && requisitos.lista.length === 0 && (
                <Aviso tipo="alerta">
                  No hay documentos configurados para este trámite. Acércate a la Gerencia de Rentas para realizarlo.
                </Aviso>
              )}

              {requisitos.estado === 'listo' && requisitos.lista.length > 0 && (
                <>
                  <Text style={[texto.pequeno, estilos.contador]}>
                    {requisitos.lista.filter((r) => adjuntos[r.id]).length} de {requisitos.lista.length} documentos adjuntos
                  </Text>
                  <View style={estilos.opciones}>
                    {requisitos.lista.map((r) => (
                      <TarjetaRequisito
                        key={r.id}
                        requisito={r}
                        archivo={adjuntos[r.id]}
                        error={erroresAdjunto[r.id]}
                        onAdjuntar={() => setHoja({ visible: true, requisito: r })}
                        onQuitar={() => quitar(r)}
                      />
                    ))}
                  </View>
                </>
              )}
            </View>
          )}

          {paso === 3 && (
            <View>
              <Text style={texto.subtitulo} accessibilityRole="header">Revisa tu solicitud</Text>
              <Text style={[texto.secundario, estilos.desc]}>Confirma que los datos sean correctos antes de enviar.</Text>
              {avisoEnvio}
              <View style={estilos.tarjeta}>
                <FilaResumen etiqueta="Trámite" valor={tramite?.titulo} onEditar={() => irAPaso(0)} deshabilitado={enviando} />
                <FilaResumen
                  etiqueta="Predio"
                  valor={`${predio?.titulo}${codigoPredio.trim() ? ` · código ${codigoPredio.trim()}` : ''}`}
                  onEditar={() => irAPaso(1)}
                  deshabilitado={enviando}
                />
                <FilaResumen etiqueta="Documentos" onEditar={() => irAPaso(2)} deshabilitado={enviando} ultima>
                  {requisitos.lista.filter((r) => adjuntos[r.id]).map((r) => (
                    <View key={r.id} style={estilos.docResumen}>
                      <Icono nombre="check-circulo" color={colores.exito} tamano={18} />
                      <View style={estilos.flex}>
                        <Text style={estilos.docNombre}>{r.nombre}</Text>
                        <Text style={texto.pequeno} numberOfLines={1} ellipsizeMode="middle">
                          {adjuntos[r.id].nombre} · {formatearTamano(adjuntos[r.id].tamano)}
                        </Text>
                      </View>
                    </View>
                  ))}
                </FilaResumen>
              </View>
              <Aviso tipo="info" style={estilos.bloqueArriba}>
                Al enviar, tu solicitud quedará en revisión y la municipalidad evaluará los documentos adjuntos.
              </Aviso>
            </View>
          )}
        </ScrollView>

        <View style={[estilos.barra, { paddingBottom: bottom + 12 }]}>
          {paso > 0 && (
            <Boton titulo="Atrás" variante="secundario" onPress={volver} deshabilitado={enviando} style={estilos.atras} />
          )}
          {paso < 3 ? (
            <Boton titulo="Continuar" onPress={continuar} style={estilos.flex} />
          ) : (
            <Boton titulo="Enviar solicitud" tituloCargando="Enviando…" icono="check" cargando={enviando} onPress={enviar} style={estilos.flex} />
          )}
        </View>
      </KeyboardAvoidingView>

      <HojaAdjuntar
        visible={hoja.visible}
        requisito={hoja.requisito}
        onElegir={elegirOrigen}
        onCerrar={() => setHoja((h) => ({ ...h, visible: false }))}
        onDismiss={alCerrarHoja}
      />
    </View>
  )
}

function FilaResumen({ etiqueta, valor, onEditar, deshabilitado, ultima, children }) {
  return (
    <View style={[estilos.fila, !ultima && estilos.filaBorde]}>
      <View style={estilos.flex}>
        <Text style={estilos.filaEtiqueta}>{etiqueta}</Text>
        {valor ? <Text style={estilos.filaValor}>{valor}</Text> : null}
        {children ? <View style={estilos.docs}>{children}</View> : null}
      </View>
      <Boton titulo="Editar" icono="lapiz" variante="texto" onPress={onEditar} deshabilitado={deshabilitado} accessibilityHint={`Editar ${etiqueta.toLowerCase()}`} style={estilos.editar} />
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  flex: { flex: 1 },
  contenido: { padding: 16, paddingTop: 24, paddingBottom: 32 },
  desc: { marginTop: 4, marginBottom: 16 },
  bloque: { marginBottom: 16 },
  bloqueArriba: { marginTop: 16 },
  opciones: { gap: 12 },
  tarjeta: {
    padding: 16, backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  contador: { marginBottom: 10 },
  esqueleto: { height: 124, borderRadius: radios.lg, backgroundColor: '#E3EAF3' },
  reintentar: { alignSelf: 'flex-start', marginTop: 8 },
  fila: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, paddingVertical: 12 },
  filaBorde: { borderBottomWidth: 1, borderBottomColor: colores.borde },
  filaEtiqueta: { fontFamily: fuentes.negrita, fontSize: 13, color: colores.textoAtenuado, textTransform: 'uppercase', letterSpacing: 0.6 },
  filaValor: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 24, color: colores.marinoOsc, marginTop: 2 },
  docs: { gap: 10, marginTop: 8 },
  docResumen: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  docNombre: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 21, color: colores.texto },
  editar: { paddingHorizontal: 10 },
  barra: {
    flexDirection: 'row', gap: 12, paddingHorizontal: 16, paddingTop: 12, backgroundColor: colores.superficie,
    borderTopWidth: 1, borderTopColor: colores.borde,
  },
  atras: { minWidth: 110 },
})
