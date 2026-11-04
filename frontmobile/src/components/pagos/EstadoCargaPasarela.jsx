import { StyleSheet, Text, View } from 'react-native'
import Boton from '../ui/Boton'
import { colores, texto } from '../../theme/tokens'

export default function EstadoCargaPasarela({ fallo, onReintentar }) {
  return <View style={estilos.aviso}>
    <Text style={texto.secundario} accessibilityLiveRegion="polite">
      {fallo ? 'No se pudo descargar el formulario de pago. Revisa tu conexión y vuelve a cargarlo.'
        : 'El formulario está tardando en cargar. Puedes seguir esperando. Reintentar comenzará la carga de nuevo.'}
    </Text>
    <Boton titulo="Reintentar carga" variante="secundario" onPress={onReintentar} />
  </View>
}

const estilos = StyleSheet.create({
  aviso: { padding: 16, gap: 12, backgroundColor: colores.advertenciaFondo },
})
