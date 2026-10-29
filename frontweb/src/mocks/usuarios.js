// Usuarios internos ficticios para la demostración de HU035 (misma forma que UsuarioRespuesta del backend).
export const ROLES_DEMO = [
  { nombre: 'ADMINISTRADOR', descripcion: 'Gestiona usuarios internos, roles y permisos.' },
  { nombre: 'GERENCIA', descripcion: 'Acceso total a la plataforma interna de la Gerencia de Rentas.' },
  { nombre: 'ANALISTA_RENTAS', descripcion: 'Analítica, reportes y registro de predios.' },
  { nombre: 'RESPONSABLE_DATOS', descripcion: 'Homologación IA y auditoría ETL.' },
  { nombre: 'VENTANILLA', descripcion: 'Constancia de No Adeudo por DNI en atención presencial.' },
  { nombre: 'TERMINALISTA', descripcion: 'Revisión de declaraciones juradas en mesa de partes.' },
  { nombre: 'GESTOR_RECLAMOS', descripcion: 'Recibe reclamos y los deriva al área correspondiente.' },
]

export const USUARIOS_DEMO = [
  { idUsuario: 1, nombreUsuario: 'admin', nombreCompleto: 'Rosa Quispe Huamán', correo: 'admin@demo.muni', rol: 'ADMINISTRADOR', activo: true, fechaCreacion: '2026-09-01T09:00:00' },
  { idUsuario: 2, nombreUsuario: 'gerencia', nombreCompleto: 'Jesús Paredes Salas', correo: 'gerencia@demo.muni', rol: 'GERENCIA', activo: true, fechaCreacion: '2026-09-01T09:05:00' },
  { idUsuario: 3, nombreUsuario: 'analista1', nombreCompleto: 'Lucía Mendoza Rojas', correo: 'analista1@demo.muni', rol: 'ANALISTA_RENTAS', activo: true, fechaCreacion: '2026-09-10T10:30:00' },
  { idUsuario: 4, nombreUsuario: 'datos1', nombreCompleto: 'Carlos Vega Ñahui', correo: 'datos1@demo.muni', rol: 'RESPONSABLE_DATOS', activo: true, fechaCreacion: '2026-09-10T10:45:00' },
  { idUsuario: 5, nombreUsuario: 'ventanilla1', nombreCompleto: 'María Torres Ccori', correo: 'ventanilla1@demo.muni', rol: 'VENTANILLA', activo: true, fechaCreacion: '2026-09-24T08:15:00' },
  { idUsuario: 6, nombreUsuario: 'terminal1', nombreCompleto: 'Pedro Alva Lazo', correo: 'terminal1@demo.muni', rol: 'TERMINALISTA', activo: true, fechaCreacion: '2026-09-24T08:20:00' },
  { idUsuario: 7, nombreUsuario: 'reclamos1', nombreCompleto: 'Ana Delgado Cruz', correo: 'reclamos1@demo.muni', rol: 'GESTOR_RECLAMOS', activo: true, fechaCreacion: '2026-10-12T11:00:00' },
  { idUsuario: 8, nombreUsuario: 'ventanilla2', nombreCompleto: 'Jorge Ramos Peña', correo: 'ventanilla2@demo.muni', rol: 'VENTANILLA', activo: false, fechaCreacion: '2026-10-01T14:00:00' },
]
