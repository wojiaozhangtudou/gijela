export interface SkillSummary {
  name: string
  source: 'BUILTIN' | 'LOCAL'
  version: string
  enabled: boolean
  builtin: boolean
  lastLoadedAt: string | null
  errorMsg: string | null
}

export interface SkillDetail extends SkillSummary {
  description: string
  entry: string
  inputSchema: Record<string, any>
  manifestRaw: string | null
  sourcePath: string | null
}
