import { useRef, useState } from 'react'
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native'
import { Image } from 'expo-image'
import { LinearGradient } from 'expo-linear-gradient'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Aviso from '../components/ui/Aviso'
import Boton from '../components/ui/Boton'
import CampoTexto from '../components/ui/CampoTexto'
import Icono from '../components/ui/Icono'
import { useSesion } from '../context/SesionContext'
import { ACCESO_PRUEBA } from '../services/authService'
import { colores, fuentes, radios, texto } from '../theme/tokens'

export default function Login() {
  const { sesion, ingresar } = useSesion()
  const { top, bottom } = useSafeAreaInsets()
  const refClave = useRef(null)
  const [codigo, setCodigo] = useState('')
  const [clave, setClave] = useState('')
  const [errores, setErrores] = useState({})
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  function usarAccesoPrueba() {
    setCodigo(ACCESO_PRUEBA.codigo)
    setClave(ACCESO_PRUEBA.password)
    setErrores({})
    setError('')
  }

  async function enviar() {
    if (enviando) return
    const faltantes = {}
    if (!codigo.trim()) faltantes.codigo = 'Ingresa tu código de contribuyente o tu documento.'
    if (!clave) faltantes.clave = 'Ingresa tu contraseña.'
    setErrores(faltantes)
    setError('')
    if (Object.keys(faltantes).length) return
    setEnviando(true)
    try {
      // Al cambiar la sesión, Stack.Protected lleva al inicio.
      await ingresar(codigo.trim(), clave)
    } catch (err) {
      setError(err.message)
      setClave('')
      setEnviando(false)
    }
  }

  return (
    <KeyboardAvoidingView style={estilos.raiz} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView
        contentContainerStyle={[estilos.scroll, { paddingBottom: bottom + 24 }]}
        keyboardShouldPersistTaps="handled"
        bounces={false}
      >
        <View style={[estilos.portada, { paddingTop: top + 20 }]}>
          <Image
            source={require('../../assets/municipalidad.jpg')}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            accessible={false}
          />
          <LinearGradient
            colors={['rgba(53,73,125,0.94)', 'rgba(23,64,107,0.88)', 'rgba(25,195,243,0.6)']}
            locations={[0, 0.6, 1]}
            start={{ x: 0, y: 0 }}
            end={{ x: 0.7, y: 1 }}
            style={StyleSheet.absoluteFill}
          />
          <View style={estilos.marca} accessible accessibilityLabel="Municipalidad de San Miguel">
            <Image source={require('../../assets/logo-san-miguel-circulo.png')} style={estilos.logo} accessible={false} />
            <Text style={estilos.marcaTexto}>
              Municipalidad{'\n'}
              <Text style={estilos.marcaNombre}>San Miguel</Text>
            </Text>
          </View>
          <Text style={estilos.lema}>Tu información tributaria, siempre a la mano.</Text>
          <Text style={estilos.lemaSub}>Realiza tus trámites municipales desde tu celular.</Text>
        </View>

        <View style={estilos.hoja}>
          <View style={estilos.acento} />
          <Text style={texto.titulo} accessibilityRole="header">Iniciar sesión</Text>
          <Text style={[texto.secundario, estilos.sub]}>
            Ingresa con tu código de contribuyente para acceder a tus servicios en línea.
          </Text>

          {sesion.vencida && !error ? (
            <Aviso tipo="alerta" style={estilos.aviso}>Tu sesión venció por inactividad. Vuelve a ingresar.</Aviso>
          ) : null}
          {error ? <Aviso tipo="critico" style={estilos.aviso}>{error}</Aviso> : null}

          <View style={estilos.formulario}>
            <CampoTexto
              etiqueta="Código de contribuyente"
              value={codigo}
              onChangeText={(v) => { setCodigo(v); if (errores.codigo) setErrores((e) => ({ ...e, codigo: undefined })) }}
              error={errores.codigo}
              ayuda="También puedes usar tu DNI o RUC."
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="username"
              textContentType="username"
              maxLength={40}
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => refClave.current?.focus()}
              editable={!enviando}
            />
            <CampoTexto
              ref={refClave}
              etiqueta="Contraseña"
              clave
              value={clave}
              onChangeText={(v) => { setClave(v); if (errores.clave) setErrores((e) => ({ ...e, clave: undefined })) }}
              error={errores.clave}
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="current-password"
              textContentType="password"
              maxLength={256}
              returnKeyType="go"
              onSubmitEditing={enviar}
              editable={!enviando}
            />
            <Boton titulo="Ingresar" tituloCargando="Verificando…" cargando={enviando} onPress={enviar} grande />
          </View>

          {__DEV__ && (
            <View style={estilos.demo} accessibilityRole="summary">
              <Text style={estilos.demoTitulo}>Acceso de prueba</Text>
              <Text style={estilos.demoDatos}>
                Código: <Text style={estilos.demoCodigo}>{ACCESO_PRUEBA.codigo}</Text>{'\n'}
                Contraseña: <Text style={estilos.demoCodigo}>{ACCESO_PRUEBA.password}</Text>
              </Text>
              <Boton
                titulo="Usar estos datos"
                variante="secundario"
                onPress={usarAccesoPrueba}
                deshabilitado={enviando}
                style={estilos.demoBoton}
              />
            </View>
          )}

          <View style={estilos.ayuda}>
            <Icono nombre="info" color={colores.textoSecundario} tamano={18} />
            <Text style={[texto.secundario, estilos.ayudaTexto]}>
              ¿Problemas para ingresar? Acércate a la Gerencia de Rentas de la Municipalidad de San Miguel.
            </Text>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}

