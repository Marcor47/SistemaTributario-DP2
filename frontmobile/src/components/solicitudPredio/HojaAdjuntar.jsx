import { Modal, Platform, Pressable, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import Boton from '../ui/Boton'
import Icono from '../ui/Icono'
import { colores, fuentes, radios, texto } from '../../theme/tokens'

// En web no hay cámara de la app: se ofrece solo galería y archivo.
const ORIGENES = [
  { id: 'camara', icono: 'camara', titulo: 'Tomar foto', descripcion: 'Fotografía el documento con buena luz.', soloNativo: true },
  { id: 'galeria', icono: 'imagen', titulo: 'Elegir de la galería', descripcion: 'Imagen JPG o PNG.' },
  { id: 'archivo', icono: 'documento', titulo: 'Elegir archivo', descripcion: 'PDF, JPG o PNG desde tus archivos.' },
]

/** Hoja inferior para elegir de dónde se adjunta el documento. */
export default function HojaAdjuntar({ visible, requisito, onElegir, onCerrar, onDismiss }) {
  const { bottom } = useSafeAreaInsets()
  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onCerrar}
      onDismiss={onDismiss}
      statusBarTranslucent
    >
      <View style={estilos.fondo}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onCerrar} accessibilityRole="button" accessibilityLabel="Cerrar" />
        <View style={[estilos.hoja, { paddingBottom: bottom + 16 }]} accessibilityViewIsModal>
          <View style={estilos.asa} />
          <Text style={texto.subtitulo} accessibilityRole="header">Adjuntar documento</Text>
          {requisito ? <Text style={[texto.secundario, estilos.req]}>{requisito.nombre}</Text> : null}
          <View style={estilos.opciones}>
            {ORIGENES.filter((o) => !(o.soloNativo && Platform.OS === 'web')).map((o) => (
              <Pressable
                key={o.id}
                onPress={() => onElegir(o.id)}
                accessibilityRole="button"
                accessibilityLabel={o.titulo}
                accessibilityHint={o.descripcion}
                style={({ pressed }) => [estilos.opcion, pressed && estilos.opcionPresionada]}
              >
                <View style={estilos.icono}><Icono nombre={o.icono} color={colores.marino} /></View>
                <View style={estilos.textos}>
                  <Text style={estilos.titulo}>{o.titulo}</Text>
                  <Text style={texto.pequeno}>{o.descripcion}</Text>
                </View>
              </Pressable>
            ))}
          </View>
          <Text style={[texto.pequeno, estilos.nota]}>Formatos aceptados: PDF, JPG o PNG. Máximo 10 MB por archivo.</Text>
          <Boton titulo="Cancelar" variante="texto" onPress={onCerrar} />
        </View>
      </View>
    </Modal>
  )
}

const estilos = StyleSheet.create({
  fondo: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(15,23,42,0.55)' },
  hoja: {
    paddingHorizontal: 20, paddingTop: 12, backgroundColor: colores.superficie,
    borderTopLeftRadius: 24, borderTopRightRadius: 24, gap: 4,
  },
  asa: { alignSelf: 'center', width: 44, height: 5, borderRadius: radios.pill, backgroundColor: colores.bordeFuerte, marginBottom: 12 },
  req: { marginBottom: 12 },
  opciones: { gap: 8 },
  opcion: {
    flexDirection: 'row', alignItems: 'center', gap: 14, minHeight: 64, paddingHorizontal: 12, paddingVertical: 10,
    borderRadius: radios.md, borderWidth: 1, borderColor: colores.borde,
  },
  opcionPresionada: { backgroundColor: colores.azulSuave, borderColor: colores.marino },
  icono: { width: 44, height: 44, borderRadius: radios.md, alignItems: 'center', justifyContent: 'center', backgroundColor: colores.azulSuave },
  textos: { flex: 1 },
  titulo: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc },
  nota: { marginTop: 12, marginBottom: 4, textAlign: 'center' },
})
