import { useState } from 'react'
import { ScrollView, StyleSheet, Text, View } from 'react-native'
import { useRouter } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Aviso from '../../components/ui/Aviso'
import Boton from '../../components/ui/Boton'
import Cabecera from '../../components/ui/Cabecera'
import CampoTexto from '../../components/ui/CampoTexto'
import { useSesion } from '../../context/SesionContext'
import { elegirFirma, enviarFirmaConstancia, firmaDePrueba, iniciarConstancia, obtenerConstancia, obtenerDocumentoFirma, textoDocumento } from '../../services/constanciaService'
import { colores, radios, texto } from '../../theme/tokens'

export default function Constancia() {
  const router = useRouter()
  const { bottom } = useSafeAreaInsets()
  const { sesion } = useSesion()
  const [numero, setNumero] = useState('')
  const [solicitud, setSolicitud] = useState(null)
  const [documento, setDocumento] = useState(null)
  const [firma, setFirma] = useState(null)
  const [certificado, setCertificado] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')

  async function ejecutar(accion) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try { await accion() } catch (err) { setError(err.message) } finally { setOcupado(false) }
  }

  function actualizarSolicitud(nueva) {
    setSolicitud(nueva)
    setNumero(String(nueva.idSolicitud))
    setDocumento(null)
    setFirma(null)
    setCertificado('')
  }

  function consultar() {
    if (!/^[1-9]\d*$/.test(numero.trim())) { setError('Ingresa un número de solicitud válido.'); return }
    ejecutar(async () => actualizarSolicitud(await obtenerConstancia(numero.trim())))
  }

  async function seleccionarFirma() {
    try {
      const seleccion = await elegirFirma()
      if (seleccion) { setFirma(seleccion); setError('') }
    } catch (err) { setError(err.message) }
  }

  function firmar(firmaBase64, nombreCertificado) {
    ejecutar(async () => actualizarSolicitud(await enviarFirmaConstancia(solicitud.idSolicitud, firmaBase64, nombreCertificado)))
  }

  const pendiente = solicitud?.estado === 'INICIADA'
  const cerrada = ['EMITIDA', 'RECHAZADA'].includes(solicitud?.estado)

  return (
    <View style={estilos.raiz}>
      <Cabecera titulo="Constancia de No Adeudo" subtitulo="Firma tu solicitud" onVolver={() => router.back()} />
      <ScrollView contentContainerStyle={[estilos.contenido, { paddingBottom: bottom + 24 }]} keyboardShouldPersistTaps="handled">
        <Text style={texto.cuerpo}>El documento y la firma se asocian a tu sesión de contribuyente {sesion.usuario?.codigoContribuyente}. Revisa el documento antes de firmarlo.</Text>
        {error ? <Aviso tipo="critico" titulo="No se pudo continuar">{error}</Aviso> : null}
        {(!solicitud || cerrada) ? <Boton titulo="Iniciar nueva solicitud" onPress={() => ejecutar(async () => actualizarSolicitud(await iniciarConstancia()))} cargando={ocupado} /> : null}
        <View style={estilos.tarjeta}>
          <CampoTexto etiqueta="Número de solicitud existente" value={numero} onChangeText={setNumero} keyboardType="number-pad" editable={!ocupado} />
          <Boton titulo="Consultar solicitud" variante="secundario" onPress={consultar} deshabilitado={ocupado} />
        </View>
        {solicitud ? <View style={estilos.tarjeta}>
          <Text style={texto.subtitulo}>Solicitud N.° {solicitud.idSolicitud}</Text>
          <Text style={texto.cuerpo}>Estado: {solicitud.estado.replaceAll('_', ' ')}</Text>
          {solicitud.firmaContribuyente ? <Aviso tipo="exito" titulo="Firma registrada">Tu solicitud fue enviada para continuar el trámite.</Aviso> : null}
          {pendiente ? <>
            <Boton titulo="Obtener documento para firma" variante="secundario" onPress={() => ejecutar(async () => { setDocumento(await obtenerDocumentoFirma(solicitud.idSolicitud)); setFirma(null) })} cargando={ocupado} />
            {documento ? <>
              <Text style={texto.cuerpo}>Documento: {documento.nombreArchivo}</Text>
              {documento.tipoContenido?.startsWith('text/') ? <Text style={estilos.documento}>{textoDocumento(documento.contenidoBase64)}</Text> : null}
              <Text style={texto.pequeno}>SHA-256: {documento.hashSha256}</Text>
              <Text style={texto.secundario}>Firma este documento con tu proveedor de firma digital y selecciona el archivo de firma resultante.</Text>
              <Boton titulo={firma ? `Firma: ${firma.nombre}` : 'Seleccionar archivo de firma'} variante="secundario" onPress={seleccionarFirma} deshabilitado={ocupado} />
              <CampoTexto etiqueta="Información del certificado digital" value={certificado} onChangeText={setCertificado} editable={!ocupado} autoCapitalize="none" />
              <Boton titulo="Enviar firma" onPress={() => firmar(firma.firmaBase64, certificado.trim())} deshabilitado={!firma || !certificado.trim() || ocupado} cargando={ocupado} />
              {documento.documentoDePrueba ? <Aviso tipo="alerta" titulo="Entorno de prueba">Esta firma no tiene validez legal.</Aviso> : null}
              {documento.documentoDePrueba ? <Boton titulo="Firmar documento de prueba" variante="secundario" onPress={() => firmar(firmaDePrueba(documento.hashSha256), 'CERTIFICADO DE PRUEBA')} deshabilitado={ocupado} /> : null}
            </> : null}
          </> : null}
        </View> : null}
      </ScrollView>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.fondo },
  contenido: { padding: 16, gap: 16 },
  tarjeta: { padding: 16, gap: 14, backgroundColor: colores.superficie, borderColor: colores.borde, borderWidth: 1, borderRadius: radios.lg },
  documento: { ...texto.cuerpo, padding: 12, backgroundColor: colores.superficieTenue, borderColor: colores.borde, borderWidth: 1, borderRadius: radios.sm },
})
