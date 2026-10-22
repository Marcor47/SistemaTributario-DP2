export const MINIMO_BUSQUEDA = 2

export const TIPOS_DOCUMENTO = {
  DNI: { maximo: 8, ayuda: '8 dígitos. Puedes buscar con parte del número.' },
  RUC: { maximo: 11, ayuda: '11 dígitos. Puedes buscar con parte del número.' },
  CE: { maximo: 12, ayuda: 'De 9 a 12 caracteres alfanuméricos. Puedes buscar con parte del número.' },
}

export function limpiarDocumento(valor, tipoDocumento) {
  const texto = String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
  if (tipoDocumento === 'CE') return texto.replace(/[^A-Z0-9]/g, '').slice(0, TIPOS_DOCUMENTO.CE.maximo)
  return texto.replace(/\D/g, '').slice(0, TIPOS_DOCUMENTO[tipoDocumento].maximo)
}

export function validarDocumento(tipoDocumento, numero) {
  if (numero.length < MINIMO_BUSQUEDA) {
    return `Ingresa al menos ${MINIMO_BUSQUEDA} caracteres del documento.`
  }
  if (tipoDocumento === 'RUC' && numero.length === 11 && !/^(10|15|17|20)\d{9}$/.test(numero)) {
    return 'El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20.'
  }
  return ''
}
