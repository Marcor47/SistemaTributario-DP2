import { Pressable, StyleSheet, Text, View } from 'react-native'
import Icono from './Icono'
import { colores, fuentes, radios, tamanos } from '../../theme/tokens'

export default function Paginacion({ pagina, totalPaginas, onCambiar }) {
  if (totalPaginas <= 1) return null
  const anterior = pagina > 0
  const siguiente = pagina + 1 < totalPaginas
  return <View style={estilos.fila} accessibilityRole="toolbar" accessibilityLabel="Paginación">
    <Pressable accessibilityRole="button" accessibilityLabel="Página anterior" accessibilityState={{ disabled: !anterior }}
      disabled={!anterior} onPress={() => onCambiar(pagina - 1)} style={({ pressed }) => [estilos.boton, !anterior && estilos.inactivo, pressed && anterior && estilos.presionado]}>
      <Icono nombre="flecha-izquierda" />
    </Pressable>
    <Text style={estilos.texto}>Página {pagina + 1} de {totalPaginas}</Text>
    <Pressable accessibilityRole="button" accessibilityLabel="Página siguiente" accessibilityState={{ disabled: !siguiente }}
      disabled={!siguiente} onPress={() => onCambiar(pagina + 1)} style={({ pressed }) => [estilos.boton, !siguiente && estilos.inactivo, pressed && siguiente && estilos.presionado]}>
      <Icono nombre="chevron-derecha" />
    </Pressable>
  </View>
}

const estilos = StyleSheet.create({
  fila: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12, paddingTop: 8 },
  boton: { width: tamanos.toque, height: tamanos.toque, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colores.bordeFuerte, borderRadius: radios.md, backgroundColor: colores.superficie },
  presionado: { backgroundColor: colores.azulSuave }, inactivo: { opacity: 0.4 },
  texto: { fontFamily: fuentes.negrita, fontSize: 15, color: colores.marinoOsc },
})
