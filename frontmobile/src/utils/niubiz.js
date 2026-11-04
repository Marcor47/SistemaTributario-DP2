import { checkoutValido, idPagoValido } from './pagos.js'

export function configuracionNiubiz(pago) {
  if (!pago || !idPagoValido(pago.idTransaccion) || pago.estado !== 'INICIADA' || pago.dePrueba !== true
    || !checkoutValido(pago.checkout) || !Number.isFinite(Number(pago.montoTotal)) || Number(pago.montoTotal) <= 0) {
    throw Object.assign(new Error('La sesión de pago no está disponible.'), { codigo: 'PASARELA_NO_DISPONIBLE' })
  }
  const c = pago.checkout
  return { operacion: String(pago.idTransaccion), config: {
    sessiontoken: c.sessionKey, merchantid: c.merchantId, purchasenumber: c.purchaseNumber,
    amount: Number(pago.montoTotal).toFixed(2), currency: 'PEN', channel: 'web', expirationminutes: '20',
    timeouturl: c.timeoutUrl, action: c.callbackUrl, merchantname: 'Municipalidad San Miguel',
    formbuttoncolor: '#17406B',
  } }
}

export function serializarNiubiz(valor) {
  return JSON.stringify(valor).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
}

// Las respuestas con datos de una operación siempre llevan su identificador, incluido el token.
export function leerMensajeNiubiz(datos) {
  let mensaje
  try { mensaje = JSON.parse(datos) } catch { return null }
  if (['preparado', 'documento'].includes(mensaje?.tipo)) return { tipo: mensaje.tipo }
  if (mensaje?.tipo === 'error' && mensaje.codigo !== 'RESPUESTA_INVALIDA') return { tipo: 'error' }
  if (typeof mensaje?.operacion !== 'string' || !idPagoValido(mensaje.operacion)) return null
  if (mensaje.tipo === 'error') return { tipo: 'error', codigo: mensaje.codigo, operacion: mensaje.operacion }
  if (['cancelado', 'demorado', 'configurado', 'reinicio'].includes(mensaje.tipo)) {
    return { tipo: mensaje.tipo, operacion: mensaje.operacion }
  }
  if (mensaje.tipo !== 'token' || mensaje.channel !== 'web'
    || typeof mensaje.token !== 'string' || !mensaje.token.trim() || mensaje.token.length > 4096) return null
  return { tipo: 'token', token: mensaje.token, operacion: mensaje.operacion }
}

