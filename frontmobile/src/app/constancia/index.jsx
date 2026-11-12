import { useCallback, useEffect, useRef, useState } from 'react'
import { ScrollView, StyleSheet, Text, View } from 'react-native'
import { useFocusEffect, usePreventRemove, useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Svg, { Path } from 'react-native-svg'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Cabecera from '../../components/ui/Cabecera'
import { useSesion } from '../../context/SesionContext'
import { usePagos } from '../../context/PagosContext'
import { confirmarPagoConstancia, consultarDeudaConstancia, enviarFirmaConstancia, firmaDePrueba, iniciarConstancia, iniciarPagoConstancia, listarConstancias, obtenerConstancia, obtenerDocumentoFirma, obtenerOrdenConstancia, textoDocumento } from '../../services/constanciaService'
import { checkoutValido } from '../../utils/pagos'
import { seleccionarPagoConstancia } from '../../utils/constancia'
import { colores, radios, texto } from '../../theme/tokens'

const ESTADOS_CERRADOS = ['EMITIDA', 'RECHAZADA']
const ESTADOS = {
  INICIADA: 'Pendiente de firma',
  FIRMADA: 'Firmada y enviada',
  DEUDA_VERIFICADA: 'Deuda verificada',
  ORDEN_PAGO_EMITIDA: 'Orden de pago emitida',
  PAGADA: 'Pago registrado',
  EMITIDA: 'Constancia emitida',
  RECHAZADA: 'Solicitud rechazada',
}
const SIGUIENTE_PASO = {
  FIRMADA: 'Tu solicitud fue enviada y está pendiente de revisión por el personal municipal.',
  DEUDA_VERIFICADA: 'La revisión de deuda terminó. El personal municipal debe emitir la orden de pago del trámite.',
  ORDEN_PAGO_EMITIDA: 'La orden está lista. Confirma el pago del derecho de trámite.',
  PAGADA: 'El pago fue confirmado. La municipalidad revisará la emisión de tu constancia.',
  EMITIDA: 'Tu constancia se encuentra en el buzón de documentos.',
}

export default function Constancia() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { sesion } = useSesion()
  const { abrirPasarela, guardarPago, descartarPago, ultimoPago } = usePagos()
  const [solicitudes, setSolicitudes] = useState([])
  const [deuda, setDeuda] = useState(null)
  const [solicitud, setSolicitud] = useState(null)
  const [documento, setDocumento] = useState(null)
  const [firmaPreparada, setFirmaPreparada] = useState(false)
  const [orden, setOrden] = useState(null)
  const [pagoEnPantalla, setPagoEnPantalla] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [cargaFallida, setCargaFallida] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const liberarPasarela = useRef(null)
  const procesandoConfirmacion = useRef(false)
  usePreventRemove(ocupado)

  useFocusEffect(useCallback(() => {
    let activo = true
    Promise.all([listarConstancias(), consultarDeudaConstancia()])
      .then(([lista, estadoDeuda]) => {
        if (!activo) return
        setDeuda(estadoDeuda)
        setCargaFallida(false)
        setSolicitudes(lista)
        seleccionarSolicitud(lista.find((item) => !ESTADOS_CERRADOS.includes(item.estado)) ?? lista[0] ?? null)
      })
      .catch((err) => { if (activo) { setError(err.message); setCargaFallida(true) } })
      .finally(() => { if (activo) setCargando(false) })
    return () => {
      activo = false
      liberarPasarela.current?.()
      liberarPasarela.current = null
    }
  }, []))

  const idSolicitud = solicitud?.idSolicitud
  const estadoSolicitud = solicitud?.estado

  useFocusEffect(useCallback(() => {
    if (!idSolicitud || !['FIRMADA', 'DEUDA_VERIFICADA', 'PAGADA'].includes(estadoSolicitud)) return
    let activo = true
    const intervalo = setInterval(async () => {
      try {
        const actual = await obtenerConstancia(idSolicitud)
        if (activo && actual.estado !== estadoSolicitud) {
          setSolicitud(actual)
          setSolicitudes((lista) => [actual, ...lista.filter((item) => item.idSolicitud !== actual.idSolicitud)])
        }
      } catch {
        // La siguiente consulta volverá a comprobar el estado.
      }
    }, 15000)
    return () => { activo = false; clearInterval(intervalo) }
  }, [idSolicitud, estadoSolicitud]))

  useEffect(() => {
    let activo = true
    if (idSolicitud && ['ORDEN_PAGO_EMITIDA', 'PAGADA', 'EMITIDA'].includes(estadoSolicitud)) {
      obtenerOrdenConstancia(idSolicitud)
        .then((resultado) => { if (activo) setOrden({ ...resultado, idSolicitud }) })
        .catch((err) => { if (activo) setError(err.message) })
    }
    return () => { activo = false }
  }, [idSolicitud, estadoSolicitud])

  async function ejecutar(accion) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try { await accion() } catch (err) { setError(err.message) } finally { setOcupado(false) }
  }

  function seleccionarSolicitud(nueva) {
    liberarPasarela.current?.()
    liberarPasarela.current = null
    setSolicitud(nueva)
    setDocumento(null)
    setFirmaPreparada(false)
    setPagoEnPantalla(null)
  }

  function actualizarSolicitud(nueva, conservarDocumento = false) {
    if (conservarDocumento) setSolicitud(nueva)
    else seleccionarSolicitud(nueva)
    setCargaFallida(false)
    setSolicitudes((lista) => [nueva, ...lista.filter((item) => item.idSolicitud !== nueva.idSolicitud)])
  }

  async function recargar() {
    const [lista, estadoDeuda] = await Promise.all([listarConstancias(), consultarDeudaConstancia()])
    setDeuda(estadoDeuda)
    setCargaFallida(false)
    setSolicitudes(lista)
    seleccionarSolicitud(lista.find((item) => !ESTADOS_CERRADOS.includes(item.estado)) ?? lista[0] ?? null)
  }

  function crear() {
    ejecutar(async () => {
      try {
        actualizarSolicitud(await iniciarConstancia())
      } catch (err) {
        if (err.codigo !== 'SOLICITUD_EN_CURSO') throw err
        await recargar()
      }
    })
  }

  function enviarSolicitud() {
    if (!solicitud || !documento?.documentoDePrueba || !firmaPreparada) return
    ejecutar(async () => actualizarSolicitud(await enviarFirmaConstancia(
      solicitud.idSolicitud, firmaDePrueba(documento.hashSha256), 'CERTIFICADO DE PRUEBA',
    ), true))
  }

  async function confirmarPago(id, token, idTransaccion) {
    if (procesandoConfirmacion.current) return
    procesandoConfirmacion.current = true
    // Una autorización con resultado incierto no debe volver a enviarse automáticamente.
    descartarPago(idTransaccion)
    setOcupado(true)
    setError('')
    setPagoEnPantalla(null)
    liberarPasarela.current = null
    try {
      const resultado = await confirmarPagoConstancia(id, token, idTransaccion)
      setOrden({ ...resultado.orden, idSolicitud: id })
      if (resultado.error) {
        setError('No se pudo confirmar el resultado. Consulta a la municipalidad antes de volver a pagar.')
      } else {
        actualizarSolicitud(await obtenerConstancia(id), true)
      }
    } catch (err) {
      setError(err.message || 'No se pudo comprobar el pago.')
    } finally {
      procesandoConfirmacion.current = false
      setOcupado(false)
    }
  }

  function abrirPago() {
    ejecutar(async () => {
      const id = solicitud.idSolicitud
      let actual = pagoActual ? { idSolicitud: id, pago: pagoActual } : null
      if (!actual) {
        const resultado = await iniciarPagoConstancia(id, orden.idTransaccion)
        actual = { idSolicitud: id, pago: resultado.pago }
        guardarPago(resultado.pago)
        setOrden({ ...resultado.orden, idSolicitud: id })
        setPagoEnPantalla(actual)
      }
      if (checkoutValido(actual.pago.checkout)) {
        liberarPasarela.current = abrirPasarela(actual.pago, {
          onToken: (token) => confirmarPago(id, token, actual.pago.idTransaccion),
          onError: () => setError('No se pudo cargar la pasarela. Vuelve a abrir el formulario.'),
        })
      }
    })
  }

  const pendiente = solicitud?.estado === 'INICIADA' && solicitud?.canal === 'APP'
  const haySinFirma = solicitudes.some((item) => item.estado === 'INICIADA')
  const otras = solicitudes.filter((item) => item.idSolicitud !== solicitud?.idSolicitud)
  const pagoActual = seleccionarPagoConstancia(pagoEnPantalla, orden, solicitud?.idSolicitud, ultimoPago)
  const lineasDocumento = documento?.tipoContenido?.startsWith('text/')
    ? textoDocumento(documento.contenidoBase64).trim().split('\n')
    : []
  const finEncabezado = lineasDocumento[0] === 'DOCUMENTO DE PRUEBA' ? 3 : 2

  return (
    <View style={estilos.raiz}>
      <Cabecera titulo="Constancia de No Adeudo" subtitulo="Firma tu solicitud" onVolver={() => router.back()} />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]} keyboardShouldPersistTaps="handled">
        <Text style={texto.cuerpo}>Aquí firmas la solicitud, no la constancia final. El trámite se asocia a tu sesión de contribuyente {sesion.usuario?.codigoContribuyente}.</Text>
        {error ? <Aviso tipo="critico" titulo="No se pudo continuar">{error}</Aviso> : null}
        {cargando ? <Text style={texto.cuerpo}>Cargando tus solicitudes...</Text> : null}
        {cargaFallida ? <Boton titulo="Reintentar carga" variante="secundario" onPress={() => ejecutar(recargar)} cargando={ocupado} /> : null}
        {deuda && !deuda.puedeSolicitar ? <View style={estilos.tarjeta}>
          <Aviso tipo="alerta" titulo="Tienes deuda pendiente">Debes cancelar tu deuda antes de solicitar la constancia.</Aviso>
          <Text style={texto.etiqueta}>Total pendiente: S/ {Number(deuda.total).toFixed(2)}</Text>
          {deuda.obligaciones.map((item) => <Text key={item.id} style={texto.cuerpo}>{item.tributo} · {item.periodo}: S/ {Number(item.deuda).toFixed(2)}</Text>)}
          <Boton titulo="Ver estado de cuenta y pagos" onPress={() => router.push('/estado-cuenta')} deshabilitado={ocupado} />
          <Boton titulo="Volver a consultar deuda" variante="secundario" onPress={() => ejecutar(recargar)} cargando={ocupado} />
        </View> : null}
        {!cargando && !cargaFallida && deuda?.puedeSolicitar && !haySinFirma && !solicitud ? <Boton titulo="Iniciar nueva solicitud" onPress={crear} cargando={ocupado} /> : null}
        {solicitud ? <View style={estilos.tarjeta}>
          <Text style={texto.subtitulo}>Solicitud N.° {solicitud.idSolicitud}</Text>
          <Text style={texto.cuerpo}>Estado: {ESTADOS[solicitud.estado] ?? solicitud.estado}</Text>
          {pendiente && deuda?.puedeSolicitar ? <>
            <Text style={texto.cuerpo}>Revisa el documento antes de confirmar tu firma.</Text>
            {!documento ? <Boton titulo="Ver documento de la solicitud" variante="secundario" onPress={() => ejecutar(async () => setDocumento(await obtenerDocumentoFirma(solicitud.idSolicitud)))} cargando={ocupado} /> : null}
          </> : pendiente ? null : solicitud.estado === 'INICIADA' && solicitud.canal === 'VENTANILLA'
            ? <Aviso tipo="info" titulo="Trámite en ventanilla">Esta solicitud se atiende en la municipalidad y no requiere tu firma en el portal.</Aviso>
            : solicitud.estado === 'RECHAZADA'
            ? <Aviso tipo="critico" titulo="Motivo del rechazo">{solicitud.motivoRechazo || 'Consulta con la municipalidad para conocer el motivo.'}</Aviso>
            : <Aviso tipo={solicitud.estado === 'FIRMADA' || solicitud.estado === 'EMITIDA' ? 'exito' : 'info'} titulo="Siguiente paso">{SIGUIENTE_PASO[solicitud.estado] ?? 'Consulta el estado más adelante.'}</Aviso>}
          {documento ? <>
            <View style={estilos.hoja} accessibilityLabel="Vista previa del documento de la solicitud">
              {lineasDocumento.slice(0, finEncabezado).map((linea, indice) => <Text key={indice} style={estilos.encabezadoDocumento}>{linea}</Text>)}
              {documento.datosContribuyente ? <View style={estilos.datosContribuyente}>
                <Text style={estilos.textoDocumento}>Contribuyente: {documento.datosContribuyente.nombre}</Text>
                <Text style={estilos.textoDocumento}>Código: {documento.datosContribuyente.codigo}</Text>
                <Text style={estilos.textoDocumento}>Tipo de documento: {documento.datosContribuyente.tipoDocumento}</Text>
              </View> : null}
              {lineasDocumento.slice(finEncabezado).map((linea, indice) => <Text key={indice} style={estilos.textoDocumento}>{linea}</Text>)}
              <View style={estilos.zonaFirma}>
                {firmaPreparada ? <Svg width={170} height={55} viewBox="0 0 170 55" accessibilityLabel="Firma visual de demostración">
                  <Path d="M8 39 C22 28 32 7 29 8 C24 13 17 38 26 40 C36 42 46 21 48 22 C50 24 38 40 44 41 C51 43 60 30 64 31 C68 32 61 40 67 40 C74 40 79 29 82 31 C85 32 77 42 85 41 C94 40 96 29 100 30 C104 31 98 40 104 41 C113 41 120 34 126 28 M18 48 C48 43 105 44 153 37" fill="none" stroke="#173d78" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                </Svg> : <View style={estilos.espacioFirma} />}
                <View style={estilos.lineaFirma} />
                <Text style={estilos.etiquetaFirma}>Firma del contribuyente</Text>
              </View>
            </View>
            {pendiente && deuda?.puedeSolicitar && documento.documentoDePrueba ? <>
              <Text style={texto.secundario}>La firma mostrada es de demostración y no tiene validez legal.</Text>
              <Boton titulo={firmaPreparada ? 'Documento firmado' : 'Firmar documento'} variante="secundario" onPress={() => setFirmaPreparada(true)} deshabilitado={firmaPreparada || ocupado} />
              <Boton titulo="Enviar solicitud" onPress={enviarSolicitud} deshabilitado={!firmaPreparada || ocupado} cargando={ocupado} />
            </> : pendiente && deuda?.puedeSolicitar ? <Aviso tipo="alerta" titulo="Firma no disponible">La firma real aún no está integrada en este portal.</Aviso> : null}
          </> : null}
          {solicitud.estado === 'ORDEN_PAGO_EMITIDA' && orden?.idSolicitud === solicitud.idSolicitud ? <View style={estilos.tarjeta}>
            <Text style={texto.etiqueta}>Derecho del trámite: S/ {Number(orden.monto).toFixed(2)}</Text>
            {orden.sesionIniciada && !pagoActual
              ? <Aviso tipo="alerta" titulo="Pago iniciado">Consulta con la municipalidad el resultado de esta orden antes de intentar pagar nuevamente.</Aviso>
              : pagoActual && !checkoutValido(pagoActual.checkout)
                ? <Boton titulo="Confirmar pago de prueba" onPress={() => confirmarPago(solicitud.idSolicitud, null, pagoActual.idTransaccion)} deshabilitado={!deuda?.puedeSolicitar} cargando={ocupado} />
                : <Boton titulo={pagoActual ? 'Abrir nuevamente la pasarela' : 'Pagar derecho de trámite'} onPress={abrirPago} deshabilitado={!deuda?.puedeSolicitar} cargando={ocupado} />}
          </View> : null}
          {deuda?.puedeSolicitar && !haySinFirma ? <Boton titulo="Crear otra solicitud" onPress={crear} cargando={ocupado} /> : null}
        </View> : null}
        {otras.length ? <View style={estilos.tarjeta}>
          <Text style={texto.subtitulo}>Otras solicitudes</Text>
          {otras.map((item) => <Boton
            key={item.idSolicitud}
            titulo={`Solicitud N.° ${item.idSolicitud} · ${ESTADOS[item.estado] ?? item.estado}`}
            variante="secundario"
            onPress={() => seleccionarSolicitud(item)}
            deshabilitado={ocupado}
          />)}
        </View> : null}
        {!cargando && !solicitudes.length && !cargaFallida ? <Text style={texto.secundario}>Aún no tienes solicitudes de constancia.</Text> : null}
        {!cargando && !cargaFallida ? <Boton titulo="Ir a mi buzón" variante="secundario" onPress={() => router.push('/buzon')} deshabilitado={ocupado} /> : null}
      </ScrollView>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 16 },
  tarjeta: { padding: 16, gap: 14, backgroundColor: colores.superficie, borderColor: colores.borde, borderWidth: 1, borderRadius: radios.lg },
  hoja: { width: '100%', maxWidth: 560, minHeight: 460, alignSelf: 'center', paddingVertical: 30, paddingHorizontal: 24, backgroundColor: '#fff', borderColor: '#c5ccd5', borderWidth: 1, shadowColor: '#172334', shadowOpacity: 0.15, shadowRadius: 8, shadowOffset: { width: 0, height: 3 }, elevation: 3 },
  encabezadoDocumento: { ...texto.etiqueta, textAlign: 'center', color: '#27364a', marginBottom: 12 },
  datosContribuyente: { marginTop: 8, marginBottom: 10 },
  textoDocumento: { ...texto.cuerpo, color: '#27364a', marginBottom: 8 },
  zonaFirma: { alignItems: 'center', marginTop: 34 },
  espacioFirma: { height: 55 },
  lineaFirma: { width: 180, maxWidth: '90%', borderTopWidth: 1, borderTopColor: '#596575' },
  etiquetaFirma: { ...texto.pequeno, textAlign: 'center', marginTop: 6 },
})
