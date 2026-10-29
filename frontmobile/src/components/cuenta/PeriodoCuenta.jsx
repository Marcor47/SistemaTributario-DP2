import { useState } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import Icono from '../ui/Icono'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'
import { moneda } from '../../utils/formato'

const ESTADOS = {
  Pagada: { color: colores.exito, fondo: colores.exitoFondo, icono: 'check-circulo' },
  Vencida: { color: colores.critico, fondo: colores.criticoFondo, icono: 'alerta' },
  Pendiente: { color: colores.advertencia, fondo: colores.advertenciaFondo, icono: 'reloj' },
}

export default function PeriodoCuenta({ periodo }) {
  const [abierto, setAbierto] = useState(false)
  const estado = ESTADOS[periodo.estado]
  return (
    <View style={estilos.tarjeta}>
      <Pressable
        onPress={() => setAbierto((valor) => !valor)}
        accessibilityRole="button"
        accessibilityLabel={`${periodo.periodo}, ${periodo.estado}. ${abierto ? 'Ocultar' : 'Ver'} conceptos`}
        accessibilityState={{ expanded: abierto }}
        style={({ pressed }) => [estilos.cabecera, pressed && estilos.presionado]}
      >
        <View style={estilos.titulo}>
          <Text style={estilos.periodo}>{periodo.periodo}</Text>
          <Text style={texto.pequeno}>{periodo.titulo}</Text>
        </View>
        <View style={[estilos.estado, { backgroundColor: estado.fondo }]}>
          <Icono nombre={estado.icono} color={estado.color} tamano={16} />
          <Text style={[estilos.estadoTexto, { color: estado.color }]}>{periodo.estado}</Text>
        </View>
        <Icono nombre={abierto ? 'chevron-arriba' : 'chevron-abajo'} tamano={18} />
      </Pressable>
      <View style={estilos.importes}>
        <Importe etiqueta="Emitido" valor={periodo.emitido} />
        <Importe etiqueta="Pagado" valor={periodo.pagado} />
        <Importe etiqueta="Saldo" valor={periodo.deuda} destacado />
      </View>
      {abierto && (
        <View style={estilos.conceptos}>
          <Text style={estilos.detalleTitulo}>Conceptos del periodo</Text>
          {periodo.conceptos.map((concepto) => (
            <View key={concepto.id} style={estilos.concepto}>
              <Text style={texto.etiqueta}>{concepto.tributo}</Text>
              <View style={estilos.importesDetalle}>
                <Importe etiqueta="Emitido" valor={concepto.emitido} />
                <Importe etiqueta="Pagado" valor={concepto.pagado} />
                <Importe etiqueta="Saldo" valor={concepto.deuda} destacado />
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  )
}

function Importe({ etiqueta, valor, destacado = false }) {
  return (
    <View style={estilos.importe}>
      <Text style={texto.pequeno}>{etiqueta}</Text>
      <Text style={[estilos.valor, destacado && estilos.destacado]}>{moneda(valor)}</Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  tarjeta: { backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, overflow: 'hidden', ...sombra },
  cabecera: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 16, minHeight: 72 },
  presionado: { backgroundColor: colores.azulSuave },
  titulo: { flex: 1, gap: 2 },
  periodo: { fontFamily: fuentes.negrita, fontSize: 18, lineHeight: 24, color: colores.marinoOsc },
  estado: { flexDirection: 'row', alignItems: 'center', flexShrink: 1, gap: 4, borderRadius: radios.pill, paddingHorizontal: 8, paddingVertical: 4 },
  estadoTexto: { fontFamily: fuentes.negrita, fontSize: 13, lineHeight: 18, flexShrink: 1 },
  importes: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, paddingHorizontal: 16, paddingBottom: 16 },
  importe: { flexGrow: 1, flexBasis: 88, gap: 2 },
  valor: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 22, color: colores.texto, fontVariant: ['tabular-nums'] },
  destacado: { color: colores.marinoOsc },
  conceptos: { padding: 16, paddingTop: 12, gap: 12, backgroundColor: colores.superficieTenue, borderTopWidth: 1, borderTopColor: colores.borde },
  detalleTitulo: { ...texto.pequeno, fontFamily: fuentes.negrita },
  concepto: { gap: 8 },
  importesDetalle: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
})
