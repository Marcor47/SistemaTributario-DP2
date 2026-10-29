import { Pressable, StyleSheet, Text, View } from 'react-native'
import Boton from '../ui/Boton'
import Icono from '../ui/Icono'
import { formatearTamano } from '../../services/adjuntos'
import { colores, fuentes, radios, sombra, tamanos } from '../../theme/tokens'

/** Un documento requerido: estado (pendiente/adjunto), archivo elegido y acciones para adjuntar, cambiar o quitar. */
export default function TarjetaRequisito({ requisito, archivo, error, onAdjuntar, onQuitar, deshabilitado }) {
  const listo = Boolean(archivo)
  return (
    <View style={[estilos.tarjeta, listo && estilos.lista, error && estilos.conError]}>
      <View style={estilos.cabeza}>
        <View style={[estilos.estado, listo && estilos.estadoListo]}>
          <Icono nombre={listo ? 'check' : 'documento'} color={listo ? colores.blanco : colores.marino} tamano={18} trazo={listo ? 3 : 2} />
        </View>
        <View style={estilos.textos}>
          <Text style={estilos.nombre}>{requisito.nombre}</Text>
          <Text style={[estilos.etiqueta, requisito.obligatorio ? estilos.obligatorio : estilos.opcional]}>
            {requisito.obligatorio ? 'Obligatorio' : 'Opcional'}
          </Text>
        </View>
      </View>

      {listo ? (
        <View style={estilos.archivo}>
          <Icono nombre={archivo.tipo === 'application/pdf' ? 'documento' : 'imagen'} color={colores.marino} />
          <View style={estilos.textos}>
            <Text style={estilos.archivoNombre} numberOfLines={1} ellipsizeMode="middle">{archivo.nombre}</Text>
            <Text style={estilos.archivoTamano}>{formatearTamano(archivo.tamano)}</Text>
          </View>
          <Pressable
            onPress={onAdjuntar}
            disabled={deshabilitado}
            accessibilityRole="button"
            accessibilityLabel={`Cambiar archivo de ${requisito.nombre}`}
            style={({ pressed }) => [estilos.accion, pressed && estilos.accionPresionada]}
          >
            <Icono nombre="reintentar" color={colores.marino} tamano={20} />
          </Pressable>
          <Pressable
            onPress={onQuitar}
            disabled={deshabilitado}
            accessibilityRole="button"
            accessibilityLabel={`Quitar archivo de ${requisito.nombre}`}
            style={({ pressed }) => [estilos.accion, pressed && estilos.accionPresionadaPeligro]}
          >
            <Icono nombre="cerrar" color={colores.critico} tamano={20} />
          </Pressable>
        </View>
      ) : (
        <Boton
          titulo="Adjuntar documento"
          icono="clip"
          variante="secundario"
          onPress={onAdjuntar}
          deshabilitado={deshabilitado}
          accessibilityHint={`Adjunta: ${requisito.nombre}`}
        />
      )}

      {error ? (
        <View style={estilos.error} accessibilityLiveRegion="polite">
          <Icono nombre="alerta" color={colores.critico} tamano={16} />
          <Text style={estilos.errorTexto}>{error}</Text>
        </View>
      ) : null}
    </View>
  )
}

const estilos = StyleSheet.create({
  tarjeta: {
    gap: 14, padding: 16, backgroundColor: colores.superficie, borderRadius: radios.lg,
    borderWidth: 1, borderColor: colores.borde, ...sombra,
  },
  lista: { borderColor: colores.exitoBorde },
  conError: { borderColor: colores.critico },
  cabeza: { flexDirection: 'row', gap: 12, alignItems: 'flex-start' },
  estado: {
    width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colores.azulSuave,
  },
  estadoListo: { backgroundColor: colores.exito },
  textos: { flex: 1, gap: 4 },
  nombre: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc },
  etiqueta: {
    alignSelf: 'flex-start', paddingHorizontal: 8, paddingVertical: 2, borderRadius: radios.pill,
    fontFamily: fuentes.negrita, fontSize: 12, lineHeight: 18, overflow: 'hidden',
  },
  obligatorio: { color: colores.marinoOsc, backgroundColor: colores.azulSuave },
  opcional: { color: colores.textoSecundario, backgroundColor: '#EEF2F7' },
  archivo: {
    flexDirection: 'row', alignItems: 'center', gap: 10, paddingLeft: 12, paddingVertical: 4,
    backgroundColor: colores.superficieTenue, borderRadius: radios.md, borderWidth: 1, borderColor: '#E3ECF5',
  },
  archivoNombre: { fontFamily: fuentes.negrita, fontSize: 15, color: colores.texto },
  archivoTamano: { fontFamily: fuentes.regular, fontSize: 13, color: colores.textoAtenuado },
  accion: { width: tamanos.toque, height: tamanos.toque, alignItems: 'center', justifyContent: 'center', borderRadius: radios.md },
  accionPresionada: { backgroundColor: colores.azulSuave },
  accionPresionadaPeligro: { backgroundColor: colores.criticoFondo },
  error: { flexDirection: 'row', gap: 6, alignItems: 'flex-start' },
  errorTexto: { flex: 1, fontFamily: fuentes.regular, fontSize: 14, lineHeight: 20, color: colores.critico },
})
