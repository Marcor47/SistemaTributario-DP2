import { StyleSheet, Text, View } from 'react-native'
import { colores, fuentes, texto } from '../../theme/tokens'
import { moneda } from '../../utils/formato'

export default function ResumenPago({ pago }) {
  return (
    <View style={estilos.resumen}>
      <View style={estilos.fila}>
        <Text style={[texto.secundario, estilos.etiqueta]}>Saldo antes de beneficios</Text>
        <Text style={estilos.valor}>{moneda(pago.montoBase)}</Text>
      </View>
      <View style={estilos.fila}>
        <Text style={[texto.secundario, estilos.etiqueta]}>Descuento aplicado</Text>
        <Text style={estilos.valor}>{moneda(pago.montoDescuento)}</Text>
      </View>
      <View style={estilos.total}>
        <Text style={texto.etiqueta}>{pago.estado === 'CONFIRMADA' ? 'Total pagado' : 'Total a pagar'}</Text>
        <Text style={estilos.importeTotal}>{moneda(pago.montoTotal)}</Text>
      </View>
    </View>
  )
}

const estilos = StyleSheet.create({
  resumen: { gap: 12 },
  fila: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8 },
  etiqueta: { flexGrow: 1, flexShrink: 1 },
  valor: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 24, color: colores.marinoOsc, fontVariant: ['tabular-nums'] },
  total: { gap: 4, paddingTop: 16, borderTopWidth: 1, borderTopColor: colores.borde },
  importeTotal: { fontFamily: fuentes.negrita, fontSize: 32, lineHeight: 40, color: colores.marinoOsc, fontVariant: ['tabular-nums'] },
})
