import cytoscape from 'cytoscape'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import './App.css'
import { api, type ImpactItem } from './api/client'
import { rippleElements } from './graph/rippleElements'

const graphStyle: cytoscape.StylesheetJson = [
  {
    selector: 'node',
    style: {
      label: 'data(label)',
      color: '#e8eaf0',
      'font-size': 11,
      'text-valign': 'bottom',
      'text-margin-y': 6,
      width: (node) => 16 + 40 * node.data('probability'),
      height: (node) => 16 + 40 * node.data('probability'),
      'background-color': 'mapData(probability, 0, 1, #35507a, #5ec8ff)',
      'background-opacity': (node) => 0.45 + 0.55 * node.data('probability'),
    },
  },
  { selector: 'node[?isTarget]', style: { 'background-color': '#ffb64d', shape: 'diamond' } },
  {
    selector: 'edge',
    style: {
      width: 1.5,
      'line-color': '#5a6b8c',
      'target-arrow-color': '#5a6b8c',
      'target-arrow-shape': 'triangle',
      'curve-style': 'bezier',
    },
  },
]

export default function App() {
  const [target, setTarget] = useState('com.example.order.OrderService#cancel')
  const [minProbability, setMinProbability] = useState(0.05)
  const [items, setItems] = useState<ImpactItem[]>([])
  const [error, setError] = useState<string>()
  const graphRef = useRef<HTMLDivElement>(null)

  async function survey(event?: FormEvent) {
    event?.preventDefault()
    const { data, error, response } = await api.GET('/impact', { params: { query: { target, minProbability } } })
    if (error !== undefined || data === undefined) {
      setError(`API エラー: HTTP ${response.status}`)
      return
    }
    setError(undefined)
    setItems(data)
  }

  useEffect(() => {
    if (!graphRef.current) return
    const cy = cytoscape({
      container: graphRef.current,
      elements: rippleElements(items),
      style: graphStyle,
      layout: { name: 'breadthfirst', directed: true, circle: true, spacingFactor: 1.2, roots: items[0] ? [items[0].path[0]] : [] },
    })
    return () => cy.destroy()
  }, [items])

  return (
    <div className="app">
      <aside className="panel">
        <h1>hamon</h1>
        <form onSubmit={survey}>
          <label>
            変更点
            <input value={target} onChange={(e) => setTarget(e.target.value)} />
          </label>
          <label>
            閾値（この確率以上を表示）: {minProbability.toFixed(2)}
            <input type="range" min={0} max={1} step={0.01} value={minProbability} onChange={(e) => setMinProbability(Number(e.target.value))} />
          </label>
          <button type="submit">影響を調べる</button>
        </form>
        {error && <p className="error">{error}</p>}
        <ol className="items">
          {items.map((item) => (
            <li key={item.node}>
              <span className="probability">{(item.probability * 100).toFixed(0)}%</span> {item.node}
            </li>
          ))}
        </ol>
      </aside>
      <main ref={graphRef} className="graph" />
    </div>
  )
}
