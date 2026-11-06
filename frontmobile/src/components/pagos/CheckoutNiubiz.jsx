import { useEffect, useMemo, useRef, useState } from 'react'
import { BackHandler, StyleSheet, View } from 'react-native'
import { WebView } from 'react-native-webview'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Cabecera from '../ui/Cabecera'
import EstadoCargaPasarela from './EstadoCargaPasarela'
import { configuracionNiubiz, htmlNiubiz, leerMensajeNiubiz, serializarNiubiz } from '../../utils/niubiz'

const BASE_URL = 'https://localhost/'

// Un WebView durante la sesión. Solo el puente cambia la configuración de cada compra.
export default function CheckoutNiubiz({ pago, abierto, onToken, onCerrar, onError }) {
  const { bottom } = useSafeAreaInsets()
  const marco = useRef(null)
  const [userAgent, setUserAgent] = useState(null)
  const [preparado, setPreparado] = useState(0)
  const [fallo, setFallo] = useState(false)
  const [demorado, setDemorado] = useState(false)
  const [revision, setRevision] = useState(0)
  const html = useMemo(() => htmlNiubiz(), [])
  const pedido = useMemo(() => pago?.estado === 'INICIADA' ? configuracionNiubiz(pago) : null, [pago])
  const source = useMemo(() => ({
    html: userAgent ? html : '<script>window.ReactNativeWebView.postMessage(JSON.stringify({tipo:"agente",valor:navigator.userAgent}));</script>',
    baseUrl: BASE_URL,
  }), [html, userAgent])

  function reintentar() {
    setPreparado(0)
    setFallo(false)
    setDemorado(false)
    setRevision(valor => valor + 1)
  }
  useEffect(() => {
    if (!preparado || fallo) return
    const script = pedido ? `window.configurarPago(${serializarNiubiz(pedido)});${abierto ? `window.abrirPago(${JSON.stringify(pedido.operacion)});` : ''}`
      : `window.retirarPago(${JSON.stringify(String(pago?.idTransaccion || ''))});`
    marco.current?.injectJavaScript(script + 'true;')
  }, [preparado, fallo, pedido, abierto, pago?.idTransaccion])
  useEffect(() => {
    if (!abierto || preparado) return
    const espera = setTimeout(() => setDemorado(true), 20000)
    return () => clearTimeout(espera)
  }, [abierto, preparado, revision])
  useEffect(() => {
    if (!abierto) return
    const suscripcion = BackHandler.addEventListener('hardwareBackPress', () => { onCerrar(); return true })
    return () => suscripcion.remove()
  }, [abierto, onCerrar])

  function recibir(evento) {
    if (!userAgent) {
      try {
        const agente = JSON.parse(evento.nativeEvent.data)
        if (agente.tipo === 'agente' && typeof agente.valor === 'string') setUserAgent(agente.valor.replace(/Version\/4\.0\s*/g, ''))
      } catch { /* Ignorar mensajes ajenos al inicio del WebView. */ }
      return
    }
    const mensaje = leerMensajeNiubiz(evento.nativeEvent.data)
    if (mensaje?.tipo === 'preparado') { setPreparado(valor => valor + 1); setFallo(false) }
    else if (mensaje?.tipo === 'documento') setDemorado(false)
    else if (mensaje?.tipo === 'error' && !mensaje.operacion) setFallo(true)
    else if (mensaje?.operacion === String(pago?.idTransaccion)) {
      if (mensaje.tipo === 'configurado') setDemorado(false)
      else if (mensaje.tipo === 'demorado') setDemorado(true)
      else if (mensaje.tipo === 'reinicio') reintentar()
      else if (abierto && mensaje.tipo === 'token') onToken(mensaje)
      else if (abierto && mensaje.tipo === 'cancelado') onCerrar()
      else if (abierto && mensaje.tipo === 'error') onError(mensaje.operacion)
    }
  }
  return <View style={[estilos.raiz, { paddingBottom: bottom }, !abierto && estilos.precarga]}
    pointerEvents={abierto ? 'auto' : 'none'} accessibilityElementsHidden={!abierto}
    importantForAccessibility={abierto ? 'yes' : 'no-hide-descendants'} accessibilityViewIsModal={abierto}>
    <Cabecera titulo="Pagar" onVolver={onCerrar} />
    {(fallo || demorado) && <EstadoCargaPasarela fallo={fallo} onReintentar={reintentar} />}
    <WebView key={revision} ref={marco} style={estilos.web} originWhitelist={['*']} userAgent={userAgent || undefined}
      source={source} cacheEnabled
      onMessage={recibir} onError={() => setFallo(true)} mixedContentMode="never" javaScriptEnabled
      onContentProcessDidTerminate={() => setFallo(true)} onRenderProcessGone={() => setFallo(true)}
      onShouldStartLoadWithRequest={({ url }) => {
        if (url === pago?.checkout?.timeoutUrl) { if (abierto) onCerrar(); return false }
        if (url === pago?.checkout?.callbackUrl) { setFallo(true); return false }
        return url === 'about:blank' || url.startsWith('https://')
      }} />
  </View>
}

const estilos = StyleSheet.create({
  raiz: { ...StyleSheet.absoluteFill, zIndex: 100, elevation: 12, backgroundColor: '#fff' },
  web: { flex: 1 },
  precarga: { opacity: 0, zIndex: -1, elevation: 0 },
})
