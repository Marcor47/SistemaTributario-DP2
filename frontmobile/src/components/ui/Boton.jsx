import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native'
import Icono from './Icono'
import { colores, fuentes, radios, tamanos } from '../../theme/tokens'

const VARIANTES = {
  primario: { fondo: colores.marino, fondoPresionado: colores.marinoOsc, borde: colores.marino, texto: colores.blanco },
  secundario: { fondo: colores.superficie, fondoPresionado: colores.azulSuave, borde: colores.bordeFuerte, texto: colores.marinoOsc },
  peligro: { fondo: colores.superficie, fondoPresionado: colores.criticoFondo, borde: colores.critico, texto: colores.critico },
  texto: { fondo: 'transparent', fondoPresionado: colores.azulSuave, borde: 'transparent', texto: colores.marinoOsc },
}

/** Botón con altura táctil mínima de 48 dp, estado presionado sin mover el layout y estado de carga. */
export default function Boton({
  titulo, onPress, variante = 'primario', icono, cargando = false, tituloCargando, deshabilitado = false,
  grande = false, accessibilityHint, style,
}) {
  const v = VARIANTES[variante]
  const inactivo = deshabilitado || cargando
  return (
    <Pressable
      onPress={onPress}
      disabled={inactivo}
      accessibilityRole="button"
      accessibilityLabel={cargando && tituloCargando ? tituloCargando : titulo}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: inactivo, busy: cargando }}
      style={({ pressed }) => [
        estilos.base,
        grande && estilos.grande,
        { backgroundColor: pressed ? v.fondoPresionado : v.fondo, borderColor: v.borde },
        deshabilitado && !cargando && estilos.deshabilitado,
        style,
      ]}
    >
      <View style={estilos.contenido}>
        {cargando ? (
          <ActivityIndicator color={v.texto} />
        ) : (
          icono && <Icono nombre={icono} color={v.texto} tamano={tamanos.iconoSm} />
        )}
        <Text style={[estilos.texto, grande && estilos.textoGrande, { color: v.texto }]}>
          {cargando && tituloCargando ? tituloCargando : titulo}
        </Text>
      </View>
    </Pressable>
  )
}

const estilos = StyleSheet.create({
  base: {
    minHeight: tamanos.toque, paddingHorizontal: 20, paddingVertical: 10, borderRadius: radios.md, borderWidth: 2,
    alignItems: 'center', justifyContent: 'center',
  },
  grande: { minHeight: 54 },
  contenido: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, maxWidth: '100%' },
  texto: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, flexShrink: 1, textAlign: 'center' },
  textoGrande: { fontSize: 18, lineHeight: 24 },
  deshabilitado: { opacity: 0.5 },
})
