import { useState } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import Icono from '../ui/Icono'
import { colores, fuentes, radios, sombra, texto } from '../../theme/tokens'
import { fecha, moneda } from '../../utils/formato'

const ESTADOS = {
  Pagada: { color: colores.exito, fondo: colores.exitoFondo, icono: 'check-circulo' },
  Vencida: { color: colores.critico, fondo: colores.criticoFondo, icono: 'alerta' },
  Pendiente: { color: colores.advertencia, fondo: colores.advertenciaFondo, icono: 'reloj' },
}

export default function PeriodoCuenta({ periodo }) {
  const [abierto, setAbierto] = useState(false)
  const estado = ESTADOS[periodo.estado] || ESTADOS.Pendiente
  const tieneDescuento = (periodo.descuento || 0) > 0

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
          {periodo.fechaVencimiento ? (
            <Text style={estilos.vencimiento}>
              Vence: <Text style={estilos.vencimientoFecha}>{fecha(periodo.fechaVencimiento)}</Text>
            </Text>
          ) : null}
        </View>
        <View style={estilos.cabeceraDerecha}>
          <View style={[estilos.estado, { backgroundColor: estado.fondo }]}>
            <Icono nombre={estado.icono} color={estado.color} tamano={16} />
            <Text style={[estilos.estadoTexto, { color: estado.color }]}>{periodo.estado}</Text>
          </View>
          <Icono nombre={abierto ? 'chevron-arriba' : 'chevron-abajo'} tamano={18} />
        </View>
      </Pressable>

      {periodo.etapaCobranza && periodo.estado === 'Vencida' ? (
        <View style={estilos.etapaCaja}>
          <Icono nombre="alerta" color={colores.criticoTexto} tamano={14} />
          <Text style={estilos.etapaTexto}>
            Etapa de cobranza: <Text style={estilos.etapaNombre}>{periodo.etapaCobranza}</Text>
          </Text>
        </View>
      ) : null}

      <View style={estilos.importes}>
        <Importe etiqueta="Emitido" valor={periodo.emitido} />
        {tieneDescuento ? <Importe etiqueta="Descuento" valor={periodo.descuento} descuento /> : null}
        <Importe etiqueta="Pagado" valor={periodo.pagado} />
        <Importe etiqueta="Saldo" valor={periodo.deuda} destacado deuda />
      </View>

      {abierto && (
        <View style={estilos.conceptos}>
          <Text style={estilos.detalleTitulo}>Conceptos del periodo</Text>
          {periodo.conceptos.map((concepto) => {
            const tieneDescCon = (concepto.descuento || 0) > 0
            return (
              <View key={concepto.id} style={estilos.concepto}>
                <View style={estilos.conceptoCabecera}>
                  <Text style={texto.etiqueta}>{concepto.tributo}</Text>
                  {concepto.fechaVencimiento ? (
                    <Text style={texto.pequeno}>Vence: {fecha(concepto.fechaVencimiento)}</Text>
                  ) : null}
                </View>

                {concepto.etapaCobranza && concepto.estado === 'Vencida' ? (
                  <View style={estilos.etapaConceptoCaja}>
                    <Text style={estilos.etapaConceptoTexto}>
                      Cobranza: <Text style={estilos.etapaNombre}>{concepto.etapaCobranza}</Text>
                    </Text>
                  </View>
                ) : null}

                <View style={estilos.importesDetalle}>
                  <Importe etiqueta="Emitido" valor={concepto.emitido} />
                  {tieneDescCon ? <Importe etiqueta="Descuento" valor={concepto.descuento} descuento /> : null}
                  <Importe etiqueta="Pagado" valor={concepto.pagado} />
                  <Importe etiqueta="Saldo" valor={concepto.deuda} destacado deuda />
                </View>
              </View>
            )
          })}
        </View>
      )}
    </View>
  )
}

function Importe({ etiqueta, valor, destacado = false, descuento = false, deuda = false }) {
  return (
    <View style={estilos.importe}>
      <Text style={texto.pequeno}>{etiqueta}</Text>
      <Text style={[
        estilos.valor,
        destacado && estilos.destacado,
        descuento && estilos.valorDescuento,
        deuda && valor > 0 && estilos.valorDeuda,
      ]}>
        {descuento ? ('-' + moneda(valor)) : moneda(valor)}
      </Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  tarjeta: { backgroundColor: colores.superficie, borderRadius: radios.lg, borderWidth: 1, borderColor: colores.borde, overflow: 'hidden', ...sombra },
  cabecera: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, padding: 16, minHeight: 76 },
  presionado: { backgroundColor: colores.azulSuave },
  titulo: { flex: 1, gap: 2 },
  cabeceraDerecha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  periodo: { fontFamily: fuentes.negrita, fontSize: 18, lineHeight: 24, color: colores.marinoOsc },
  vencimiento: { ...texto.pequeno, marginTop: 2, color: colores.textoSecundario },
  vencimientoFecha: { fontFamily: fuentes.negrita, color: colores.texto },
  estado: { flexDirection: 'row', alignItems: 'center', flexShrink: 1, gap: 4, borderRadius: radios.pill, paddingHorizontal: 8, paddingVertical: 4 },
  estadoTexto: { fontFamily: fuentes.negrita, fontSize: 13, lineHeight: 18, flexShrink: 1 },
  etapaCaja: {
    flexDirection: 'row', alignItems: 'center', gap: 6, marginHorizontal: 16, marginBottom: 12,
    paddingHorizontal: 10, paddingVertical: 6, backgroundColor: colores.criticoFondo,
    borderRadius: radios.sm, borderWidth: 1, borderColor: colores.criticoBorde,
  },
  etapaTexto: { ...texto.pequeno, color: colores.criticoTexto },
  etapaNombre: { fontFamily: fuentes.negrita, color: colores.criticoTexto },
  importes: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, paddingHorizontal: 16, paddingBottom: 16 },
  importe: { flexGrow: 1, flexBasis: 76, gap: 2 },
  valor: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 22, color: colores.texto, fontVariant: ['tabular-nums'] },
  valorDescuento: { color: colores.exito },
  valorDeuda: { color: colores.critico },
  destacado: { color: colores.marinoOsc },
  conceptos: { padding: 16, paddingTop: 12, gap: 14, backgroundColor: colores.superficieTenue, borderTopWidth: 1, borderTopColor: colores.borde },
  detalleTitulo: { ...texto.pequeno, fontFamily: fuentes.negrita },
  concepto: { gap: 6, paddingBottom: 8, borderBottomWidth: 1, borderBottomColor: colores.borde },
  conceptoCabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  etapaConceptoCaja: {
    alignSelf: 'flex-start', paddingHorizontal: 6, paddingVertical: 2,
    backgroundColor: colores.criticoFondo, borderRadius: radios.sm,
  },
  etapaConceptoTexto: { fontSize: 12, color: colores.criticoTexto },
  importesDetalle: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 4 },
})
