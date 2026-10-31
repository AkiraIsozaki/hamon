import { describe, expect, it } from 'vitest'
import type { ImpactItem } from '../api/client'
import { rippleElements } from './rippleElements'

function item(probability: number, ...path: string[]): ImpactItem {
  return { node: path[path.length - 1], probability, path }
}

describe('影響候補からグラフの要素を作る', () => {
  it('経路の隣り合うノードが辺になり、重複する辺は 1 本にまとまる', () => {
    const elements = rippleElements([item(0.9, 'T', 'A'), item(0.5, 'T', 'A', 'B')])

    const edgeIds = elements.filter((e) => 'source' in e.data).map((e) => e.data.id)
    expect(edgeIds).toEqual(['T->A', 'A->B'])
  })

  it('変更点は確率 1、途中のノードはそこを通る候補の最大の確率になる', () => {
    const elements = rippleElements([item(0.3, 'T', 'A', 'B'), item(0.9, 'T', 'A')])

    const probability = Object.fromEntries(
      elements.filter((e) => !('source' in e.data)).map((e) => [e.data.id, e.data.probability]),
    )
    expect(probability).toEqual({ T: 1, A: 0.9, B: 0.3 })
  })

  it('候補が無ければ要素も無い', () => {
    expect(rippleElements([])).toEqual([])
  })

  it('表示名はパッケージやディレクトリを省く', () => {
    const elements = rippleElements([item(0.5, 'com.example.OrderService#cancel', 'web/order/cancel.html')])

    const labels = elements.filter((e) => !('source' in e.data)).map((e) => e.data.label)
    expect(labels).toEqual(['OrderService#cancel', 'cancel.html'])
  })
})
