// Valores de TipoTramitePredio y TipoPredio del backend, con su texto para el contribuyente.
export const TRAMITES = [
  {
    valor: 'REGISTRO', icono: 'casa', titulo: 'Registro de predio',
    descripcion: 'Inscribe a tu nombre un predio que aún no está registrado en la municipalidad.',
  },
  {
    valor: 'TRASPASO', icono: 'traspaso', titulo: 'Traspaso de predio',
    descripcion: 'Registra la transferencia de un predio ya inscrito. Necesitarás su código.',
  },
]

export const PREDIOS = [
  { valor: 'URBANO', icono: 'edificio', titulo: 'Urbano', descripcion: 'Casa, departamento, local o terreno en zona urbana.' },
  { valor: 'RUSTICO', icono: 'brote', titulo: 'Rústico', descripcion: 'Terreno agrícola o fuera de la zona urbana.' },
  { valor: 'OTRO', icono: 'cuadricula', titulo: 'Otro', descripcion: 'Si tu predio no corresponde a los anteriores.' },
]

export const ESTADOS = {
  EN_REVISION: 'En revisión',
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
}
