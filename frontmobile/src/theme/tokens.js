// Tokens visuales tomados de frontweb/src/index.css y del logo de la Municipalidad de San Miguel,
// para que la app móvil conserve la identidad del portal web.
export const colores = {
  marino: '#35497D',
  marinoOsc: '#17406B',
  cian: '#19C3F3',
  azulLogo: '#3F97D4',
  azulSuave: '#E6F4FB',
  texto: '#1E2B3C',
  textoSecundario: '#3A4A5F',
  textoAtenuado: '#51627A',
  fondo: '#F1F5FA',
  superficie: '#FFFFFF',
  superficieTenue: '#F7FAFD',
  borde: '#D6E2EF',
  bordeFuerte: '#B9C8DA',
  critico: '#B42318',
  criticoTexto: '#8A1C12',
  criticoFondo: '#FDF1F0',
  criticoBorde: '#E8A39C',
  exito: '#067647',
  exitoFondo: '#ECFDF3',
  exitoBorde: '#9ADBB8',
  advertencia: '#7A4100',
  advertenciaTexto: '#5E3300',
  advertenciaFondo: '#FFF4DF',
  advertenciaBorde: '#E8C98A',
  advertenciaAcento: '#D9892B',
  infoBorde: '#BFE6F5',
  blanco: '#FFFFFF',
}

export const fuentes = {
  regular: 'AtkinsonHyperlegible_400Regular',
  negrita: 'AtkinsonHyperlegible_700Bold',
}

export const radios = { sm: 8, md: 10, lg: 14, xl: 20, pill: 999 }

// Ritmo de 4/8 dp.
export const espacio = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 }

export const tamanos = { toque: 48, icono: 22, iconoSm: 18 }

export const sombra = {
  shadowColor: colores.marinoOsc,
  shadowOpacity: 0.08,
  shadowRadius: 14,
  shadowOffset: { width: 0, height: 6 },
  elevation: 3,
}

export const texto = {
  titulo: { fontFamily: fuentes.negrita, fontSize: 28, lineHeight: 34, color: colores.marinoOsc },
  subtitulo: { fontFamily: fuentes.negrita, fontSize: 20, lineHeight: 26, color: colores.marinoOsc },
  cuerpo: { fontFamily: fuentes.regular, fontSize: 16, lineHeight: 24, color: colores.texto },
  secundario: { fontFamily: fuentes.regular, fontSize: 15, lineHeight: 22, color: colores.textoSecundario },
  etiqueta: { fontFamily: fuentes.negrita, fontSize: 15, lineHeight: 20, color: colores.marinoOsc },
  pequeno: { fontFamily: fuentes.regular, fontSize: 13, lineHeight: 18, color: colores.textoAtenuado },
}
