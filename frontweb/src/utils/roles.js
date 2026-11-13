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

/** Permisos de un rol en formato normalizado, venga como texto separado por comas o como lista. */
export function permisosDeRol(rol) {
  const fuente = Array.isArray(rol?.permisosLista) ? rol.permisosLista : rol?.permisos
  const permisos = Array.isArray(fuente) ? fuente : String(fuente ?? '').split(',')
  return [...new Set(
    permisos.map((permiso) => String(permiso).trim().toUpperCase()).filter(Boolean),
  )]
}

export function rolTienePermiso(rol, permiso) {
  const buscado = String(permiso ?? '').trim().toUpperCase()
  return Boolean(buscado) && permisosDeRol(rol).includes(buscado)
}

// Consultas internas de predios (HU003 y HU005): solo estos roles pueden ver la información.
export const ROLES_CONSULTA_PREDIO = ['ANALISTA_RENTAS', 'ADMINISTRADOR']

// La sesión interna (SesionInternaRespuesta) expone los roles del usuario en `roles`.
export function tieneRolInterno(sesion, roles = ROLES_CONSULTA_PREDIO) {
  const asignados = Array.isArray(sesion?.roles) ? sesion.roles : []
  if (roles.some((rol) => asignados.includes(rol))) return true
  return Boolean(sesion?.rol) && roles.includes(sesion.rol)
}

// Un módulo puede restringirse por permiso, por rol o por ambos.
export function puedeAccederModulo(modulo, sesion) {
  if (!modulo) return false
  const cumplePermiso = !modulo.permiso || Boolean(sesion?.permisos?.includes(modulo.permiso))
  const cumpleRol = !modulo.roles?.length || tieneRolInterno(sesion, modulo.roles)
  return cumplePermiso && cumpleRol
}
