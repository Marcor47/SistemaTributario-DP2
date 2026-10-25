import { BrowserRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import MenuLateral from './components/MenuLateral.jsx'
import { MODULOS } from './utils/modulos.jsx'
import Inicio from './pages/Inicio.jsx'
import Login from './pages/Login.jsx'
import ConsultaPredio from './pages/ConsultaPredio.jsx'
import ConsultaContribuyente from './pages/ConsultaContribuyente.jsx'
import ConfiguracionMetas from './pages/ConfiguracionMetas.jsx'
import Formularios from './pages/Formularios.jsx'
import IndiceMorosidad from './pages/IndiceMorosidad.jsx'

function LayoutInterno() {
  const { pathname } = useLocation()
  const modulo = MODULOS.find((m) => pathname.startsWith(m.ruta))
  return (
    <div className="app">
      <MenuLateral />
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
        <Route element={<LayoutInterno />}>
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
