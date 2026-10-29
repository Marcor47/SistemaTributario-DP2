import { ActivityIndicator, StyleSheet, View } from 'react-native'
import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { Image } from 'expo-image'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import {
  AtkinsonHyperlegible_400Regular,
  AtkinsonHyperlegible_700Bold,
  useFonts,
} from '@expo-google-fonts/atkinson-hyperlegible'
import { SesionProvider, useSesion } from '../context/SesionContext'
import { PagosProvider } from '../context/PagosContext'
import { colores } from '../theme/tokens'

export default function RootLayout() {
  const [fuentesListas] = useFonts({ AtkinsonHyperlegible_400Regular, AtkinsonHyperlegible_700Bold })
  return (
    <SafeAreaProvider>
      <SesionProvider>
        <StatusBar style="light" />
        {fuentesListas ? <Navegacion /> : <Bienvenida />}
      </SesionProvider>
    </SafeAreaProvider>
  )
}

// Sin sesión solo existe el login; con sesión, el home y los trámites. Si la sesión vence, Expo Router redirige.
function Navegacion() {
  const { sesion } = useSesion()
  if (sesion.estado === 'cargando') return <Bienvenida />
  const autenticado = Boolean(sesion.usuario)
  return (
    <PagosProvider key={sesion.usuario?.codigoContribuyente ?? 'sin-sesion'}>
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colores.fondo } }}>
        <Stack.Protected guard={!autenticado} redirectTo="/">
          <Stack.Screen name="login" />
        </Stack.Protected>
        <Stack.Protected guard={autenticado} redirectTo="/login">
          <Stack.Screen name="index" />
          <Stack.Screen name="estado-cuenta" />
          <Stack.Screen name="pagos/[id]" />
          <Stack.Screen name="solicitud-predio/index" />
          <Stack.Screen name="solicitud-predio/enviada" options={{ gestureEnabled: false }} />
          <Stack.Screen name="constancia/index" />
        </Stack.Protected>
      </Stack>
    </PagosProvider>
  )
}

function Bienvenida() {
  return (
    <View style={estilos.bienvenida} accessibilityLabel="Cargando">
      <Image source={require('../../assets/logo-san-miguel-circulo.png')} style={estilos.logo} accessible={false} />
      <ActivityIndicator color={colores.blanco} size="large" />
    </View>
  )
}

const estilos = StyleSheet.create({
  bienvenida: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 24, backgroundColor: colores.marinoOsc },
  logo: { width: 96, height: 96, borderRadius: 48, backgroundColor: colores.blanco },
})
