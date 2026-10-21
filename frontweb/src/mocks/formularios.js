// Catálogo compartido de demostración. Conserva las publicaciones anteriores;
// el servicio expone solamente la última versión publicada de cada documento.
export const FORMULARIOS = [
  {
    codigo: 'PL-001',
    nombre: 'Constancia de No Adeudo',
    tipo: 'Plantilla',
    descripcion: 'Formato de referencia para la emisión de la Constancia de No Adeudo.',
    versiones: [
      { numero: 1, fechaPublicacion: '2026-09-01', archivo: 'constancia-no-adeudo-v1.pdf' },
      { numero: 2, fechaPublicacion: '2026-10-19', archivo: 'constancia-no-adeudo-v2.pdf' },
    ],
  },
  {
    codigo: 'FO-001',
    nombre: 'Hoja de Resumen (HR)',
    tipo: 'Formulario',
    descripcion: 'Resumen de datos del contribuyente y de sus predios para el llenado manual.',
    versiones: [
      { numero: 1, fechaPublicacion: '2026-10-12', archivo: 'hoja-resumen-v1.pdf' },
    ],
  },
  {
    codigo: 'FO-002',
    nombre: 'Declaración Jurada de Predio',
    tipo: 'Formulario',
    descripcion: 'Datos de identificación, ubicación y características del predio.',
    versiones: [
      { numero: 1, fechaPublicacion: '2026-08-10', archivo: 'declaracion-jurada-predio-v1.pdf' },
      { numero: 2, fechaPublicacion: '2026-09-15', archivo: 'declaracion-jurada-predio-v2.pdf' },
      { numero: 3, fechaPublicacion: '2026-10-15', archivo: 'declaracion-jurada-predio-v3.pdf' },
    ],
  },
  {
    codigo: 'FO-003',
    nombre: 'Solicitud de Registro o Traspaso de Predio',
    tipo: 'Formulario',
    descripcion: 'Solicitud para iniciar el registro o traspaso de un predio en ventanilla.',
    versiones: [
      { numero: 1, fechaPublicacion: '2026-10-16', archivo: 'solicitud-registro-traspaso-v1.pdf' },
    ],
  },
]
