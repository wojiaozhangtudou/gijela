export interface GraphCanvasNode {
  id: string
  label: string
  type?: string
}

export interface GraphCanvasEdge {
  id: string
  source: string
  target: string
  label?: string
  strength?: number
}
