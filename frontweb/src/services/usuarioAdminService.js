import { MODO_DEMO } from './authService.js'
import { cambiarRolInterno, crearUsuarioInterno, listarRolesInternos, listarUsuariosInternos } from './authInternaService.js'
import { ROLES_DEMO, USUARIOS_DEMO } from '../mocks/usuarios.js'

// HU034/HU035: los endpoints administrativos exigen sesión y permisos; solo el modo demo o la falta de conexión
// habilitan datos ficticios.
let usuariosDemo = USUARIOS_DEMO.map((usuario) => ({ ...usuario }))

const esperar = () => new Promise((resolve) => setTimeout(resolve, 250))
const usarDatosDemo = (err) => MODO_DEMO || err.codigo === 'SIN_CONEXION'

export function reiniciarUsuariosDemo() {
  usuariosDemo = USUARIOS_DEMO.map((usuario) => ({ ...usuario }))
}

// Devuelve { usuarios, roles, demo }; demo indica que los datos son ficticios y no se guardan en el servidor.
export async function cargarUsuariosYRoles(cargarRoles = true) {
  try {
    const [usuarios, roles] = await Promise.all([listarUsuariosInternos(), cargarRoles ? listarRolesInternos() : []])
    return { usuarios, roles, demo: false }
  } catch (err) {
    if (!usarDatosDemo(err)) throw err
    await esperar()
    return { usuarios: usuariosDemo.map((usuario) => ({ ...usuario })), roles: ROLES_DEMO, demo: true }
  }
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

export async function cambiarRol(usuario, rolNuevo, motivo, demo) {
  if (!rolNuevo) {
    throw Object.assign(new Error('Selecciona el nuevo rol.'), { codigo: 'ROL_REQUERIDO' })
  }
  if (rolNuevo === usuario.rol) {
    throw Object.assign(new Error('El usuario ya tiene ese rol.'), { codigo: 'ROL_IDENTICO' })
  }

  let actualizado
  if (demo) {
    await esperar()
    usuariosDemo = usuariosDemo.map((u) => (u.idUsuario === usuario.idUsuario ? { ...u, rol: rolNuevo } : u))
    actualizado = usuariosDemo.find((u) => u.idUsuario === usuario.idUsuario)
  } else {
    actualizado = await cambiarRolInterno(usuario.idUsuario, rolNuevo, motivo)
  }
  return { usuario: actualizado, registro: construirRegistroCambio(usuario, rolNuevo, motivo) }
}
