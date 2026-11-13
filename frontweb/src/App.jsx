import { lazy, Suspense, useState, useCallback } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import MenuLateral from './components/MenuLateral.jsx'
import BarraSuperior from './components/BarraSuperior.jsx'
import { MODULOS } from './utils/modulos.jsx'
import Inicio from './pages/Inicio.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Segmentos from './pages/Segmentos.jsx'
import Login from './pages/Login.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { RutaContribuyente } from './components/RutaProtegida.jsx'
import ConsultaPredio from './pages/ConsultaPredio.jsx'
import ConsultaContribuyente from './pages/ConsultaContribuyente.jsx'
import ConfiguracionMetas from './pages/ConfiguracionMetas.jsx'
import ConfiguracionRecordatorios from './pages/ConfiguracionRecordatorios.jsx'
import ConfiguracionAccesoReportes from './pages/ConfiguracionAccesoReportes.jsx'
import Formularios from './pages/Formularios.jsx'
import IndiceMorosidad from './pages/IndiceMorosidad.jsx'
import Reclamos from './pages/Reclamos.jsx'
import ResumenEjecutivo from './pages/ResumenEjecutivo.jsx'
import GestionReclamos from './pages/GestionReclamos.jsx'
import Reportes from './pages/Reportes.jsx'
import SolicitudesReportes from './pages/SolicitudesReportes.jsx'
import Constancia from './pages/Constancia.jsx'
import ConstanciaVentanilla from './pages/ConstanciaVentanilla.jsx'
import SolicitudesPredio from './pages/SolicitudesPredio.jsx'
import EstadoCuentaPortal from './pages/EstadoCuentaPortal.jsx'
import InicioPortal from './pages/InicioPortal.jsx'
import InicioGestion from './pages/InicioGestion.jsx'
import PasarelaSimulada from './pages/PasarelaSimulada.jsx'
import LoginInterno from './pages/LoginInterno.jsx'
import CambiarClaveInterna from './pages/CambiarClaveInterna.jsx'
import GestionUsuarios from './pages/GestionUsuarios.jsx'
import AlertasMora from './pages/AlertasMora.jsx'
import SinAcceso from './pages/SinAcceso.jsx'
import Buzon from './pages/Buzon.jsx'
import RutaInternaProtegida from './components/RutaInternaProtegida.jsx'
import { SesionInternaProvider } from './context/SesionInternaContext.jsx'

