import { Pressable, StyleSheet, Text, View } from 'react-native'
import { LinearGradient } from 'expo-linear-gradient'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Icono from './Icono'
import { colores, fuentes, radios, tamanos } from '../../theme/tokens'

/** Cabecera institucional (degradado del menú lateral de la web) que respeta la barra de estado. */
export default function Cabecera({ titulo, subtitulo, onVolver, volverDeshabilitado = false, izquierda, derecha, children }) {
  const { top } = useSafeAreaInsets()
  return (
    <LinearGradient
      colors={[colores.marino, colores.marinoOsc]}
      start={{ x: 0, y: 0 }}
      end={{ x: 0.4, y: 1 }}
      style={[estilos.cabecera, { paddingTop: top + 8 }]}
    >
      <View style={estilos.barra}>
        {onVolver ? (
          <Pressable
            onPress={onVolver}
            disabled={volverDeshabilitado}
            accessibilityRole="button"
            accessibilityLabel="Volver"
            accessibilityState={{ disabled: volverDeshabilitado }}
            style={({ pressed }) => [estilos.icono, pressed && estilos.presionado, volverDeshabilitado && estilos.deshabilitado]}
          >
            <Icono nombre="flecha-izquierda" color={colores.blanco} />
          </Pressable>
        ) : izquierda}
        <View style={estilos.textos}>
          <Text style={estilos.titulo} accessibilityRole="header" numberOfLines={2}>{titulo}</Text>
          {subtitulo ? <Text style={estilos.subtitulo}>{subtitulo}</Text> : null}
        </View>
        {derecha}
      </View>
      {children}
    </LinearGradient>
  )
}

export function BotonCabecera({ icono, etiqueta, onPress }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={etiqueta}
      style={({ pressed }) => [estilos.icono, pressed && estilos.presionado]}
    >
      <Icono nombre={icono} color={colores.blanco} />
    </Pressable>
  )
}

const estilos = StyleSheet.create({
  cabecera: { paddingHorizontal: 16, paddingBottom: 16 },
  barra: { flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: tamanos.toque },
  icono: {
    width: tamanos.toque, height: tamanos.toque, borderRadius: radios.md, alignItems: 'center', justifyContent: 'center',
  },
  presionado: { backgroundColor: 'rgba(255,255,255,0.16)' },
  deshabilitado: { opacity: 0.5 },
  textos: { flex: 1 },
  titulo: { fontFamily: fuentes.negrita, fontSize: 20, lineHeight: 26, color: colores.blanco },
  subtitulo: { fontFamily: fuentes.regular, fontSize: 14, lineHeight: 20, color: '#E1ECF8' },
})
