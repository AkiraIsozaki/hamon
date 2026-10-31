import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // /api は Spring Boot（./gradlew bootRun）へ中継する。openapi.yaml の servers.url と揃える
    proxy: { '/api': 'http://localhost:8080' },
  },
})
