// Reclamos ficticios del trabajador interno para la demostración de HU050 (misma forma que el modelo Reclamo del backend).
export const RECLAMOS_DEMO = [
  {
    idReclamo: 1,
    numeroReclamo: 'REC-2026-000001',
    tipoReclamante: 'TRABAJADOR',
    tipoReclamo: 'AREA_RELACIONADA',
    referencia: 'Oficina de Rentas, módulo 3',
    fechaHoraIncidente: '2026-10-14T09:30:00',
    comentario: 'El equipo de cómputo del módulo 3 lleva una semana sin conexión a la red y no puedo atender a los contribuyentes.',
    estado: 'EN_REVISION',
    fechaRegistro: '2026-10-14T11:05:00',
    respuesta: null,
    fechaRespuesta: null,
    derivaciones: [
      {
        fechaDerivacion: '2026-10-15T08:40:00',
        areaDestino: 'Tecnologías de la Información',
        responsable: 'Carlos Vega Ñahui',
        derivadoPor: 'Ana Delgado Cruz',
        observacion: 'Revisar la conexión de red del módulo 3.',
      },
    ],
  },
  {
    idReclamo: 2,
    numeroReclamo: 'REC-2026-000002',
    tipoReclamante: 'TRABAJADOR',
    tipoReclamo: 'DOCUMENTO',
    referencia: 'Memorándum 045-2026',
    fechaHoraIncidente: '2026-10-02T15:00:00',
    comentario: 'El memorándum indica un horario de turno distinto al acordado con mi jefatura.',
    estado: 'APROBADO_CON_RESPUESTA',
    fechaRegistro: '2026-10-02T16:20:00',
    respuesta: 'Se corrigió el memorándum y se emitió una versión actualizada con el horario acordado.',
    fechaRespuesta: '2026-10-09T10:15:00',
    derivaciones: [
      {
        fechaDerivacion: '2026-10-03T09:00:00',
        areaDestino: 'Recursos Humanos',
        responsable: 'María Torres Ccori',
        derivadoPor: 'Ana Delgado Cruz',
        observacion: null,
      },
    ],
  },
]
