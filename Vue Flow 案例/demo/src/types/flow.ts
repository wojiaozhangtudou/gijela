export type BizNodeType = 'start' | 'llm' | 'end'

export interface BizNodeConfig {
  modelKey?: string
  temperature?: number
  maxTokens?: number
  prompt?: string
}

export interface DefinitionNode {
  id: string
  type: BizNodeType
  position: { x: number; y: number }
  config: BizNodeConfig
}

export interface DefinitionEdge {
  id: string
  sourceNodeId: string
  targetNodeId: string
  sourceHandle?: string | null
  targetHandle?: string | null
}

export interface WorkflowDefinition {
  nodes: DefinitionNode[]
  edges: DefinitionEdge[]
}
