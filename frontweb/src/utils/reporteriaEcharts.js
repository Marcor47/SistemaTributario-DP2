import { BarChart, HeatmapChart, LineChart, PieChart, ScatterChart } from 'echarts/charts'
import { AriaComponent, DataZoomComponent, GridComponent, LegendComponent, TitleComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart, LineChart, PieChart, HeatmapChart, ScatterChart,
  AriaComponent, DataZoomComponent, GridComponent, LegendComponent, TitleComponent, TooltipComponent, VisualMapComponent,
  CanvasRenderer,
])

export default echarts
