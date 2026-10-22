import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import Cabecera from './components/Cabecera.jsx'
import ConsultaPredio from './pages/ConsultaPredio.jsx'
import ConsultaContribuyente from './pages/ConsultaContribuyente.jsx'
import ConfiguracionMetas from './pages/ConfiguracionMetas.jsx'
import Formularios from './pages/Formularios.jsx'

export default function App() {
  return (
    <BrowserRouter>
      <Cabecera />
      <main className="pmain">
        <Routes>
          <Route path="/" element={<Navigate to="/predios" replace />} />
          <Route path="/predios" element={<ConsultaPredio />} />
          <Route path="/contribuyentes" element={<ConsultaContribuyente />} />
          <Route path="/metas" element={<ConfiguracionMetas />} />
          <Route path="/formularios" element={<Formularios />} />
        </Routes>
      </main>
      <footer className="pie">
        Todos los nombres, códigos y montos son <strong>ficticios</strong>, generados para la demostración; no
        representan a contribuyentes reales.
      </footer>
    </BrowserRouter>
  )
}
