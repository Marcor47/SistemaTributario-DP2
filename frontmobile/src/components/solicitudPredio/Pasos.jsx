import { StyleSheet, Text, View } from 'react-native'
import Icono from '../ui/Icono'
import { colores, fuentes } from '../../theme/tokens'

/** Indicador de avance sobre la cabecera: número o check por paso y el nombre del paso actual. */
export default function Pasos({ pasos, actual }) {
  return (
    <View
      style={estilos.raiz}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`Paso ${actual + 1} de ${pasos.length}: ${pasos[actual]}`}
      accessibilityValue={{ min: 1, max: pasos.length, now: actual + 1 }}
    >
      <View style={estilos.fila}>
        {pasos.map((paso, i) => {
          const hecho = i < actual
          const activo = i === actual
          return (
            <View key={paso} style={[estilos.tramo, i === pasos.length - 1 && estilos.ultimo]}>
              <View style={[estilos.punto, hecho && estilos.puntoHecho, activo && estilos.puntoActivo]}>
                {hecho ? (
                  <Icono nombre="check" color={colores.marinoOsc} tamano={16} trazo={3} />
                ) : (
                  <Text style={[estilos.numero, activo && estilos.numeroActivo]}>{i + 1}</Text>
                )}
              </View>
              {i < pasos.length - 1 && <View style={[estilos.linea, hecho && estilos.lineaHecha]} />}
            </View>
          )
        })}
      </View>
      <Text style={estilos.texto}>
        Paso {actual + 1} de {pasos.length} · <Text style={estilos.nombre}>{pasos[actual]}</Text>
      </Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  raiz: { marginTop: 12, gap: 10 },
  fila: { flexDirection: 'row', alignItems: 'center' },
  tramo: { flex: 1, flexDirection: 'row', alignItems: 'center' },
  ultimo: { flex: 0 },
  punto: {
    width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center',
    borderWidth: 2, borderColor: 'rgba(255,255,255,0.55)',
  },
  puntoActivo: { borderColor: colores.cian, backgroundColor: colores.blanco },
  puntoHecho: { borderColor: colores.cian, backgroundColor: colores.cian },
  numero: { fontFamily: fuentes.negrita, fontSize: 15, color: colores.blanco },
  numeroActivo: { color: colores.marinoOsc },
  linea: { flex: 1, height: 3, marginHorizontal: 6, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.3)' },
  lineaHecha: { backgroundColor: colores.cian },
  texto: { fontFamily: fuentes.regular, fontSize: 15, color: '#E1ECF8' },
  nombre: { fontFamily: fuentes.negrita, color: colores.blanco },
})
