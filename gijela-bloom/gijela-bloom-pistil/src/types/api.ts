export interface ApiResponse<T = any> {
  code: number
  message?: string
  data?: T
}

export interface MenuNode {
  id: number | string
  name: string
  path?: string
  permission?: string
  icon?: string
  type?: 'D' | 'M' | 'B' | 'S'
  status?: string | number
  children?: MenuNode[]
}
