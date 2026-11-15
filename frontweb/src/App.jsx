import { lazy, Suspense, useState, useCallback } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import MenuLateral from './components/MenuLateral.jsx'
import BarraSuperior from './components/BarraSuperior.jsx'
import { MODULOS } from './utils/modulos.jsx'
import Inicio from './pages/Inicio.jsx'
import Login from './pages/Login.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { RutaContribuyente } from './components/RutaProtegida.jsx'
import LoginInterno from './pages/LoginInterno.jsx'
import RutaInternaProtegida from './components/RutaInternaProtegida.jsx'
import { SesionInternaProvider } from './context/SesionInternaContext.jsx'

// Cada pantalla se descarga al abrirla: el inicio de sesión no carga el código de todo el sistema.
const Segmentos = lazy(() => import('./pages/Segmentos.jsx'))
const ConsultaPredio = lazy(() => import('./pages/ConsultaPredio.jsx'))
const ConsultaContribuyente = lazy(() => import('./pages/ConsultaContribuyente.jsx'))
const ConfiguracionMetas = lazy(() => import('./pages/ConfiguracionMetas.jsx'))
const ConfiguracionRecordatorios = lazy(() => import('./pages/ConfiguracionRecordatorios.jsx'))
const ConfiguracionAccesoReportes = lazy(() => import('./pages/ConfiguracionAccesoReportes.jsx'))
const Formularios = lazy(() => import('./pages/Formularios.jsx'))
const Reclamos = lazy(() => import('./pages/Reclamos.jsx'))
const GestionReclamos = lazy(() => import('./pages/GestionReclamos.jsx'))
const Constancia = lazy(() => import('./pages/Constancia.jsx'))
const SolicitudesPredio = lazy(() => import('./pages/SolicitudesPredio.jsx'))
const TramitesPredio = lazy(() => import('./pages/TramitesPredio.jsx'))
const EstadoCuentaPortal = lazy(() => import('./pages/EstadoCuentaPortal.jsx'))
const InicioPortal = lazy(() => import('./pages/InicioPortal.jsx'))
const InicioGestion = lazy(() => import('./pages/InicioGestion.jsx'))
const PasarelaPago = lazy(() => import('./pages/PasarelaPago.jsx'))
const CambiarClaveInterna = lazy(() => import('./pages/CambiarClaveInterna.jsx'))
const GestionUsuarios = lazy(() => import('./pages/GestionUsuarios.jsx'))
const AlertasMora = lazy(() => import('./pages/AlertasMora.jsx'))
const SinAcceso = lazy(() => import('./pages/SinAcceso.jsx'))
const Buzon = lazy(() => import('./pages/Buzon.jsx'))
const Reporteria = lazy(() => import('./pages/Reporteria.jsx'))
const ConstanciasNoAdeudo = lazy(() => import('./pages/ConstanciasNoAdeudo.jsx'))
const CalidadDatos = lazy(() => import('./pages/CalidadDatos.jsx'))
const cargando = <p className="mensaje-carga" role="status">Cargando…</p>

const moduloDeRuta = (pathname, seccion) => MODULOS
  .filter((modulo) => modulo.seccion === seccion && pathname.startsWith(modulo.ruta))
  .sort((a, b) => b.ruta.length - a.ruta.length)[0]

/**
 * Layout exclusivo para el Portal del Ciudadano (Servicios en Línea).
 * Solo muestra los módulos de la sección 'portal' en el menú lateral.
 */
function LayoutPortal() {
  const { pathname } = useLocation()
  const modulo = moduloDeRuta(pathname, 'portal')
  const [menuVisible, setMenuVisible] = useState(true)
  const toggleMenu = useCallback(() => setMenuVisible((v) => !v), [])

  return (
    <div className={`app${menuVisible ? '' : ' app--sin-menu'}`}>
      <MenuLateral solo="portal" visible={menuVisible} onToggle={toggleMenu} />
      <div className="contenido">
        <BarraSuperior
          seccion="portal"
        />
        {modulo && (
          <header className="pagina-cab">
            <p className="pagina-cab__ruta">Portal del Ciudadano · Servicios en Línea · {modulo.texto}</p>
            <h1>{modulo.titulo}</h1>
            <p className="pagina-cab__desc">{modulo.descripcion}</p>
          </header>
        )}
        <main className="pmain">
          <Outlet />
        </main>
        <footer className="pie">
          © {new Date().getFullYear()} Municipalidad Distrital de San Miguel · Gerencia de Rentas · Av. Federico Gallese 370, San Miguel, Lima
        </footer>
      </div>
    </div>
  )
}

