import assert from 'node:assert/strict'
import test from 'node:test'
import vm from 'node:vm'
import { checkoutValido, normalizarPago, validarPasarela } from '../src/utils/pagos.js'
import { configuracionNiubiz, htmlNiubiz, leerMensajeNiubiz } from '../src/utils/niubiz.js'
import { actualizarPasarela, PASARELA_INICIAL } from '../src/utils/estadoPasarela.js'

const checkout = { sessionKey: 'sesion-niubiz', merchantId: '123456789', purchaseNumber: '000000000047',
  checkoutUrl: 'https://static-content-qas.vnforapps.com/env/sandbox/js/checkout.js',
  callbackUrl: 'https://municipalidad.example/retorno', timeoutUrl: 'https://municipalidad.example/timeout' }
const pago = { idTransaccion: 47, estado: 'INICIADA', montoBase: 30, montoDescuento: 4.5,
  montoTotal: 25.5, obligaciones: ['101'], dePrueba: true, checkout }
const siguiente = { ...pago, idTransaccion: 48, montoBase: 40, montoDescuento: 0, montoTotal: 40,
  checkout: { ...checkout, sessionKey: 'otra-sesion-nueva', purchaseNumber: '000000000048' } }

function visor(inicial) {
  const mensajes = [], configuraciones = [], temporizadores = []
  let receptor, aperturas = 0
  const padre = {}, proveedor = {}, marco = { contentWindow: proveedor }
  const sdk = { configure(c) { this.configuration = { ...c, complete() { throw Error('Retorno POST no esperado') } }; configuraciones.push(this.configuration) },
    open() { aperturas++ } }
  const contexto = vm.createContext({
    window: { parent: padre, addEventListener(_tipo, fn) { receptor = fn },
      ReactNativeWebView: { postMessage: m => mensajes.push(JSON.parse(m)) } },
    document: { getElementById: () => marco }, getComputedStyle: () => ({ visibility: 'hidden' }),
    setTimeout(fn) { return temporizadores.push(fn) - 1 }, clearTimeout(id) { temporizadores[id] = null }, VisanetCheckout: sdk,
  })
  vm.runInContext(/<script>([\s\S]*?)<\/script>/.exec(htmlNiubiz(inicial))[1], contexto)
  return { contexto, sdk, mensajes, configuraciones, temporizadores, get aperturas() { return aperturas },
    preparar() { vm.runInContext('preparar()', contexto) },
    configurar(p) { contexto.window.configurarPago(configuracionNiubiz(p)) },
    abrir(id) { contexto.window.abrirPago(String(id)) },
    retirar(id) { contexto.window.retirarPago(String(id)) },
    cerrarSDK() { receptor({ source: proveedor, data: JSON.stringify({ method: 'close' }) }) },
  }
}

test('acepta el sandbox y rechaza scripts productivos, comercios o retornos inválidos', () => {
  assert.equal(validarPasarela(normalizarPago(pago)).checkout, checkout)
  assert.equal(checkoutValido({ ...checkout, checkoutUrl: 'https://static-content.vnforapps.com/v2/js/checkout.js' }), false)
  assert.equal(checkoutValido({ ...checkout, callbackUrl: 'javascript:alert(1)' }), false)
  assert.equal(checkoutValido({ ...checkout, merchantId: 'otro-comercio' }), false)
  assert.throws(() => validarPasarela({ ...pago, checkout: null, urlPasarela: null }))
  assert.throws(() => configuracionNiubiz({ ...pago, estado: 'CONFIRMADA' }))
})

test('un retorno del navegador no basta para marcar una operación como confirmada', () => {
  assert.throws(() => normalizarPago({ ...pago, estado: 'CONFIRMADA' }))
  assert.equal(leerMensajeNiubiz('{"tipo":"token","token":"t","operacion":"47","channel":"pagoefectivo"}'), null)
  assert.equal(leerMensajeNiubiz('{"tipo":"token","token":"t","channel":"web"}'), null)
  assert.equal(leerMensajeNiubiz('{"tipo":"confirmado"}'), null)
  assert.equal(leerMensajeNiubiz('no-es-json'), null)
  assert.equal(leerMensajeNiubiz(JSON.stringify({ tipo: 'token', operacion: '47', token: 'a'.repeat(4097), channel: 'web' })), null)
})

test('la precarga inicial descarga el SDK sin configurar ni abrir una compra', () => {
  const v = visor()
  v.preparar()
  assert.equal(v.configuraciones.length, 0)
  assert.equal(v.aperturas, 0)
  assert.deepEqual(v.mensajes, [{ tipo: 'preparado' }])
  assert.equal(htmlNiubiz().includes('sesion-niubiz'), false)
  assert.equal(htmlNiubiz().includes('<button'), false)
})

