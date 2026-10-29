// Perfiles de acceso de RF-047 más el Gestor de reclamos de RF-064 (enum NombreRol del backend).
export const ETIQUETAS_ROL = {
  ADMINISTRADOR: 'Administrador',
  GERENCIA: 'Gerencia de Rentas',
  ANALISTA_RENTAS: 'Analista de Rentas',
  RESPONSABLE_DATOS: 'Responsable de datos',
  VENTANILLA: 'Ventanilla',
  TERMINALISTA: 'Terminalista',
  GESTOR_RECLAMOS: 'Gestor de reclamos',
}

export function etiquetaRol(rol) {
  return ETIQUETAS_ROL[rol] ?? (rol || 'Sin rol')
}

// Consultas internas de predios (HU003 y HU005): solo estos roles pueden ver la información.
export const ROLES_CONSULTA_PREDIO = ['ANALISTA_RENTAS', 'ADMINISTRADOR']

export function tieneRolInterno(sesion, roles = ROLES_CONSULTA_PREDIO) {
  return sesion?.tipo === 'interno' && roles.some((rol) => sesion.usuario?.roles?.includes(rol))
}
