import { useEffect, useMemo, useRef, useState } from 'react'
import Cabecera from '../ui/Cabecera'
import EstadoCargaPasarela from './EstadoCargaPasarela'
import { configuracionNiubiz, htmlNiubiz, leerMensajeNiubiz } from '../../utils/niubiz'

// Instancia única en PagosProvider: la navegación y los nuevos pagos no cambian srcDoc.
export default function CheckoutNiubiz({ pago, abierto, onToken, onCerrar, onError }) {
  const marco = useRef(null)
  const [preparado, setPreparado] = useState(0)
  const [fallo, setFallo] = useState(false)
  const [demorado, setDemorado] = useState(false)
  const [revision, setRevision] = useState(0)
  const html = useMemo(() => htmlNiubiz(), [])
  const pedido = useMemo(() => pago?.estado === 'INICIADA' ? configuracionNiubiz(pago) : null, [pago])

  function reintentar() {
    setPreparado(0)
    setFallo(false)
    setDemorado(false)
    setRevision(valor => valor + 1)
  }
  useEffect(() => {
    const recibir = evento => {
      if (evento.source !== marco.current?.contentWindow) return
      const mensaje = leerMensajeNiubiz(evento.data)
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
    window.addEventListener('message', recibir)
    return () => window.removeEventListener('message', recibir)
  }, [pago?.idTransaccion, abierto, onToken, onError, onCerrar])
  useEffect(() => {
    if (!preparado || fallo) return
    const enviar = valor => marco.current?.contentWindow?.postMessage(JSON.stringify(valor), '*')
    if (pedido) {
      enviar({ tipo: 'configurar', datos: pedido })
      if (abierto) enviar({ tipo: 'abrir', operacion: pedido.operacion })
    } else enviar({ tipo: 'retirar', operacion: String(pago?.idTransaccion || '') })
  }, [preparado, fallo, pedido, abierto, pago?.idTransaccion])
  useEffect(() => {
    if (!abierto || preparado) return
    const espera = setTimeout(() => setDemorado(true), 20000)
    return () => clearTimeout(espera)
  }, [abierto, preparado, revision])
  useEffect(() => {
    if (!abierto) return
    const cerrar = evento => { if (evento.key === 'Escape') onCerrar() }
    window.addEventListener('keydown', cerrar)
    return () => window.removeEventListener('keydown', cerrar)
  }, [abierto, onCerrar])
  return <div role="dialog" aria-label="Pago en línea" aria-modal={abierto} aria-hidden={!abierto} style={{ position: 'absolute', inset: 0, zIndex: 100, display: 'flex', flexDirection: 'column', background: '#fff', visibility: abierto ? 'visible' : 'hidden' }}>
    <Cabecera titulo="Pagar" onVolver={onCerrar} />
    {(fallo || demorado) && <EstadoCargaPasarela fallo={fallo} onReintentar={reintentar} />}
    <iframe key={revision} ref={marco} title="Formulario de pago" srcDoc={html}
      onLoad={() => marco.current?.contentWindow?.postMessage(JSON.stringify({ tipo: 'estado' }), '*')}
      style={{ flex: 1, minHeight: 0, border: 0, width: '100%' }} />
  </div>
}