// HTML estable: el SDK se descarga una vez. Los datos de cada pago llegan por el puente, sin recargarlo.
export function htmlNiubiz(pago) {
  const inicial = pago ? serializarNiubiz(configuracionNiubiz(pago)) : 'null'
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
  <link rel="preconnect" href="https://static-content-qas.vnforapps.com" crossorigin>
  <link rel="preconnect" href="https://apisandbox.vnforappstest.com" crossorigin>
  <style>body{margin:0;background:#fff;color:#17406B;font:16px Arial}.carga{min-height:100vh;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:16px}.rueda{width:32px;height:32px;border:3px solid #e2e8f0;border-top-color:#17406B;border-radius:50%;animation:girar 1s linear infinite}@keyframes girar{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.rueda{animation:none}}</style></head>
  <body><div class="carga" role="status"><div class="rueda" aria-hidden="true"></div><p>Cargando medio de pago…</p></div>
  <script>
    var preparada = false, vigente = null, solicitud = ${inicial}, aperturaSolicitada = null;
    var visorAbierto = false, espera, tokensEntregados = [];
    function avisar(mensaje) {
      var texto = JSON.stringify(mensaje);
      if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(texto);
      else window.parent.postMessage(texto, '*');
    }
    function configurarPago(datos) {
      if (!datos || !datos.operacion || !datos.config) return;
      solicitud = datos;
      if (!preparada) return;
      if (vigente && vigente.operacion === datos.operacion && vigente.config.sessiontoken === datos.config.sessiontoken) return;
      // Un SDK todavía abierto no se reasigna a otra compra. El contenedor creará una instancia limpia.
      if (visorAbierto) { avisar({tipo:'reinicio',operacion:datos.operacion}); return; }
      vigente = datos;
      var configuracion = Object.assign({}, datos.config);
      VisanetCheckout.configure(configuracion);
      VisanetCheckout.configuration.complete = function(parametros) {
        clearTimeout(espera);
        if (vigente !== datos) return;
        if (parametros && parametros.channel && parametros.channel !== 'web') {
          avisar({tipo:'error',codigo:'RESPUESTA_INVALIDA',operacion:datos.operacion}); return;
        }
        var token = parametros && (parametros.token || parametros.transactionToken);
        if (typeof token !== 'string' || !token.trim() || token.length > 4096) {
          avisar({tipo:'error',codigo:'RESPUESTA_INVALIDA',operacion:datos.operacion}); return;
        }
        if (tokensEntregados.indexOf(token) !== -1) return;
        tokensEntregados.push(token);
        if (tokensEntregados.length > 64) tokensEntregados.shift();
        avisar({tipo:'token',token:token,channel:parametros.channel || 'web',operacion:datos.operacion});
      };
      VisanetCheckout.configuration.cancel = function() {
        clearTimeout(espera); visorAbierto = false;
        if (vigente === datos) avisar({tipo:'cancelado',operacion:datos.operacion});
      };
      avisar({tipo:'configurado',operacion:datos.operacion});
      if (aperturaSolicitada === datos.operacion) abrirPago(datos.operacion);
    }
    function abrirPago(operacion) {
      operacion = String(operacion || (solicitud && solicitud.operacion) || '');
      aperturaSolicitada = operacion;
      if (!preparada || !vigente || vigente.operacion !== operacion || visorAbierto) return;
      visorAbierto = true;
      VisanetCheckout.open();
      clearTimeout(espera);
      espera = setTimeout(function() {
        var marco = document.getElementById('visaNetJS');
        if (vigente && vigente.operacion === operacion && (!marco || getComputedStyle(marco).visibility === 'hidden')) {
          avisar({tipo:'demorado',operacion:operacion});
        }
      }, 20000);
    }
    function retirarPago(operacion) {
      clearTimeout(espera);
      if (solicitud && solicitud.operacion === operacion) solicitud = null;
      if (vigente && vigente.operacion === operacion) vigente = null;
      if (aperturaSolicitada === operacion) aperturaSolicitada = null;
    }
    window.configurarPago = configurarPago;
    window.abrirPago = abrirPago;
    window.retirarPago = retirarPago;
    window.addEventListener('message', function(evento) {
      try {
        var mensaje = JSON.parse(evento.data), marco = document.getElementById('visaNetJS');
        if (marco && evento.source === marco.contentWindow) {
          if (mensaje.isReady === true) { clearTimeout(espera); avisar({tipo:'documento'}); }
          if (mensaje.method === 'close' || mensaje.method === 'cancel') visorAbierto = false;
        }
        if (evento.source === window.parent) {
          if (mensaje.tipo === 'configurar') configurarPago(mensaje.datos);
          else if (mensaje.tipo === 'abrir') abrirPago(mensaje.operacion);
          else if (mensaje.tipo === 'retirar') retirarPago(mensaje.operacion);
          else if (mensaje.tipo === 'estado' && preparada) avisar({tipo:'preparado'});
        }
      } catch (error) { /* Mensaje ajeno al protocolo de la pasarela. */ }
    });
    function preparar() {
      preparada = true;
      avisar({tipo:'preparado'});
      if (solicitud) configurarPago(solicitud);
    }
  </script><script src="https://static-content-qas.vnforapps.com/env/sandbox/js/checkout.js" fetchpriority="high" onload="preparar()" onerror="avisar({tipo:'error'})"></script></body></html>`
}
