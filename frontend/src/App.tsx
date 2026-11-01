import cytoscape from 'cytoscape'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import './App.css'
import { api, type ImpactItem, type NodeKind, type NodeSummary } from './api/client'
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
  // 種類ごとの形。表は樽（DB の筒）、列はタグ、ファイルは四角、設定キーは角丸。メソッドは既定の丸
  { selector: 'node[kind = "TABLE"]', style: { shape: 'barrel' } },
  { selector: 'node[kind = "COLUMN"]', style: { shape: 'round-tag' } },
  { selector: 'node[kind = "FILE"]', style: { shape: 'rectangle' } },
  { selector: 'node[kind = "CONFIG"]', style: { shape: 'round-rectangle' } },
  { selector: 'node[kind = "CLASS"]', style: { shape: 'hexagon' } },
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

const kindLabel: Record<NodeKind, string> = {
  METHOD: 'メソッド',
  CLASS: 'クラス',
  FILE: 'ファイル',
  TABLE: '表',
  COLUMN: '列',
  CONFIG: '設定',
}

export default function App() {
  const [target, setTarget] = useState('io.github.akiraisozaki.hamon.impact.ImpactRow#toItem')
  const [minProbability, setMinProbability] = useState(0.05)
  const [items, setItems] = useState<ImpactItem[]>([])
  const [error, setError] = useState<string>()
  const [analysis, setAnalysis] = useState<string>()
  const [suggestions, setSuggestions] = useState<NodeSummary[]>([])
  const graphRef = useRef<HTMLDivElement>(null)

  /** 変更点の入力に合わせてノード名の候補を引く。2 文字未満では引かない */
  async function suggest(q: string) {
    setTarget(q)
    if (q.length < 2) {
      setSuggestions([])
      return
    }
    const { data } = await api.GET('/nodes', { params: { query: { q } } })
    setSuggestions(data ?? [])
  }

  async function analyze() {
    setAnalysis('解析中…')
    const { data, error, response } = await api.POST('/analysis')
    if (error !== undefined || data === undefined) {
      setAnalysis(`解析エラー: HTTP ${response.status}`)
      return
    }
    setAnalysis(`${data.nodeCount} ノード / ${data.edgeCount} 辺`)
  }

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
        <p className="analysis">
          <button type="button" onClick={analyze}>ソースを解析してグラフを作り直す</button>
          {analysis && <span>{analysis}</span>}
        </p>
        <form onSubmit={survey}>
          <label>
            変更点（名前の一部を打つと候補が出る）
            <input value={target} list="node-suggestions" onChange={(e) => suggest(e.target.value)} />
            <datalist id="node-suggestions">
              {suggestions.map((node) => (
                <option key={node.name} value={node.name}>{kindLabel[node.kind]}</option>
              ))}
            </datalist>
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
              <span className="probability">{(item.probability * 100).toFixed(0)}%</span>
              <span className="kind">{kindLabel[item.kind]}</span> {item.node}
            </li>
          ))}
        </ol>
      </aside>
      <main ref={graphRef} className="graph" />
    </div>
  )
}
