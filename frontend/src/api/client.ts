import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

export type ImpactItem = components['schemas']['ImpactItem']

/** openapi.yaml から生成した型で守られた API クライアント（schema.d.ts は `npm run api` で再生成） */
export const api = createClient<paths>({ baseUrl: '/api' })
