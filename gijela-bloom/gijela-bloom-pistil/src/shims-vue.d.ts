declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  // add other env vars here
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
