import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import EstadoCuentaView from './pages/EstadoCuentaView';

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-container">
        <Navbar />
        <Routes>
          <Route path="/estado-cuenta" element={<EstadoCuentaView />} />
          <Route path="/" element={<Navigate to="/estado-cuenta" replace />} />
          <Route path="*" element={<Navigate to="/estado-cuenta" replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}