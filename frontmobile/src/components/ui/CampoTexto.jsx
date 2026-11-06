import { forwardRef, useState } from 'react'
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native'
import Icono from './Icono'
import { colores, fuentes, radios, tamanos } from '../../theme/tokens'

/** Campo con etiqueta visible, ayuda y error junto al campo; con `clave` añade el botón de mostrar contraseña. */
const CampoTexto = forwardRef(function CampoTexto(
  { etiqueta, ayuda, error, clave = false, style, onFocus, onBlur, ...props }, ref,
) {
  const [enfocado, setEnfocado] = useState(false)
  const [ver, setVer] = useState(false)
  const borde = error ? colores.critico : enfocado ? colores.marino : colores.bordeFuerte
  return (
    <View style={[estilos.campo, style]}>
      <Text style={estilos.etiqueta}>{etiqueta}</Text>
      <View style={[estilos.caja, { borderColor: borde }, enfocado && estilos.enfocado]}>
        <TextInput
          ref={ref}
          style={estilos.input}
          placeholderTextColor={colores.textoAtenuado}
          secureTextEntry={clave && !ver}
          accessibilityLabel={etiqueta}
          accessibilityHint={error || ayuda}
          {...props}
          onFocus={(e) => { setEnfocado(true); onFocus?.(e) }}
          onBlur={(e) => { setEnfocado(false); onBlur?.(e) }}
        />
        {clave && (
          <Pressable
            onPress={() => setVer((v) => !v)}
            accessibilityRole="button"
            accessibilityLabel={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            accessibilityState={{ selected: ver }}
            hitSlop={4}
            style={({ pressed }) => [estilos.ojo, pressed && { backgroundColor: colores.azulSuave }]}
          >
            <Icono nombre={ver ? 'ojo-tachado' : 'ojo'} color={colores.textoSecundario} />
          </Pressable>
        )}
      </View>
      {error ? (
        <View style={estilos.fila}>
          <Icono nombre="alerta" color={colores.critico} tamano={16} />
          <Text style={estilos.error}>{error}</Text>
        </View>
      ) : ayuda ? (
        <Text style={estilos.ayuda}>{ayuda}</Text>
      ) : null}
    </View>
  )
})

export default CampoTexto

const estilos = StyleSheet.create({
  campo: { gap: 8 },
  etiqueta: { fontFamily: fuentes.negrita, fontSize: 16, lineHeight: 22, color: colores.marinoOsc },
  caja: {
    flexDirection: 'row', alignItems: 'center', minHeight: 52, borderWidth: 2, borderRadius: radios.md,
    backgroundColor: colores.superficie,
  },
  enfocado: { shadowColor: colores.cian, shadowOpacity: 0.45, shadowRadius: 4, shadowOffset: { width: 0, height: 0 } },
  input: {
    flex: 1, minHeight: 48, paddingHorizontal: 14, paddingVertical: 10, fontFamily: fuentes.regular, fontSize: 17,
    color: colores.marinoOsc,
  },
  ojo: { width: tamanos.toque, height: tamanos.toque, alignItems: 'center', justifyContent: 'center', borderRadius: radios.sm, marginRight: 2 },
  fila: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  error: { flex: 1, fontFamily: fuentes.regular, fontSize: 14, lineHeight: 20, color: colores.critico },
  ayuda: { fontFamily: fuentes.regular, fontSize: 14, lineHeight: 20, color: colores.textoAtenuado },
})
