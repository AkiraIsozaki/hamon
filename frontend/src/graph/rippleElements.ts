import type { ElementDefinition } from 'cytoscape'
import type { ImpactItem, NodeKind } from '../api/client'

/**
 * 影響候補の一覧を、Cytoscape に渡すノードと辺に変換する。
 * 辺は各候補の経路（path）の隣り合う 2 点から作る。ノードの確率は、そのノードを通る候補の最大値。
 * 種類（kind）は候補として返ってきたノードにだけ付く。変更点は候補ではないので付かない
 */
export function rippleElements(items: ImpactItem[]): ElementDefinition[] {
  if (items.length === 0) return []
  const target = items[0].path[0]
  const probabilityOf = new Map<string, number>([[target, 1]])
  const kindOf = new Map<string, NodeKind>(items.map((item) => [item.node, item.kind]))
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
    data: { id, probability, kind: kindOf.get(id), label: shortName(id), isTarget: id === target },
  }))
  return [...nodes, ...edges.values()]
}

/**
 * メソッド `com.example.OrderService#cancel` → `OrderService#cancel`、ファイル `web/order/cancel.html` → `cancel.html`。
 * `#` も `/` も無い名前（表 `ORDERS`、列 `ORDERS.STATUS`、設定キー `batch.order-cancel.cron`）はそのまま
 */
export function shortName(node: string): string {
  if (node.includes('#')) {
    const [type, ...rest] = node.split('#')
    return [type.split('.').at(-1) ?? type, ...rest].join('#')
  }
  if (node.includes('/')) return node.split('/').at(-1) ?? node
  return node
}
