import { Modal, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Boton from '../ui/Boton'
import Icono from '../ui/Icono'
import ResumenPago from './ResumenPago'
import { colores, fuentes, radios, texto } from '../../theme/tokens'

export default function ModalPagoConfirmado({ pago, abierto, onCerrar, onVolver }) {
  const { top, bottom } = useSafeAreaInsets()
  return (
    <Modal visible={abierto} transparent animationType="none" onRequestClose={onCerrar}>
      <View style={[estilos.fondo, { paddingTop: Math.max(16, top), paddingBottom: Math.max(16, bottom) }]}>
        <ScrollView style={estilos.tarjeta} contentContainerStyle={estilos.contenido} bounces={false}>
          <View style={estilos.check}><Icono nombre="check" color={colores.blanco} tamano={30} /></View>
          <Text style={[texto.subtitulo, estilos.centro]} accessibilityRole="header">¡Pago realizado!</Text>
          <Text style={[texto.secundario, estilos.centro]}>Tu pago quedó registrado correctamente.</Text>
          <View style={estilos.comprobante}>
            <Text style={texto.pequeno}>Comprobante</Text>
            <Text style={estilos.referencia} selectable>{pago.comprobante}</Text>
          </View>
          <ResumenPago pago={pago} />
          <Text style={texto.pequeno}>{pago.obligaciones.length} obligaciones incluidas en el pago.</Text>
          <Boton titulo="Volver a mi estado de cuenta" icono="flecha-izquierda" onPress={onVolver} />
          <Boton titulo="Ver comprobante" variante="texto" onPress={onCerrar} />
        </ScrollView>
      </View>
    </Modal>
  )
}

const estilos = StyleSheet.create({
  fondo: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20, backgroundColor: colores.sombraModal },
  tarjeta: { flexGrow: 0, width: '100%', maxWidth: 420, maxHeight: '95%', backgroundColor: colores.superficie, borderRadius: radios.xl },
  contenido: { padding: 24, gap: 16 },
  centro: { textAlign: 'center' },
  check: { alignSelf: 'center', width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center', backgroundColor: colores.exito },
  comprobante: { padding: 12, gap: 4, borderRadius: radios.md, backgroundColor: colores.exitoFondo },
  referencia: { fontFamily: fuentes.negrita, fontSize: 20, lineHeight: 26, color: colores.exito },
})