const Reporteria = lazy(() => import('./pages/Reporteria.jsx'))

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
          Todos los nombres, códigos y montos son <strong>ficticios</strong>, generados para la demostración; no
          representan a contribuyentes reales.
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
  const esDashboard = pathname.startsWith('/dashboard')
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
        <main className={`pmain${esDashboard ? ' pmain--dashboard' : ''}${esMetas ? ' pmain--metas' : ''}${esReportes ? ' pmain--reportes' : ''}`}>
          <Outlet />
        </main>
        <footer className="pie">
          Todos los nombres, códigos y montos son <strong>ficticios</strong>, generados para la demostración; no
          representan a contribuyentes reales.
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
          <Routes>
            <Route path="/" element={<Inicio />} />
            <Route path="/login" element={<Login />} />
            <Route path="/gestion/login" element={<LoginInterno />} />
            <Route path="/interno/login" element={<Navigate to="/gestion/login" replace />} />
            <Route path="/gestion/cambiar-clave" element={<RutaInternaProtegida permitirCambioClave><CambiarClaveInterna /></RutaInternaProtegida>} />
            <Route path="/pasarela-simulada/:idOrden" element={<PasarelaSimulada />} />
            <Route path="/pagos/:id" element={<PasarelaSimulada />} />

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
            </Route>
          </Route>

        {/* Rutas de funcionarios (Gestión Municipal) */}
        <Route element={<RutaInternaProtegida><LayoutGestion /></RutaInternaProtegida>}>
          <Route path="/gestion/inicio" element={<InicioGestion />} />
          <Route path="/gestion" element={<Navigate to="/gestion/inicio" replace />} />
          <Route path="/dashboard" element={<RutaInternaProtegida permiso="DASHBOARD:CONSULTAR"><Dashboard /></RutaInternaProtegida>} />
          <Route path="/segmentos" element={<RutaInternaProtegida permiso="SEGMENTOS:CONSULTAR"><Segmentos /></RutaInternaProtegida>} />
          <Route path="/administracion/usuarios" element={<RutaInternaProtegida permiso="USUARIOS:LISTAR"><GestionUsuarios /></RutaInternaProtegida>} />
          <Route path="/usuarios" element={<Navigate to="/administracion/usuarios" replace />} />
          <Route path="/configuracion/reportes" element={<RutaInternaProtegida permiso="USUARIOS:LISTAR"><ConfiguracionAccesoReportes /></RutaInternaProtegida>} />
          <Route path="/predios" element={<RutaInternaProtegida permiso="PREDIOS:CONSULTAR"><ConsultaPredio /></RutaInternaProtegida>} />
          <Route path="/contribuyentes" element={<RutaInternaProtegida permiso="CONTRIBUYENTES:CONSULTAR"><ConsultaContribuyente /></RutaInternaProtegida>} />
          <Route path="/ventanilla/constancias" element={<RutaInternaProtegida permiso="CONSTANCIAS:GESTIONAR"><ConstanciaVentanilla /></RutaInternaProtegida>} />
          <Route path="/ventanilla/solicitudes-predio/:idSolicitud?" element={<RutaInternaProtegida permiso="SOLICITUDES_PREDIO:REVISAR"><SolicitudesPredio /></RutaInternaProtegida>} />
          <Route path="/metas" element={<RutaInternaProtegida permiso="METAS:GESTIONAR"><ConfiguracionMetas /></RutaInternaProtegida>} />
          <Route path="/configuracion/recordatorios" element={<RutaInternaProtegida roles={['ADMINISTRADOR']}><ConfiguracionRecordatorios /></RutaInternaProtegida>} />
          <Route path="/formularios" element={<RutaInternaProtegida permiso="FORMULARIOS:CONSULTAR"><Formularios /></RutaInternaProtegida>} />
          <Route path="/morosidad" element={<RutaInternaProtegida permiso="INDICADORES:CONSULTAR"><IndiceMorosidad /></RutaInternaProtegida>} />
          <Route path="/resumen-ejecutivo" element={<RutaInternaProtegida permiso="INDICADORES:CONSULTAR"><ResumenEjecutivo /></RutaInternaProtegida>} />
          <Route path="/reportes" element={<RutaInternaProtegida permiso="REPORTES:CONSULTAR"><Reportes /></RutaInternaProtegida>} />
          <Route path="/reportes/solicitudes" element={<RutaInternaProtegida permiso="REPORTES:SOLICITUDES"><SolicitudesReportes /></RutaInternaProtegida>} />
          <Route path="/reporteria" element={<RutaInternaProtegida permiso="REPORTES:CONSULTAR"><Suspense fallback={<p className="mensaje-carga" role="status">Cargando la plataforma analítica…</p>}><Reporteria /></Suspense></RutaInternaProtegida>} />
          <Route path="/alertas-mora" element={<RutaInternaProtegida permiso="ALERTAS:CONSULTAR"><AlertasMora /></RutaInternaProtegida>} />
          <Route path="/reclamos" element={<RutaInternaProtegida permiso="RECLAMOS:CREAR"><Reclamos reclamante="TRABAJADOR" /></RutaInternaProtegida>} />
          <Route path="/gestion/reclamos" element={<RutaInternaProtegida permiso="RECLAMOS:GESTIONAR"><GestionReclamos modo="gestion" /></RutaInternaProtegida>} />
          <Route path="/reclamos/asignados" element={<RutaInternaProtegida permiso="RECLAMOS:ATENDER"><GestionReclamos modo="asignados" /></RutaInternaProtegida>} />
          <Route path="/gestion/sin-acceso" element={<SinAcceso />} />
        </Route>
          </Routes>
        </SesionInternaProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
