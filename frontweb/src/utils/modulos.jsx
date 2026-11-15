export const icono = (children) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

export const MODULOS = [
  {
    ruta: '/portal/inicio',
    texto: 'Inicio',
    titulo: 'Bienvenido a Servicios en Línea',
    descripcion: 'Accede a tus consultas y trámites municipales desde un solo lugar.',
    seccion: 'portal',
    icono: icono(<><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9M9 20v-6h6v6" /></>),
  },
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
    ruta: '/portal/tramites-predio',
    texto: 'Trámites de predio',
    titulo: 'Mis trámites de predio',
    descripcion: 'Consulta el estado de tus solicitudes de registro o traspaso de predio y la observación de la Municipalidad.',
    seccion: 'portal',
    icono: icono(<><path d="M3 21h18M5 21V8l7-5 7 5v13" /><path d="M9 13h6M9 17h6" /></>),
  },
  {
    ruta: '/portal/buzon',
    texto: 'Buzón',
    titulo: 'Buzón municipal',
    descripcion: 'Consulta, organiza y descarga las comunicaciones y documentos enviados por la Municipalidad.',
    seccion: 'portal',
    icono: icono(<><path d="M4 4h16v16H4z" /><path d="m4 7 8 6 8-6" /></>),
  },
  {
    ruta: '/gestion/inicio',
    texto: 'Inicio',
    titulo: 'Panel de inicio institucional',
    descripcion: 'Accede a los módulos y herramientas autorizadas según tu rol institucional.',
    seccion: 'gestion',
    icono: icono(<><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9M9 20v-6h6v6" /></>),
  },
  {
    ruta: '/segmentos',
    texto: 'Segmentos',
    titulo: 'Segmentación de contribuyentes',
    descripcion: 'Revisa las segmentaciones generadas por el modelo y publica la que apruebe la Gerencia de Rentas.',
    seccion: 'gestion',
    permiso: 'SEGMENTOS:CONSULTAR',
    icono: icono(
      <>
        <circle cx="6" cy="6" r="3" />
        <circle cx="18" cy="6" r="3" />
        <circle cx="12" cy="18" r="3" />
        <path d="M9 6h6M8.5 8.2 10.8 15M15.5 8.2 13.2 15" />
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
    ruta: '/configuracion/reportes',
    texto: 'Acceso a reportes',
    titulo: 'Acceso y alcance de reportes',
    descripcion: 'Define por rol qué reportes puede consultar cada usuario, con qué zonas, segmentos y columnas.',
    seccion: 'gestion',
    permiso: 'USUARIOS:LISTAR',
    icono: icono(<><path d="M12 2 20 6v6c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6z" /><path d="m9 12 2 2 4-4" /></>),
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
    descripcion: 'Busca por código o documento y revisa la ficha, el estado de cuenta, los pagos y beneficios del contribuyente.',
    seccion: 'gestion',
    permiso: 'CONTRIBUYENTES:CONSULTAR',
    icono: icono(<><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></>),
  },
  {
    ruta: '/ventanilla/solicitudes-predio',
    texto: 'Solicitudes de predio',
    titulo: 'Solicitudes de registro y traspaso de predio',
    descripcion: 'Revisa los documentos enviados por los contribuyentes y resuelve sus solicitudes.',
    seccion: 'gestion',
    permiso: 'SOLICITUDES_PREDIO:REVISAR',
    icono: icono(<><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 15l2 2 5-5" /></>),
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
    ruta: '/configuracion/recordatorios',
    texto: 'Recordatorios',
    titulo: 'Recordatorios de vencimiento',
    descripcion: 'Configura con cuántos días de anticipación se envía el recordatorio por correo antes del vencimiento de una obligación.',
    seccion: 'gestion',
    roles: ['ADMINISTRADOR'],
    icono: icono(<><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" /></>),
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
    ruta: '/gestion/constancias',
    texto: 'Constancias de No Adeudo',
    titulo: 'Constancias de No Adeudo',
    descripcion: 'Atiende las solicitudes en línea y en ventanilla, verifica la deuda, emite las constancias y consulta su historial.',
    seccion: 'gestion',
    permiso: 'CONSTANCIAS:GESTIONAR',
    icono: icono(<><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 15l2 2 5-5" /></>),
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
    ruta: '/reporteria',
    texto: 'Plataforma analítica',
    titulo: 'Plataforma analítica de Rentas',
    descripcion: 'Recaudación, morosidad, principales contribuyentes, buenos pagadores, índice de morosidad, resumen ejecutivo con IA y listados exportables a la fecha de corte.',
    seccion: 'gestion',
    permiso: 'REPORTES:CONSULTAR',
    icono: icono(<><path d="M3 3v18h18" /><path d="M7 16v-4M12 16V8M17 16v-7" /></>),
  },
  {
    ruta: '/calidad-datos',
    texto: 'Calidad de datos',
    titulo: 'Calidad de datos y cargas del ETL',
    descripcion: 'Audita las cargas del ETL y sus reglas de calidad, ejecuta una carga a demanda y resuelve la bandeja de homologación.',
    seccion: 'gestion',
    permiso: 'ETL:GESTIONAR',
    icono: icono(<><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" /></>),
  },
  {
    ruta: '/alertas-mora',
    texto: 'Alertas de mora',
    titulo: 'Alertas de mora temprana',
    descripcion: 'Envía por correo el aviso de mora a los contribuyentes con cuotas vencidas y consulta el historial de avisos por lote o contribuyente.',
    seccion: 'gestion',
    permiso: 'ALERTAS:CONSULTAR',
    icono: icono(<><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" /></>),
  },
]

export function rutaInicialGestion(sesion) {
  if (!sesion) return '/gestion/login'
  return '/gestion/inicio'
}