const estilos = StyleSheet.create({
  raiz: { flex: 1, backgroundColor: colores.superficie },
  scroll: { flexGrow: 1 },
  portada: { minHeight: 300, paddingHorizontal: 24, paddingBottom: 56, justifyContent: 'space-between', gap: 20, overflow: 'hidden', backgroundColor: colores.marino },
  marca: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  logo: { width: 56, height: 56, borderRadius: 28, backgroundColor: colores.blanco },
  marcaTexto: { fontFamily: fuentes.regular, fontSize: 15, lineHeight: 19, color: colores.blanco },
  marcaNombre: { fontFamily: fuentes.negrita, fontSize: 21, lineHeight: 25 },
  lema: { fontFamily: fuentes.negrita, fontSize: 28, lineHeight: 34, color: colores.blanco, maxWidth: 340 },
  lemaSub: { fontFamily: fuentes.regular, fontSize: 16, lineHeight: 22, color: '#E1ECF8', marginTop: -12 },
  hoja: {
    flex: 1, marginTop: -28, paddingHorizontal: 24, paddingTop: 28, backgroundColor: colores.superficie,
    borderTopLeftRadius: 28, borderTopRightRadius: 28,
  },
  acento: { width: 44, height: 5, borderRadius: radios.pill, backgroundColor: colores.cian, marginBottom: 20 },
  sub: { marginTop: 8, marginBottom: 20 },
  aviso: { marginBottom: 16 },
  formulario: { gap: 20 },
  demo: {
    marginTop: 24, padding: 14, gap: 8, borderRadius: radios.md, borderWidth: 1,
    backgroundColor: colores.advertenciaFondo, borderColor: colores.advertenciaBorde,
  },
  demoTitulo: { fontFamily: fuentes.negrita, fontSize: 15, color: colores.advertenciaTexto },
  demoDatos: { fontFamily: fuentes.regular, fontSize: 15, lineHeight: 24, color: colores.advertenciaTexto },
  demoCodigo: { fontFamily: fuentes.negrita },
  demoBoton: { alignSelf: 'flex-start', borderColor: '#A86A13' },
  ayuda: {
    flexDirection: 'row', gap: 10, alignItems: 'flex-start', marginTop: 28, paddingTop: 20,
    borderTopWidth: 1, borderTopColor: '#E1E8F0',
  },
  ayudaTexto: { flex: 1 },
})
