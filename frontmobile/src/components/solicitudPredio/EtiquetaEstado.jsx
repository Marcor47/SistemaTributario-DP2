import { StyleSheet, Text, View } from 'react-native'
import Icono from '../ui/Icono'
import { infoEstado } from '../../utils/solicitudPredio'
import { colores, fuentes, radios } from '../../theme/tokens'

// Colores por tono; el estado nunca se comunica solo con el color: siempre lleva icono y texto.
const TONOS = {
  alerta: { fondo: colores.advertenciaFondo, borde: colores.advertenciaBorde, texto: colores.advertencia },
  exito: { fondo: colores.exitoFondo, borde: colores.exitoBorde, texto: colores.exito },
  critico: { fondo: colores.criticoFondo, borde: colores.criticoBorde, texto: colores.criticoTexto },
  info: { fondo: colores.azulSuave, borde: colores.infoBorde, texto: colores.marinoOsc },
}

/** Etiqueta con el estado de una solicitud de predio (En revisión, Aceptada o Rechazada). */
export default function EtiquetaEstado({ estado }) {
  const info = infoEstado(estado)
  const tono = TONOS[info.tono]
  return (
    <View
      accessible
      accessibilityLabel={`Estado: ${info.etiqueta}`}
      style={[estilos.etiqueta, { backgroundColor: tono.fondo, borderColor: tono.borde }]}
    >
      <Icono nombre={info.icono} color={tono.texto} tamano={16} />
      <Text style={[estilos.texto, { color: tono.texto }]}>{info.etiqueta}</Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  etiqueta: {
    flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 4,
    borderRadius: radios.pill, borderWidth: 1,
  },
  texto: { fontFamily: fuentes.negrita, fontSize: 14 },
})
