import { cambiarRolInterno, crearUsuarioInterno, listarRolesInternos, listarUsuariosInternos } from './authInternaService.js'

// HU034/HU035: usuarios y roles internos, siempre desde el servidor (exige sesión y permisos).
export async function cargarUsuariosYRoles(cargarRoles = true) {
  const [usuarios, roles] = await Promise.all([listarUsuariosInternos(), cargarRoles ? listarRolesInternos() : []])
  return { usuarios, roles }
}

export const crearUsuario = (datos) => crearUsuarioInterno(datos)

// Registro del cambio con usuario, fecha y valor anterior (criterio de aceptación de HU035).
export function construirRegistroCambio(usuario, rolNuevo, motivo, ahora = new Date()) {
  return {
    idUsuario: usuario.idUsuario,
    nombreUsuario: usuario.nombreUsuario,
    nombreCompleto: usuario.nombreCompleto,
    rolAnterior: usuario.rol,
    rolNuevo,
    motivo: (motivo ?? '').trim(),
    fecha: ahora.toISOString(),
  }
}

export async function cambiarRol(usuario, rolNuevo, motivo) {
  if (!rolNuevo) {
    throw Object.assign(new Error('Selecciona el nuevo rol.'), { codigo: 'ROL_REQUERIDO' })
  }
  if (rolNuevo === usuario.rol) {
    throw Object.assign(new Error('El usuario ya tiene ese rol.'), { codigo: 'ROL_IDENTICO' })
  }

  const actualizado = await cambiarRolInterno(usuario.idUsuario, rolNuevo, motivo)
  return { usuario: actualizado, registro: construirRegistroCambio(usuario, rolNuevo, motivo) }
}
