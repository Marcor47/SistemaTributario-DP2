import { useState } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import Boton from '../ui/Boton'
import Icono from '../ui/Icono'
import { agruparDeuda, periodoDeuda } from '../../utils/deudaConstancia'
import { moneda as soles } from '../../utils/formato'
import { colores, fuentes, radios, texto } from '../../theme/tokens'

const cuotas = (cantidad) => `${cantidad} ${cantidad === 1 ? 'cuota pendiente' : 'cuotas pendientes'}`

export default function DeudaPendiente({ deuda, ocupado, onVerCuenta, onConsultar }) {
  const grupos = agruparDeuda(deuda.obligaciones)
  return (
    <View style={estilos.tarjeta}>
      <View style={estilos.resumen}>
        <View style={estilos.filaResumen}>
          <Text style={estilos.etiquetaTotal}>TOTAL PENDIENTE</Text>
          <View style={estilos.iconoTotal}><Icono nombre="tarjeta" tamano={22} color={colores.blanco} /></View>
        </View>
        <Text style={estilos.total}>{soles(deuda.total)}</Text>
        <Text style={estilos.cantidad}>{cuotas(deuda.obligaciones.length)}</Text>
      </View>

      <View style={estilos.aviso}>
        <Icono nombre="alerta" tamano={20} color={colores.advertencia} />
        <Text style={estilos.textoAviso}>Cancela tu deuda para continuar con la solicitud de tu constancia.</Text>
      </View>

      <Boton titulo="Ver estado de cuenta y pagos" icono="tarjeta" onPress={onVerCuenta} deshabilitado={ocupado} />

      <View style={estilos.encabezadoDetalle}>
        <Text style={texto.etiqueta}>Detalle de tu deuda</Text>
        <Text style={texto.pequeno}>{grupos.length} {grupos.length === 1 ? 'tributo' : 'tributos'}</Text>
      </View>
      <View style={estilos.grupos}>
        {grupos.map((grupo) => <GrupoDeuda key={grupo.clave} grupo={grupo} />)}
      </View>

      <Boton titulo="Volver a consultar deuda" variante="texto" icono="reintentar" onPress={onConsultar} cargando={ocupado} />
    </View>
  )
}

function GrupoDeuda({ grupo }) {
  const [abierto, setAbierto] = useState(false)
  return (
    <View style={[estilos.grupo, abierto && estilos.grupoAbierto]}>
      <Pressable accessibilityRole="button" accessibilityState={{ expanded: abierto }}
        accessibilityLabel={`${grupo.titulo}, ${soles(grupo.total)}, ${cuotas(grupo.items.length)}. ${abierto ? 'Ocultar' : 'Ver'} períodos`}
        onPress={() => setAbierto(!abierto)} style={({ pressed }) => [estilos.cabeceraGrupo, pressed && estilos.presionado]}>
        <View style={estilos.iconoGrupo}><Icono nombre={grupo.clave === 'IMPUESTO_PREDIAL' ? 'casa' : 'edificio'} tamano={21} /></View>
        <View style={estilos.nombreGrupo}>
          <Text style={estilos.tituloGrupo}>{grupo.titulo}</Text>
          <Text style={texto.pequeno}>{cuotas(grupo.items.length)}</Text>
        </View>
        <View style={estilos.derechaGrupo}>
          <Text style={estilos.subtotal}>{soles(grupo.total)}</Text>
          <View style={estilos.desplegar}>
            <Text style={estilos.textoDesplegar}>{abierto ? 'Ocultar' : 'Ver períodos'}</Text>
            <Icono nombre={abierto ? 'chevron-arriba' : 'chevron-abajo'} tamano={15} />
          </View>
        </View>
      </Pressable>
      {abierto ? <View style={estilos.periodos}>
        {grupo.items.map((item, indice) => <View key={item.id ?? indice} style={[estilos.periodo, indice > 0 && estilos.separador]}>
          <View style={estilos.datosPeriodo}>
            <Text style={estilos.nombrePeriodo}>{periodoDeuda(item.periodo)}</Text>
            {item.codigoPredio ? <Text style={texto.pequeno}>Predio {item.codigoPredio}</Text> : null}
          </View>
          <Text style={estilos.importePeriodo}>{soles(item.deuda)}</Text>
        </View>)}
      </View> : null}
    </View>
  )
}

const estilos = StyleSheet.create({
  tarjeta: { padding: 16, gap: 16, backgroundColor: colores.superficie, borderWidth: 1, borderColor: colores.borde, borderRadius: radios.xl },
  resumen: { padding: 20, backgroundColor: colores.marinoOsc, borderRadius: radios.lg, gap: 6 },
  filaResumen: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  etiquetaTotal: { fontFamily: fuentes.negrita, fontSize: 12, lineHeight: 18, letterSpacing: 1.2, color: '#D4E8FA', flexShrink: 1 },
  iconoTotal: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#31577D', alignItems: 'center', justifyContent: 'center' },
  total: { fontFamily: fuentes.negrita, fontSize: 34, lineHeight: 42, color: colores.blanco, fontVariant: ['tabular-nums'] },
  cantidad: { ...texto.secundario, color: '#D4E8FA' },
  aviso: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, padding: 12, backgroundColor: colores.advertenciaFondo, borderRadius: radios.sm },
  textoAviso: { ...texto.secundario, color: colores.advertenciaTexto, flex: 1 },
  encabezadoDetalle: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginTop: 4 },
  grupos: { gap: 10 },
  grupo: { borderWidth: 1, borderColor: colores.borde, borderRadius: radios.md, overflow: 'hidden' },
  grupoAbierto: { borderColor: colores.bordeFuerte },
  cabeceraGrupo: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, minHeight: 84 },
  presionado: { backgroundColor: colores.azulSuave },
  iconoGrupo: { width: 34, height: 38, borderRadius: 10, backgroundColor: colores.azulSuave, alignItems: 'center', justifyContent: 'center' },
  nombreGrupo: { flex: 1, minWidth: 0, gap: 4 },
  tituloGrupo: { ...texto.etiqueta, fontSize: 15, lineHeight: 19 },
  derechaGrupo: { alignItems: 'flex-end', gap: 6, flexShrink: 0 },
  subtotal: { ...texto.etiqueta, fontSize: 17, lineHeight: 22, fontVariant: ['tabular-nums'] },
  desplegar: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  textoDesplegar: { ...texto.pequeno, fontSize: 12, color: colores.marinoOsc },
  periodos: { paddingHorizontal: 12, borderTopWidth: 1, borderTopColor: colores.borde, backgroundColor: colores.superficieTenue },
  periodo: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, paddingVertical: 13 },
  separador: { borderTopWidth: 1, borderTopColor: colores.borde },
  datosPeriodo: { flex: 1, minWidth: 0, gap: 3 },
  nombrePeriodo: { ...texto.cuerpo, fontSize: 14, lineHeight: 20 },
  importePeriodo: { ...texto.etiqueta, fontSize: 14, lineHeight: 20, fontVariant: ['tabular-nums'], flexShrink: 0 },
})
