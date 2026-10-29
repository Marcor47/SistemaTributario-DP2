import Svg, { Circle, Path, Rect } from 'react-native-svg'
import { colores, tamanos } from '../../theme/tokens'

// Íconos de trazo (estilo Lucide, trazo 2) dibujados en SVG, igual que los íconos en línea de la web.
// Cada figura es un path (string), un círculo { c: [cx, cy, r] } o un rectángulo { r: [x, y, w, h, rx] }.
const FIGURAS = {
  'flecha-izquierda': ['M19 12H5', 'M12 19l-7-7 7-7'],
  'chevron-derecha': ['m9 18 6-6-6-6'],
  ojo: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z', { c: [12, 12, 3] }],
  'ojo-tachado': ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z', { c: [12, 12, 3] }, 'M4 4l16 16'],
  alerta: [{ c: [12, 12, 9] }, 'M12 7.5v5.5', 'M12 16.5v.1'],
  info: [{ c: [12, 12, 9] }, 'M12 16v-4.5', 'M12 8h.01'],
  'check-circulo': [{ c: [12, 12, 9] }, 'm8.5 12 2.5 2.5 4.5-5'],
  check: ['M20 6 9 17l-5-5'],
  casa: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5', 'M10 21v-6h4v6'],
  traspaso: ['M17 2l4 4-4 4', 'M3 11v-1a4 4 0 0 1 4-4h14', 'M7 22l-4-4 4-4', 'M21 13v1a4 4 0 0 1-4 4H3'],
  edificio: [{ r: [4, 2, 16, 20, 2] }, 'M9 22v-4h6v4', 'M8 6h.01', 'M12 6h.01', 'M16 6h.01', 'M8 10h.01',
    'M12 10h.01', 'M16 10h.01', 'M8 14h.01', 'M12 14h.01', 'M16 14h.01'],
  brote: ['M7 20h10', 'M10 20c5.5-2.5.8-6.4 3-10',
    'M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z',
    'M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z'],
  cuadricula: [{ r: [3, 3, 7, 7, 1] }, { r: [14, 3, 7, 7, 1] }, { r: [14, 14, 7, 7, 1] }, { r: [3, 14, 7, 7, 1] }],
  clip: ['m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48'],
  camara: ['M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z',
    { c: [12, 13, 3] }],
  imagen: [{ r: [3, 3, 18, 18, 2] }, { c: [9, 9, 2] }, 'm21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21'],
  documento: ['M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z', 'M14 2v4a2 2 0 0 0 2 2h4',
    'M10 9H8', 'M16 13H8', 'M16 17H8'],
  'documento-mas': ['M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z', 'M14 2v4a2 2 0 0 0 2 2h4',
    'M12 18v-6', 'M9 15h6'],
  cerrar: ['M18 6 6 18', 'm6 6 12 12'],
  salir: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  reintentar: ['M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8', 'M21 3v5h-5',
    'M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16', 'M8 16H3v5'],
  lapiz: ['M21.17 6.81a1 1 0 0 0-3.98-3.98L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z'],
  reloj: [{ c: [12, 12, 9] }, 'M12 7v5l3 2'],
  usuario: ['M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2', { c: [12, 7, 4] }],
  candado: [{ r: [3, 11, 18, 11, 2] }, 'M7 11V7a5 5 0 0 1 10 0v4'],
}

/** Ícono decorativo: quien lo use pone el texto accesible en el control que lo contiene. */
export default function Icono({ nombre, tamano = tamanos.icono, color = colores.marinoOsc, trazo = 2 }) {
  const figuras = FIGURAS[nombre] || []
  return (
    <Svg
      width={tamano}
      height={tamano}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={trazo}
      strokeLinecap="round"
      strokeLinejoin="round"
      accessible={false}
      importantForAccessibility="no-hide-descendants"
    >
      {figuras.map((f, i) => {
        if (typeof f === 'string') return <Path key={i} d={f} />
        if (f.c) return <Circle key={i} cx={f.c[0]} cy={f.c[1]} r={f.c[2]} />
        return <Rect key={i} x={f.r[0]} y={f.r[1]} width={f.r[2]} height={f.r[3]} rx={f.r[4]} />
      })}
    </Svg>
  )
}
