// Datos ficticios de demostración (copiados del prototipo navegable).
// En producción vienen de SAMNET a través de la capa de APIs municipal.

export const CONTRIBUYENTES = [
  { codigo: '014502', nombre: 'GARCÍA TORRES, MARÍA ELENA', tipoDoc: 'DNI', doc: '41028765' },
  { codigo: '021390', nombre: 'INVERSIONES COSTA AZUL S.A.C.', tipoDoc: 'RUC', doc: '20456781239' },
  { codigo: '033218', nombre: 'RAMOS QUISPE, VÍCTOR HUGO', tipoDoc: 'DNI', doc: '09215430' },
  { codigo: '045067', nombre: 'CENTRO EDUCATIVO SOL DE LIMA E.I.R.L.', tipoDoc: 'RUC', doc: '20512309876' },
  { codigo: '058934', nombre: 'MEDINA CRUZ, PATRICIA', tipoDoc: 'DNI', doc: '45871203' },
  { codigo: '061177', nombre: 'MEDINA CRUZ, ROBERTO', tipoDoc: 'DNI', doc: '45871777' },
  { codigo: '072845', nombre: 'SALAZAR VEGA, LUCÍA', tipoDoc: 'DNI', doc: '47639182' },
]

// `titulares`: códigos de contribuyente y su porcentaje de propiedad.
export const PREDIOS = [
  {
    codPredio: '014502-01', catastral: 'CAT-014502-01', ubicacion: 'Av. La Marina 1450',
    uso: 'Vivienda', tipo: 'Casa habitación', estado: 'Activo', categoria: 'LP',
    areaTerreno: 220, valorTerreno: 140800, valorNiveles: 44600, valorOtrasInstalaciones: 0,
    baseImponible: 185400, fechaAdquisicion: '2018-06-10', giro: '', observaciones: '',
    titulares: [{ codigo: '014502', pct: 100 }],
  },
  {
    codPredio: '021390-01', catastral: 'CAT-021390-01', ubicacion: 'Jr. Los Precursores 220',
    uso: 'Comercio', tipo: 'Local comercial', estado: 'Activo', categoria: 'PJ',
    areaTerreno: 560, valorTerreno: 459200, valorNiveles: 510000, valorOtrasInstalaciones: 10800,
    baseImponible: 980000, fechaAdquisicion: '2015-02-20', giro: 'Comercio al por menor', observaciones: '',
    titulares: [{ codigo: '021390', pct: 100 }],
  },
  {
    codPredio: '021390-02', catastral: 'CAT-021390-02', ubicacion: 'Jr. Los Precursores 224 (depósito)',
    uso: 'Industria', tipo: 'Depósito', estado: 'Activo', categoria: 'PJ',
    areaTerreno: 180, valorTerreno: 115200, valorNiveles: 0, valorOtrasInstalaciones: 4800,
    baseImponible: 120000, fechaAdquisicion: '2019-09-01', giro: 'Almacenamiento',
    observaciones: 'Predio anexo al principal.',
    titulares: [{ codigo: '021390', pct: 100 }],
  },
  {
    codPredio: '033218-01', catastral: 'CAT-033218-01', ubicacion: 'Calle Bahía 88',
    uso: 'Vivienda', tipo: 'Departamento', estado: 'Activo', categoria: 'SE',
    areaTerreno: 95, valorTerreno: 60800, valorNiveles: 34500, valorOtrasInstalaciones: 0,
    baseImponible: 96500, fechaAdquisicion: '2005-01-12', giro: '', observaciones: '',
    titulares: [{ codigo: '033218', pct: 100 }],
  },
  {
    codPredio: '045067-01', catastral: 'CAT-045067-01', ubicacion: 'Av. Faro Azul 640',
    uso: 'Educación', tipo: 'Local comercial', estado: 'Activo', categoria: 'PJ',
    areaTerreno: 640, valorTerreno: 409600, valorNiveles: 180000, valorOtrasInstalaciones: 6200,
    baseImponible: 410000, fechaAdquisicion: '2010-03-15', giro: 'Enseñanza', observaciones: '',
    titulares: [{ codigo: '045067', pct: 100 }],
  },
  {
    codPredio: '058934-01', catastral: 'CAT-058934-01', ubicacion: 'Jr. Mirador 340',
    uso: 'Vivienda', tipo: 'Casa habitación', estado: 'En trámite', categoria: 'SE',
    areaTerreno: 160, valorTerreno: 102400, valorNiveles: 38500, valorOtrasInstalaciones: 0,
    baseImponible: 142000, fechaAdquisicion: '2020-11-30', giro: '',
    observaciones: 'Predio con DJ en revisión por cambio de área construida.',
    titulares: [{ codigo: '058934', pct: 50 }, { codigo: '061177', pct: 50 }],
  },
]
