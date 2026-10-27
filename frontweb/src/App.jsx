import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import MenuLateral from './components/MenuLateral.jsx'
import { MODULOS } from './utils/modulos.jsx'
import Inicio from './pages/Inicio.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Login from './pages/Login.jsx'
import ConsultaPredio from './pages/ConsultaPredio.jsx'
import ConsultaContribuyente from './pages/ConsultaContribuyente.jsx'
import ConfiguracionMetas from './pages/ConfiguracionMetas.jsx'
import Formularios from './pages/Formularios.jsx'
import IndiceMorosidad from './pages/IndiceMorosidad.jsx'
import Constancia from './pages/Constancia.jsx'
import EstadoCuentaPortal from './pages/EstadoCuentaPortal.jsx'

/**
 * Layout exclusivo para el Portal del Ciudadano (Servicios en Línea).
 * Solo muestra los módulos de la sección 'portal' en el menú lateral.
 * Cabecera: "Portal del Ciudadano · Servicios en Línea"
 */
function LayoutPortal() {
  const { pathname } = useLocation()
  const modulo = MODULOS.filter((m) => m.seccion === 'portal').find((m) => pathname.startsWith(m.ruta))
  return (
    <div className="app">
      <MenuLateral solo="portal" />
      <div className="contenido">
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
 * Cabecera: "Sistema de Gestión Tributaria"
 */
function LayoutGestion() {
  const { pathname } = useLocation()
  const modulo = MODULOS.filter((m) => m.seccion !== 'portal').find((m) => pathname.startsWith(m.ruta))
  return (
    <div className="app">
      <MenuLateral solo="gestion" />
      <div className="contenido">
        {modulo && (
          <header className="pagina-cab">
            <p className="pagina-cab__ruta">Sistema de Gestión Tributaria · {modulo.texto}</p>
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

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/login" element={<Login />} />

        {/* Rutas del portal del Contribuyente (HU014 / HU041) */}
        <Route element={<LayoutPortal />}>
          <Route path="/estado-cuenta" element={<EstadoCuentaPortal />} />
          <Route path="/portal/estado-cuenta" element={<Navigate to="/estado-cuenta" replace />} />
          <Route path="/mi-cuenta" element={<Navigate to="/estado-cuenta" replace />} />
          <Route path="/constancias" element={<Constancia />} />
        </Route>

        {/* Rutas de funcionarios (Gestión Municipal) */}
        <Route element={<LayoutGestion />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/predios" element={<ConsultaPredio />} />
          <Route path="/contribuyentes" element={<ConsultaContribuyente />} />
          <Route path="/metas" element={<ConfiguracionMetas />} />
          <Route path="/formularios" element={<Formularios />} />
          <Route path="/morosidad" element={<IndiceMorosidad />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
