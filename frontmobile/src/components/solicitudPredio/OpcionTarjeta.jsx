import { Pressable, StyleSheet, Text, View } from 'react-native'
import Icono from '../ui/Icono'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'

/** Opción de selección única en forma de tarjeta (rol radio), con check además del color. */
export default function OpcionTarjeta({ icono, titulo, descripcion, seleccionada, onPress, compacta = false }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityLabel={titulo}
      accessibilityHint={descripcion}
      accessibilityState={{ checked: seleccionada }}
      style={({ pressed }) => [
        estilos.tarjeta,
        compacta && estilos.compacta,
        seleccionada && estilos.seleccionada,
        pressed && !seleccionada && estilos.presionada,
      ]}
    >
      <View style={[estilos.icono, seleccionada && estilos.iconoSeleccionado]}>
        <Icono nombre={icono} color={seleccionada ? colores.blanco : colores.marino} tamano={compacta ? 22 : 26} />
      </View>
      <View style={estilos.textos}>
        <Text style={estilos.titulo}>{titulo}</Text>
        {descripcion ? <Text style={texto.secundario}>{descripcion}</Text> : null}
      </View>
      <View style={[estilos.radio, seleccionada && estilos.radioSeleccionado]}>
        {seleccionada && <Icono nombre="check" color={colores.blanco} tamano={14} trazo={3} />}
      </View>
    </Pressable>
  )
}

const estilos = StyleSheet.create({
  tarjeta: {
    flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16, minHeight: 88,
    backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 2, borderColor: colores.borde, ...sombra,
  },
  compacta: { minHeight: 64, paddingVertical: 12 },
  seleccionada: { borderColor: colores.marino, backgroundColor: colores.azulSuave },
  presionada: { borderColor: colores.bordeFuerte, backgroundColor: colores.superficieTenue },
  icono: {
    width: 48, height: 48, borderRadius: radios.md, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colores.azulSuave,
  },
  iconoSeleccionado: { backgroundColor: colores.marino },
  textos: { flex: 1, gap: 2 },
  titulo: { fontFamily: fuentes.negrita, fontSize: 17, lineHeight: 23, color: colores.marinoOsc },
  radio: {
    width: 26, height: 26, borderRadius: 13, borderWidth: 2, borderColor: colores.bordeFuerte,
    alignItems: 'center', justifyContent: 'center',
  },
  radioSeleccionado: { borderColor: colores.marino, backgroundColor: colores.marino },
})
