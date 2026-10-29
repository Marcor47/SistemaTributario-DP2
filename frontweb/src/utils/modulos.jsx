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
    ruta: '/portal/reclamos',
    texto: 'Mis reclamos',
    titulo: 'Reclamos y seguimiento',
    descripcion: 'Registra un reclamo sobre la atención o los servicios municipales y consulta su respuesta.',
    seccion: 'portal',
    icono: icono(<><path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M12 8v4M12 15.5v.01" /></>),
  },
  {
    ruta: '/dashboard',
    texto: 'Dashboard',
    titulo: 'Dashboard de Rentas',
    descripcion: 'Revisa las alertas activas por desvío de meta y apoya la toma de decisiones de la Gerencia de Rentas.',
    seccion: 'gestion',
    permiso: 'ALERTAS:CONSULTAR',
    icono: icono(
      <>
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </>
    ),
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
    ruta: '/gestion/reclamos',
    texto: 'Gestión de reclamos',
    titulo: 'Gestión de reclamos',
    descripcion: 'Revisa los reclamos recibidos y derívalos al área y al responsable correspondiente.',
    seccion: 'gestion',
    permiso: 'RECLAMOS:GESTIONAR',
    icono: icono(<><path d="M4 4h16v12H7l-3 3z" /><path d="M9 8h6M9 12h4" /></>),
  },
  {
    ruta: '/reclamos',
    texto: 'Mis reclamos',
    titulo: 'Mis reclamos laborales',
    descripcion: 'Registra un reclamo por un incidente laboral y consulta su estado, derivaciones y respuesta.',
    seccion: 'gestion',
    permiso: 'RECLAMOS:CREAR',
    icono: icono(<><path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /><path d="M12 8v4M12 15.5v.01" /></>),
  },
  {
    ruta: '/reclamos/asignados',
    texto: 'Reclamos asignados',
    titulo: 'Reclamos asignados',
    descripcion: 'Atiende los reclamos derivados a tu cargo y registra la respuesta para el reclamante.',
    seccion: 'gestion',
    permiso: 'RECLAMOS:ATENDER',
    icono: icono(<><path d="M9 11l3 3L22 4" /><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" /></>),
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
