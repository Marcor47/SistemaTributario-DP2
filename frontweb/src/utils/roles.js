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
