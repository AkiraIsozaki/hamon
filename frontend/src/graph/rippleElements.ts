import type { ElementDefinition } from 'cytoscape'
import type { ImpactItem } from '../api/client'

/**
 * 影響候補の一覧を、Cytoscape に渡すノードと辺に変換する。
 * 辺は各候補の経路（path）の隣り合う 2 点から作る。ノードの確率は、そのノードを通る候補の最大値。
 */
export function rippleElements(items: ImpactItem[]): ElementDefinition[] {
  if (items.length === 0) return []
  const target = items[0].path[0]
  const probabilityOf = new Map<string, number>([[target, 1]])
  const edges = new Map<string, ElementDefinition>()
  for (const item of items) {
    item.path.forEach((node, i) => {
      if (i === 0) return
      probabilityOf.set(node, Math.max(probabilityOf.get(node) ?? 0, item.probability))
      const source = item.path[i - 1]
      edges.set(`${source}->${node}`, { data: { id: `${source}->${node}`, source, target: node } })
    })
  }
  const nodes = [...probabilityOf].map(([id, probability]) => ({
    data: { id, probability, label: shortName(id), isTarget: id === target },
  }))
  return [...nodes, ...edges.values()]
}

/** `com.example.OrderService#cancel` → `OrderService#cancel`、`web/order/cancel.html` → `cancel.html` */
function shortName(node: string): string {
  const [head, ...rest] = node.split('#')
  const separator = head.includes('/') ? '/' : '.'
  const last = head.split(separator).at(-1) ?? head
  return [last, ...rest].join('#')
}
