export const icono = (children) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

export const MODULOS = [
  {
    ruta: '/estado-cuenta',
    texto: 'Estado de cuenta',
    titulo: 'Mi estado de cuenta',
    descripcion: 'Consulta tus deudas vigentes, pagos realizados y obligaciones tributarias en Servicios en Línea.',
    seccion: 'portal',
    icono: icono(
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="M3 10h18M7 15h4" />
      </>
    ),
  },
  {
    ruta: '/constancias',
    texto: 'Constancia de No Adeudo',
    titulo: 'Firma de solicitud de constancia',
    descripcion: 'Prepara y firma tu solicitud de Constancia de No Adeudo desde el portal.',
    seccion: 'portal',
    icono: icono(<><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 15l2 2 5-5" /></>),
  },
  {
    ruta: '/administracion/usuarios',
    texto: 'Usuarios',
    titulo: 'Administración de usuarios',
    descripcion: 'Crea cuentas institucionales y asigna el rol correspondiente a cada funcionario.',
    seccion: 'gestion',
    permiso: 'USUARIOS:LISTAR',
    icono: icono(<><circle cx="9" cy="8" r="4" /><path d="M2 21c0-4 3.1-7 7-7s7 3 7 7M18 8v6M15 11h6" /></>),
  },
  {
    ruta: '/predios',
    texto: 'Predios',
    titulo: 'Consulta de predios',
    descripcion: 'Ubica un predio por su código o por el contribuyente asociado y revisa sus características y titulares.',
    seccion: 'gestion',
    permiso: 'PREDIOS:CONSULTAR',
    icono: icono(<path d="M3 21V9l9-6 9 6v12M9 21v-7h6v7" />),
  },
  {
    ruta: '/contribuyentes',
    texto: 'Contribuyentes',
    titulo: 'Consulta de contribuyentes',
    descripcion: 'Busca por documento o nombre y revisa la ficha, el estado de cuenta y la regularidad de pago.',
    seccion: 'gestion',
    permiso: 'CONTRIBUYENTES:CONSULTAR',
    icono: icono(<><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></>),
  },
  {
    ruta: '/metas',
    texto: 'Metas',
    titulo: 'Metas de recaudación',
    descripcion: 'Configura las metas de recaudación de la Gerencia de Rentas y da seguimiento a su cumplimiento.',
    seccion: 'gestion',
    permiso: 'METAS:GESTIONAR',
    icono: icono(<><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1" /></>),
  },
  {
    ruta: '/formularios',
    texto: 'Formularios',
    titulo: 'Formularios y plantillas',
    descripcion: 'Descarga y administra las plantillas oficiales de la Municipalidad.',
    seccion: 'gestion',
    permiso: 'FORMULARIOS:CONSULTAR',
    icono: icono(<><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" /><path d="M14 3v5h5M9 13h6M9 17h6" /></>),
  },
  {
    ruta: '/usuarios',
    texto: 'Usuarios y roles',
    titulo: 'Usuarios internos y roles',
    descripcion: 'Cambia el rol de los usuarios internos; cada cambio queda registrado para auditoría.',
    seccion: 'gestion',
    permiso: 'ROLES:CAMBIAR',
    icono: icono(<><circle cx="9" cy="8" r="3.5" /><path d="M2 20c0-3.6 3.1-6 7-6s7 2.4 7 6" /><path d="M17 5l4 2v4c0 2.4-1.7 4-4 5-2.3-1-4-2.6-4-5V7z" /></>),
  },
  {
    ruta: '/reclamos',
    texto: 'Reclamos',
    titulo: 'Reclamos',
    descripcion: 'Registra un reclamo por un incidente laboral y consulta su estado, derivaciones y respuesta.',
    seccion: 'gestion',
    icono: icono(<><path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M12 8v4M12 15.5v.01" /></>),
  },
  {
    ruta: '/morosidad',
    texto: 'Morosidad',
    titulo: 'Índice de Morosidad',
    descripcion: 'Consulta el índice total, por tributo y por zona de cada periodo publicado para priorizar la gestión de cobranza.',
    seccion: 'gestion',
    permiso: 'INDICADORES:CONSULTAR',
    icono: icono(<><path d="M3 3v18h18" /><path d="M18 8l-6 6-3-3-5 5" /></>),
  },
]

export function rutaInicialGestion(sesion) {
  return MODULOS.find((modulo) => modulo.seccion === 'gestion' && sesion?.permisos?.includes(modulo.permiso))?.ruta
    || '/gestion/sin-acceso'
}