/**
 * Layout para los módulos internos de funcionarios (Gestión Municipal).
 * Solo muestra los módulos de la sección 'gestion' en el menú lateral.
 */
function LayoutGestion() {
  const { pathname } = useLocation()
  const modulo = moduloDeRuta(pathname, 'gestion')
  const esMetas = pathname.startsWith('/metas')
  const esReportes = pathname.startsWith('/reportes') || pathname.startsWith('/reporteria')
  const [menuVisible, setMenuVisible] = useState(true)
  const toggleMenu = useCallback(() => setMenuVisible((v) => !v), [])

  return (
    <div className={`app${menuVisible ? '' : ' app--sin-menu'}`}>
      <MenuLateral solo="gestion" visible={menuVisible} onToggle={toggleMenu} />
      <div className="contenido">
        <BarraSuperior
          seccion="gestion"
        />
        {modulo && (
          <header className="pagina-cab">
            <p className="pagina-cab__ruta">Sistema de Gestión Tributaria · {modulo.texto}</p>
            <h1>{modulo.titulo}</h1>
            <p className="pagina-cab__desc">{modulo.descripcion}</p>
          </header>
        )}
        <main className={`pmain${esMetas ? ' pmain--metas' : ''}${esReportes ? ' pmain--reportes' : ''}`}>
          <Outlet />
        </main>
        <footer className="pie">
          © {new Date().getFullYear()} Municipalidad Distrital de San Miguel · Gerencia de Rentas · Av. Federico Gallese 370, San Miguel, Lima
        </footer>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <SesionInternaProvider>
          <Suspense fallback={cargando}>
          <Routes>
            <Route path="/" element={<Inicio />} />
            <Route path="/login" element={<Login />} />
            <Route path="/gestion/login" element={<LoginInterno />} />
            <Route path="/interno/login" element={<Navigate to="/gestion/login" replace />} />
            <Route path="/gestion/cambiar-clave" element={<RutaInternaProtegida permitirCambioClave><CambiarClaveInterna /></RutaInternaProtegida>} />
            <Route path="/pasarela/:idOrden" element={<PasarelaPago />} />
            <Route path="/pagos/:id" element={<PasarelaPago />} />

          {/* Rutas del portal del Contribuyente (HU014 / HU041) */}
          <Route element={<RutaContribuyente />}>
            <Route element={<LayoutPortal />}>
              <Route path="/portal/inicio" element={<InicioPortal />} />
              <Route path="/portal" element={<Navigate to="/portal/inicio" replace />} />
              <Route path="/estado-cuenta" element={<EstadoCuentaPortal />} />
              <Route path="/portal/estado-cuenta" element={<Navigate to="/estado-cuenta" replace />} />
              <Route path="/mi-cuenta" element={<Navigate to="/estado-cuenta" replace />} />
              <Route path="/constancias" element={<Constancia />} />
              <Route path="/portal/reclamos" element={<Reclamos reclamante="CONTRIBUYENTE" />} />
              <Route path="/portal/buzon" element={<Buzon />} />
              <Route path="/portal/tramites-predio" element={<TramitesPredio />} />
            </Route>
          </Route>

        {/* Rutas de funcionarios (Gestión Municipal) */}
        <Route element={<RutaInternaProtegida><LayoutGestion /></RutaInternaProtegida>}>
          <Route path="/gestion/inicio" element={<InicioGestion />} />
          <Route path="/gestion" element={<Navigate to="/gestion/inicio" replace />} />
          {/* Las vistas de análisis viven en la Plataforma analítica; las rutas anteriores llevan a su subpestaña. */}
          <Route path="/dashboard" element={<Navigate to="/reporteria" replace />} />
          <Route path="/morosidad" element={<Navigate to="/reporteria?pestana=morosidad" replace />} />
          <Route path="/resumen-ejecutivo" element={<Navigate to="/reporteria?pestana=resumen-ia" replace />} />
          <Route path="/reportes" element={<Navigate to="/reporteria?pestana=listados" replace />} />
          <Route path="/reportes/solicitudes" element={<Navigate to="/reporteria?pestana=uso" replace />} />
          <Route path="/segmentos" element={<RutaInternaProtegida permiso="SEGMENTOS:CONSULTAR"><Segmentos /></RutaInternaProtegida>} />
          <Route path="/administracion/usuarios" element={<RutaInternaProtegida permiso="USUARIOS:LISTAR"><GestionUsuarios /></RutaInternaProtegida>} />
          <Route path="/usuarios" element={<Navigate to="/administracion/usuarios" replace />} />
          <Route path="/configuracion/reportes" element={<RutaInternaProtegida permiso="USUARIOS:LISTAR"><ConfiguracionAccesoReportes /></RutaInternaProtegida>} />
          <Route path="/predios" element={<RutaInternaProtegida permiso="PREDIOS:CONSULTAR"><ConsultaPredio /></RutaInternaProtegida>} />
          <Route path="/contribuyentes" element={<RutaInternaProtegida permiso="CONTRIBUYENTES:CONSULTAR"><ConsultaContribuyente /></RutaInternaProtegida>} />
          <Route path="/ventanilla/constancias" element={<Navigate to="/gestion/constancias?pestana=ventanilla" replace />} />
          <Route path="/ventanilla/solicitudes-predio/:idSolicitud?" element={<RutaInternaProtegida permiso="SOLICITUDES_PREDIO:REVISAR"><SolicitudesPredio /></RutaInternaProtegida>} />
          <Route path="/metas" element={<RutaInternaProtegida permiso="METAS:GESTIONAR"><ConfiguracionMetas /></RutaInternaProtegida>} />
          <Route path="/configuracion/recordatorios" element={<RutaInternaProtegida roles={['ADMINISTRADOR']}><ConfiguracionRecordatorios /></RutaInternaProtegida>} />
          <Route path="/formularios" element={<RutaInternaProtegida permiso="FORMULARIOS:CONSULTAR"><Formularios /></RutaInternaProtegida>} />
          <Route path="/reporteria" element={<RutaInternaProtegida permiso="REPORTES:CONSULTAR"><Suspense fallback={<p className="mensaje-carga" role="status">Cargando la plataforma analítica…</p>}><Reporteria /></Suspense></RutaInternaProtegida>} />
          <Route path="/calidad-datos" element={<RutaInternaProtegida permiso="ETL:GESTIONAR"><CalidadDatos /></RutaInternaProtegida>} />
          <Route path="/alertas-mora" element={<RutaInternaProtegida permiso="ALERTAS:CONSULTAR"><AlertasMora /></RutaInternaProtegida>} />
          {/* Los reclamos solo los registra el contribuyente desde Servicios en Línea. */}
          <Route path="/reclamos" element={<Navigate to="/gestion/inicio" replace />} />
          <Route path="/gestion/reclamos" element={<RutaInternaProtegida permiso="RECLAMOS:GESTIONAR"><GestionReclamos modo="gestion" /></RutaInternaProtegida>} />
          <Route path="/gestion/constancias" element={<RutaInternaProtegida permiso="CONSTANCIAS:GESTIONAR"><Suspense fallback={cargando}><ConstanciasNoAdeudo /></Suspense></RutaInternaProtegida>} />
          <Route path="/reclamos/asignados" element={<RutaInternaProtegida permiso="RECLAMOS:ATENDER"><GestionReclamos modo="asignados" /></RutaInternaProtegida>} />
          <Route path="/gestion/sin-acceso" element={<SinAcceso />} />
        </Route>
          </Routes>
          </Suspense>
        </SesionInternaProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