test('el clic anticipado se conserva hasta que llegue el SDK y solo entrega token y operación', () => {
  const v = visor()
  v.configurar(pago)
  v.abrir(47)
  assert.equal(v.aperturas, 0)
  v.preparar()
  assert.equal(v.aperturas, 1)
  const c = v.sdk.configuration
  assert.equal(c.amount, '25.50')
  assert.equal(c.purchasenumber, checkout.purchaseNumber)
  c.complete({ token: 'token-de-niubiz', channel: 'web', cardNumber: 'NO-DEBE-SALIR' })
  const mensaje = v.mensajes.at(-1)
  assert.deepEqual(mensaje, { tipo: 'token', token: 'token-de-niubiz', channel: 'web', operacion: '47' })
  assert.deepEqual(leerMensajeNiubiz(JSON.stringify(mensaje)), { tipo: 'token', token: 'token-de-niubiz', operacion: '47' })
})

test('dos compras usan el mismo visor con configuraciones nuevas y los tokens anteriores no se reaprovechan', () => {
  const v = visor(pago)
  v.preparar(); v.abrir(47)
  const callbackViejo = v.sdk.configuration.complete
  v.cerrarSDK()
  callbackViejo({ token: 'token-compra-47', channel: 'web' })
  v.retirar(47)
  v.configurar(siguiente); v.abrir(48)
  assert.equal(v.configuraciones.length, 2)
  assert.equal(v.sdk.configuration.amount, '40.00')
  assert.equal(v.sdk.configuration.sessiontoken, 'otra-sesion-nueva')
  const antes = v.mensajes.filter(m => m.tipo === 'token').length
  callbackViejo({ token: 'tardio-de-47', channel: 'web' })
  v.sdk.configuration.complete({ token: 'token-compra-47', channel: 'web' })
  assert.equal(v.mensajes.filter(m => m.tipo === 'token').length, antes)
  v.sdk.configuration.complete({ token: 'token-compra-48', channel: 'web' })
  assert.equal(v.mensajes.at(-1).operacion, '48')
})

test('no reasigna un SDK abierto a otra compra ni abre una operación anterior', () => {
  const v = visor(pago)
  v.preparar(); v.abrir(47)
  v.configurar(siguiente); v.abrir(48)
  assert.equal(v.configuraciones.length, 1)
  assert.equal(v.aperturas, 1)
  assert(v.mensajes.some(m => m.tipo === 'reinicio' && m.operacion === '48'))
})

test('una descarga lenta avisa sin cerrar y acepta el token posterior', () => {
  const v = visor(pago)
  v.preparar(); v.abrir(47)
  v.temporizadores.filter(Boolean).at(-1)()
  assert.equal(v.mensajes.some(m => m.tipo === 'error'), false)
  assert.equal(v.mensajes.at(-1).tipo, 'demorado')
  v.sdk.configuration.complete({ token: 'token-tras-la-espera', channel: 'web' })
  assert.equal(v.mensajes.at(-1).tipo, 'token')
})

test('cancelar y retomar la misma compra no vuelve a configurar el SDK', () => {
  const v = visor(pago)
  v.preparar(); v.abrir(47)
  v.sdk.configuration.cancel()
  v.configurar(pago); v.abrir(47)
  assert.equal(v.configuraciones.length, 1)
  assert.equal(v.aperturas, 2)
  assert(v.mensajes.some(m => m.tipo === 'cancelado' && m.operacion === '47'))
})

test('los valores de sesión no pueden cerrar el script HTML', () => {
  const html = htmlNiubiz({ ...pago, checkout: { ...checkout, sessionKey: '</script><script>malicioso()' } })
  assert.equal(html.includes('</script><script>malicioso()'), false)
})

test('el motor permanece tras consultar, confirmar y preparar el siguiente pago con sesión nueva', () => {
  const inicial = actualizarPasarela(PASARELA_INICIAL, pago)
  assert.equal(actualizarPasarela(inicial, { ...pago, checkout: null }), inicial)
  const confirmado = actualizarPasarela({ ...inicial, tokenEntregado: true, abierto: true }, { ...pago, estado: 'CONFIRMADA', checkout: null })
  assert.equal(confirmado.abierto, false)
  const nuevo = actualizarPasarela(confirmado, siguiente)
  assert.equal(nuevo.generacion, inicial.generacion)
  assert.equal(nuevo.tokenEntregado, false)
  assert.equal(nuevo.pago.idTransaccion, 48)
})

test('una operación abandonada o confirmada fuera de este visor aísla sus callbacks con un motor nuevo', () => {
  const inicial = actualizarPasarela(PASARELA_INICIAL, pago)
  assert.equal(actualizarPasarela(inicial, siguiente).generacion, inicial.generacion + 1)
  const confirmadoEnOtro = actualizarPasarela(inicial, { ...pago, estado: 'CONFIRMADA' })
  assert.equal(actualizarPasarela(confirmadoEnOtro, siguiente).generacion, inicial.generacion + 1)
  const ajeno = actualizarPasarela(inicial, { ...siguiente, estado: 'CONFIRMADA' })
  assert.equal(ajeno, inicial)
})
