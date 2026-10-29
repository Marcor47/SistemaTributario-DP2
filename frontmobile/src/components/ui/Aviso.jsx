import { StyleSheet, Text, View } from 'react-native'
import Icono from './Icono'
import { colores, fuentes } from '../../theme/tokens'

// Los cuatro tipos de mensaje de la web: .info, .alert, .confirmacion y .lg-alerta.
const TIPOS = {
  info: { fondo: colores.azulSuave, borde: colores.infoBorde, acento: colores.cian, texto: colores.marinoOsc, icono: 'info' },
  alerta: { fondo: colores.advertenciaFondo, borde: colores.advertenciaBorde, acento: colores.advertenciaAcento, texto: colores.advertencia, icono: 'alerta' },
  exito: { fondo: colores.exitoFondo, borde: colores.exitoBorde, acento: colores.exito, texto: colores.exito, icono: 'check-circulo' },
  critico: { fondo: colores.criticoFondo, borde: colores.criticoBorde, acento: colores.critico, texto: colores.criticoTexto, icono: 'alerta' },
}

export default function Aviso({ tipo = 'info', titulo, children, accion, style }) {
  const t = TIPOS[tipo]
  return (
    <View
      accessibilityRole={tipo === 'critico' ? 'alert' : undefined}
      accessibilityLiveRegion={tipo === 'critico' ? 'assertive' : 'polite'}
      style={[estilos.caja, { backgroundColor: t.fondo, borderColor: t.borde, borderLeftColor: t.acento }, style]}
    >
      <Icono nombre={t.icono} color={t.texto} tamano={20} />
      <View style={estilos.cuerpo}>
        {titulo ? <Text style={[estilos.titulo, { color: t.texto }]}>{titulo}</Text> : null}
        {typeof children === 'string' ? <Text style={[estilos.texto, { color: t.texto }]}>{children}</Text> : children}
        {accion}
      </View>
    </View>
  )
}

const estilos = StyleSheet.create({
  caja: {
    flexDirection: 'row', gap: 10, alignItems: 'flex-start', padding: 14, borderRadius: 12,
    borderWidth: 1, borderLeftWidth: 5,
  },
  cuerpo: { flex: 1, gap: 4 },
  titulo: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 21 },
  texto: { fontFamily: fuentes.regular, fontSize: 15, lineHeight: 21 },
})
