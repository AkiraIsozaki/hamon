import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

export type ImpactItem = components['schemas']['ImpactItem']
export type NodeKind = components['schemas']['NodeKind']
export type NodeSummary = components['schemas']['NodeSummary']

export const api = createClient<paths>({ baseUrl: '/api' })
