<template>
  <!-- 外层 wrapper 由 Vue 管理，D3 容器和空状态完全隔离，避免 innerHTML='' 踩踏 vdom -->
  <div class="graph-canvas-wrapper" :style="{ height: `${canvasHeight}px` }">
    <!-- 空状态：仅在无数据时存在于 DOM，D3 容器此时完全不挂载 -->
    <div v-if="isEmpty" class="graph-empty">
      <svg viewBox="0 0 64 64" width="48" height="48" fill="none">
        <circle cx="20" cy="32" r="10" stroke="#555" stroke-width="2"/>
        <circle cx="44" cy="20" r="8" stroke="#555" stroke-width="2"/>
        <circle cx="44" cy="44" r="8" stroke="#555" stroke-width="2"/>
        <line x1="29" y1="28" x2="37" y2="23" stroke="#555" stroke-width="1.5"/>
        <line x1="29" y1="36" x2="37" y2="41" stroke="#555" stroke-width="1.5"/>
      </svg>
      <span>暂无图谱数据</span>
    </div>
    <!-- D3 容器：仅在有数据时挂载，Vue 不管理其子节点，D3 独占操作权 -->
    <div v-else ref="containerRef" style="width:100%;height:100%"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as d3 from 'd3'
import type { GraphCanvasEdge, GraphCanvasNode } from '@/components/graph/types'

const props = defineProps<{
  nodes: GraphCanvasNode[]
  edges: GraphCanvasEdge[]
  highlightKeyword?: string
  height?: number
}>()

const emit = defineEmits<{
  (e: 'node-click', nodeId: string): void
  (e: 'edge-click', edgeId: string): void
  (e: 'node-expand', nodeId: string): void
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let cleanup: (() => void) | null = null
const canvasHeight = props.height ?? 500
const isEmpty = computed(() => props.nodes.length === 0 && props.edges.length === 0)
let isAlive = true

function safeEmit(event: 'node-click' | 'edge-click' | 'node-expand', payload: string) {
  if (!isAlive) return
  emit(event as any, payload)
}

// 内部 D3 实例引用（供 locateNode 使用）
let _svgSel: any = null
let _zoomBehavior: any = null
let _nodeDataMap = new Map<string, any>()

// ── 持久化状态（跨重渲染保留）────────────────────────────────────────────────
/** 锁定节点：key=nodeId, value=锁定时的坐标 */
const lockedPositions = new Map<string, { x: number; y: number }>()
/** 已隐藏节点 id 集合 */
const hiddenNodeIds = new Set<string>()

// ── Neo4j-like 调色板：按 type 分配颜色 ──────────────────────────────────
const TYPE_PALETTE = [
  '#4C8EDA', '#E05C5C', '#27AE8F', '#E67E22',
  '#8E44AD', '#2980B9', '#C0392B', '#16A085',
  '#D35400', '#7D3C98',
]
const typeColorMap = new Map<string, string>()
let paletteIdx = 0
function nodeColor(type: string | undefined, matched: boolean): string {
  if (matched) return '#f5a623'
  const key = type || '__default__'
  if (!typeColorMap.has(key)) {
    typeColorMap.set(key, TYPE_PALETTE[paletteIdx % TYPE_PALETTE.length])
    paletteIdx++
  }
  return typeColorMap.get(key)!
}

function truncLabel(text: string, max = 14): string {
  return text && text.length > max ? text.slice(0, max - 1) + '…' : (text || '')
}

// ── 节点操作环形菜单配置 ────────────────────────────────────────────────────
const NODE_R  = 24
const RING_R  = NODE_R + 32   // 节点中心到操作按钮中心的距离
const BTN_R   = 14            // 操作按钮半径

interface ActionBtn {
  key: 'expand' | 'lock' | 'hide'
  angle: number          // 按钮角度（度）
  baseColor: string
  activeColor: string    // 激活态（如已锁定）颜色
  iconD: string          // SVG path
}
const ACTION_BTNS: ActionBtn[] = [
  {
    key: 'expand',
    angle: -90,          // 正上方
    baseColor: '#57C7E3',
    activeColor: '#57C7E3',
    // 树形分叉图标：主干 + 两个分支
    iconD: 'M0,6 L0,-2 M0,-2 L-5,-6 M0,-2 L5,-6',
  },
  {
    key: 'lock',
    angle: 30,           // 右下
    baseColor: '#FFC454',
    activeColor: '#ff8800',
    // 锁头图标：锁体矩形 + 锁扣弧
    iconD: 'M-3.5,0.5 L-3.5,5.5 L3.5,5.5 L3.5,0.5 Z M-2.5,0.5 L-2.5,-2 Q-2.5,-6 0,-6 Q2.5,-6 2.5,-2 L2.5,0.5',
  },
  {
    key: 'hide',
    angle: 150,          // 左下
    baseColor: '#F16667',
    activeColor: '#F16667',
    // 眼睛+斜线（隐藏含义）
    iconD: 'M-5.5,0 Q0,-4.5 5.5,0 Q0,4.5 -5.5,0 M-4,-3.5 L4,3.5',
  },
]

function renderGraph() {
  if (!containerRef.value) return
  const container = containerRef.value
  const width  = container.clientWidth  || 800
  const height = container.clientHeight || 500

  container.innerHTML = ''
  typeColorMap.clear()
  paletteIdx = 0

  if (props.nodes.length === 0 && props.edges.length === 0) return

  // ── 数据准备：过滤隐藏节点，还原锁定坐标 ─────────────────────────────────
  const allNodes: any[] = props.nodes
    .filter(n => !!n.id && !hiddenNodeIds.has(n.id))
    .map(n => {
      const node: any = { ...n }
      if (lockedPositions.has(n.id)) {
        const p = lockedPositions.get(n.id)!
        node.fx = p.x; node.fy = p.y
        node.x  = p.x; node.y  = p.y
      }
      return node
    })

  const visibleIdSet = new Set<string>(allNodes.map(n => n.id))

  const allLinks: any[] = props.edges
    .filter(e => !!e.source && !!e.target
      && !hiddenNodeIds.has(e.source)
      && !hiddenNodeIds.has(e.target))
    .map(e => ({ ...e }))

  for (const edge of allLinks) {
    if (!visibleIdSet.has(edge.source) && !hiddenNodeIds.has(edge.source)) {
      allNodes.push({ id: edge.source, label: edge.source, type: 'AUTO' })
      visibleIdSet.add(edge.source)
    }
    if (!visibleIdSet.has(edge.target) && !hiddenNodeIds.has(edge.target)) {
      allNodes.push({ id: edge.target, label: edge.target, type: 'AUTO' })
      visibleIdSet.add(edge.target)
    }
  }

  const keyword = (props.highlightKeyword || '').trim().toLowerCase()
  const nodeMatched = (d: any): boolean =>
    !!keyword && (String(d.label||'').toLowerCase().includes(keyword)
      || String(d.type||'').toLowerCase().includes(keyword))
  const edgeMatched = (d: any): boolean =>
    !!keyword && (String(d.label||'').toLowerCase().includes(keyword)
      || String(d.source?.id||d.source||'').toLowerCase().includes(keyword)
      || String(d.target?.id||d.target||'').toLowerCase().includes(keyword))

  // ── SVG 骨架 ─────────────────────────────────────────────────────────────
  const svg = d3.select(container)
    .append('svg')
    .attr('width', width)
    .attr('height', height)
    .style('background', '#f8f9fc')
    .style('border-radius', '8px')

  const defs = svg.append('defs')

  // 网格背景
  const grid = defs.append('pattern')
    .attr('id', 'neo4j-grid').attr('width', 40).attr('height', 40)
    .attr('patternUnits', 'userSpaceOnUse')
  grid.append('path').attr('d', 'M 40 0 L 0 0 0 40')
    .attr('fill', 'none').attr('stroke', '#e4e8f0').attr('stroke-width', '1')
  svg.append('rect').attr('width', width).attr('height', height).attr('fill', 'url(#neo4j-grid)')

  // 箭头 marker
  const makeArrow = (id: string, color: string) =>
    defs.append('marker').attr('id', id)
      .attr('viewBox', '0 -4 10 8')
      .attr('refX', NODE_R + 10).attr('refY', 0)
      .attr('markerWidth', 7).attr('markerHeight', 7)
      .attr('orient', 'auto')
      .append('path').attr('d', 'M0,-4L10,0L0,4').attr('fill', color)
  makeArrow('arrow-default', '#94a3b8')
  makeArrow('arrow-highlight', '#e67e22')

  // Glow 滤镜
  const glow = defs.append('filter').attr('id', 'glow')
  glow.append('feGaussianBlur').attr('stdDeviation', '4').attr('result', 'coloredBlur')
  const feMerge = glow.append('feMerge')
  feMerge.append('feMergeNode').attr('in', 'coloredBlur')
  feMerge.append('feMergeNode').attr('in', 'SourceGraphic')

  const graphLayer = svg.append('g')

  const zoom: any = d3.zoom()
    .scaleExtent([0.15, 4])
    .on('zoom', (event: any) => graphLayer.attr('transform', event.transform))
  svg.call(zoom)

  // 点击背景收起操作环
  svg.on('click', (event: any) => {
    const t = event.target as Element
    if (t === svg.node() || t.tagName === 'rect') dismissRing()
  })

  // ── 边 ───────────────────────────────────────────────────────────────────
  const linkGroup = graphLayer.append('g').attr('class', 'links')

  const linkPath = linkGroup.selectAll('path.link-line')
    .data(allLinks).join('path').attr('class', 'link-line')
    .attr('fill', 'none')
    .attr('stroke', (d: any) => edgeMatched(d) ? '#e67e22' : '#94a3b8')
    .attr('stroke-width', (d: any) => edgeMatched(d) ? 2 : 1.5)
    .attr('stroke-opacity', 0.9)
    .attr('marker-end', (d: any) => edgeMatched(d) ? 'url(#arrow-highlight)' : 'url(#arrow-default)')
    .style('cursor', 'pointer')
    .on('mouseenter', function(this: any, _: any, d: any) {
      d3.select(this).attr('stroke-width', 3).attr('stroke-opacity', 1)
    })
    .on('mouseleave', function(this: any, _: any, d: any) {
      d3.select(this).attr('stroke-width', edgeMatched(d) ? 2 : 1.5).attr('stroke-opacity', 0.9)
    })
    .on('click', (_: any, d: any) => safeEmit('edge-click', d.id))

  const linkLabel = linkGroup.selectAll('text.link-label')
    .data(allLinks).join('text').attr('class', 'link-label')
    .text((d: any) => truncLabel(d.label || '', 12))
    .attr('font-size', 10)
    .attr('fill', (d: any) => edgeMatched(d) ? '#e67e22' : '#64748b')
    .attr('text-anchor', 'middle').attr('dominant-baseline', 'central')
    .style('pointer-events', 'none').style('user-select', 'none')

  // ── 节点 ──────────────────────────────────────────────────────────────────
  const nodeGroup = graphLayer.append('g').attr('class', 'nodes')

  const nodeEl = nodeGroup.selectAll('g.node-item')
    .data(allNodes).join('g').attr('class', 'node-item')
    .style('cursor', 'pointer')
    .on('click', (event: any, d: any) => {
      event.stopPropagation()
      safeEmit('node-click', d.id)
      showRing(d)
    })
    .on('mouseenter', function(this: any) {
      d3.select(this).select('circle.node-body').attr('r', NODE_R + 4).attr('filter', 'url(#glow)')
      d3.select(this).select('text.node-label').attr('font-size', 12)
    })
    .on('mouseleave', function(this: any) {
      d3.select(this).select('circle.node-body').attr('r', NODE_R).attr('filter', null)
      d3.select(this).select('text.node-label').attr('font-size', 11)
    })

  // 关键字高亮外环
  nodeEl.append('circle').attr('class', 'node-ring')
    .attr('r', NODE_R + 5).attr('fill', 'none')
    .attr('stroke', (d: any) => nodeMatched(d) ? '#e67e22' : 'transparent')
    .attr('stroke-width', 2.5).attr('stroke-dasharray', '4 2')

  // 节点主体
  nodeEl.append('circle').attr('class', 'node-body')
    .attr('r', NODE_R)
    .attr('fill', (d: any) => nodeColor(d.type, nodeMatched(d)))
    .attr('stroke', (d: any) => nodeMatched(d) ? '#e67e22' : 'rgba(0,0,0,0.15)')
    .attr('stroke-width', (d: any) => nodeMatched(d) ? 2.5 : 1.5)

  // 节点内标签（白色字，对比深色圆）
  nodeEl.append('text').attr('class', 'node-label')
    .text((d: any) => truncLabel(d.label, 6))
    .attr('text-anchor', 'middle').attr('dominant-baseline', 'central')
    .attr('font-size', 11).attr('font-weight', '600').attr('fill', '#ffffff')
    .style('pointer-events', 'none').style('user-select', 'none')

  // 节点外标签（深色字，对比白底）
  nodeEl.append('text').attr('class', 'node-outer-label')
    .text((d: any) => truncLabel(d.label, 16))
    .attr('text-anchor', 'middle').attr('dy', NODE_R + 14)
    .attr('font-size', 11)
    .attr('fill', (d: any) => nodeMatched(d) ? '#e67e22' : '#334155')
    .style('pointer-events', 'none').style('user-select', 'none')

  // 已锁定节点显示锁徽标
  nodeEl.filter((d: any) => lockedPositions.has(d.id))
    .append('text').attr('class', 'lock-badge')
    .text('🔒').attr('text-anchor', 'middle')
    .attr('dy', -(NODE_R + 5)).attr('font-size', 11)
    .style('pointer-events', 'none')

  // ── 节点操作环（环形菜单）────────────────────────────────────────────────
  // 放在节点层之上，跟随被选节点移动
  const ringLayer = graphLayer.append('g').attr('class', 'ring-layer')
  const ringG = ringLayer.append('g').attr('class', 'action-ring').style('display', 'none')
  let ringDatum: any = null

  // 选中高亮圆环
  ringG.append('circle').attr('class', 'ring-sel')
    .attr('r', NODE_R + 4).attr('fill', 'none')
    .attr('stroke', 'rgba(30,40,80,0.6)').attr('stroke-width', 2)
    .attr('stroke-dasharray', '5 3')

  // 从节点到每个按钮的虚线连接
  ACTION_BTNS.forEach(btn => {
    const rad = (btn.angle * Math.PI) / 180
    ringG.append('line')
      .attr('x1', (NODE_R + 5) * Math.cos(rad))
      .attr('y1', (NODE_R + 5) * Math.sin(rad))
      .attr('x2', (RING_R - BTN_R - 1) * Math.cos(rad))
      .attr('y2', (RING_R - BTN_R - 1) * Math.sin(rad))
      .attr('stroke', 'rgba(30,40,80,0.25)').attr('stroke-width', 1)
      .attr('stroke-dasharray', '3 3').style('pointer-events', 'none')
  })

  // 三个操作按钮
  ACTION_BTNS.forEach(btn => {
    const rad = (btn.angle * Math.PI) / 180
    const bx  = RING_R * Math.cos(rad)
    const by  = RING_R * Math.sin(rad)

    const bg = ringG.append('g')
      .attr('class', `action-btn action-btn-${btn.key}`)
      .attr('transform', `translate(${bx},${by})`)
      .style('cursor', 'pointer')

    // 投影（增加立体感）
    bg.append('circle').attr('r', BTN_R + 1)
      .attr('fill', 'rgba(0,0,0,0.15)').attr('transform', 'translate(1.5,1.5)')

    // 按钮主体
    bg.append('circle').attr('class', 'btn-circle')
      .attr('r', BTN_R)
      .attr('fill', btn.baseColor)
      .attr('stroke', 'rgba(255,255,255,0.35)').attr('stroke-width', 1.5)

    // 图标
    bg.append('path').attr('d', btn.iconD)
      .attr('fill', 'none')
      .attr('stroke', '#ffffff').attr('stroke-width', 2)
      .attr('stroke-linecap', 'round').attr('stroke-linejoin', 'round')
      .style('pointer-events', 'none')

    // 悬停放大
    bg.on('mouseenter', function(this: any) {
      d3.select(this).select('.btn-circle').attr('r', BTN_R + 2.5).attr('filter', 'url(#glow)')
    }).on('mouseleave', function(this: any) {
      d3.select(this).select('.btn-circle').attr('r', BTN_R).attr('filter', null)
    })

    // 点击操作
    bg.on('click', (event: any) => {
      event.stopPropagation()
      if (!ringDatum) return
      handleRingAction(btn.key, ringDatum)
    })
  })

  function showRing(d: any) {
    ringDatum = d
    // 锁定按钮根据当前状态变色
    const isLocked = lockedPositions.has(d.id)
    ringG.select('.action-btn-lock .btn-circle')
      .attr('fill', isLocked ? ACTION_BTNS[1].activeColor : ACTION_BTNS[1].baseColor)
    ringG.style('display', null).attr('transform', `translate(${d.x},${d.y})`)
  }

  function dismissRing() {
    ringDatum = null
    ringG.style('display', 'none')
  }

  function handleRingAction(key: ActionBtn['key'], d: any) {
    if (key === 'expand') {
      // 展开一跳：通知父组件调用 API
      safeEmit('node-expand', d.id)
      dismissRing()

    } else if (key === 'lock') {
      if (lockedPositions.has(d.id)) {
        // 解锁
        lockedPositions.delete(d.id)
        d.fx = null; d.fy = null
        simulation.alphaTarget(0.15).restart()
        ringG.select('.action-btn-lock .btn-circle').attr('fill', ACTION_BTNS[1].baseColor)
        nodeEl.filter((nd: any) => nd.id === d.id).select('.lock-badge').remove()
      } else {
        // 锁定
        lockedPositions.set(d.id, { x: d.x, y: d.y })
        d.fx = d.x; d.fy = d.y
        ringG.select('.action-btn-lock .btn-circle').attr('fill', ACTION_BTNS[1].activeColor)
        nodeEl.filter((nd: any) => nd.id === d.id)
          .append('text').attr('class', 'lock-badge')
          .text('🔒').attr('text-anchor', 'middle')
          .attr('dy', -(NODE_R + 5)).attr('font-size', 11)
          .style('pointer-events', 'none')
      }

    } else if (key === 'hide') {
      hiddenNodeIds.add(d.id)
      dismissRing()
      // 重绘（不依赖 props 变化，直接重新渲染）
      simulation.stop()
      cleanup = null
      try { renderGraph() } catch (e) { console.error('[GraphCanvasD3] hide-redraw error', e) }
    }
  }

  // ── 力模拟 ───────────────────────────────────────────────────────────────
  const simulation = d3.forceSimulation(allNodes)
    .force('link', d3.forceLink(allLinks).id((d: any) => d.id).distance(120).strength(0.4))
    .force('charge', d3.forceManyBody().strength(-350))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide(NODE_R + 18))
    .on('tick', () => {
      linkPath.attr('d', (d: any) => `M${d.source.x},${d.source.y}L${d.target.x},${d.target.y}`)
      linkLabel
        .attr('x', (d: any) => (d.source.x + d.target.x) / 2)
        .attr('y', (d: any) => (d.source.y + d.target.y) / 2)
      nodeEl.attr('transform', (d: any) => `translate(${d.x},${d.y})`)
      // 同步节点坐标（供 locateNode 使用）
      allNodes.forEach((nd: any) => _nodeDataMap.set(nd.id, nd))
      // 操作环跟随节点移动
      if (ringDatum) {
        ringG.attr('transform', `translate(${ringDatum.x},${ringDatum.y})`)
      }
    })

  // ── 拖拽 ─────────────────────────────────────────────────────────────────
  nodeEl.call(
    d3.drag()
      .on('start', (event: any, d: any) => {
        if (!event.active) simulation.alphaTarget(0.3).restart()
        d.fx = d.x; d.fy = d.y
        dismissRing()   // 开始拖拽时收起操作环
      })
      .on('drag', (event: any, d: any) => {
        d.fx = event.x; d.fy = event.y
      })
      .on('end', (event: any, d: any) => {
        if (!event.active) simulation.alphaTarget(0)
        if (lockedPositions.has(d.id)) {
          // 拖拽后更新锁定坐标
          lockedPositions.set(d.id, { x: d.x, y: d.y })
        } else {
          d.fx = null; d.fy = null
        }
      })
  )

  svg.call(zoom.transform, d3.zoomIdentity)
  _svgSel = svg
  _zoomBehavior = zoom
  cleanup = () => simulation.stop()
}

onMounted(() => {
  try { renderGraph() } catch (e) { console.error('[GraphCanvasD3] mount error', e) }
})

watch(() => [props.nodes, props.edges, props.highlightKeyword], () => {
  cleanup?.()
  try { renderGraph() } catch (e) { console.error('[GraphCanvasD3] update error', e) }
}, { deep: true })

onBeforeUnmount(() => {
  isAlive = false
  cleanup?.()
})

/** 将视图平移到指定节点并闪烁高亮 */
function locateNode(nodeId: string) {
  const nd = _nodeDataMap.get(nodeId)
  if (!nd || !_svgSel || !_zoomBehavior || !containerRef.value) return
  const w = containerRef.value.clientWidth  || 800
  const h = containerRef.value.clientHeight || 500
  const scale = 1.2
  _svgSel.transition().duration(500)
    .call(_zoomBehavior.transform,
      d3.zoomIdentity.translate(w / 2 - scale * nd.x, h / 2 - scale * nd.y).scale(scale))
  _svgSel.selectAll('g.node-item')
    .filter((d: any) => d.id === nodeId)
    .select('circle.node-body')
    .transition().duration(180).attr('r', 32).attr('filter', 'url(#glow)')
    .transition().duration(180).attr('r', 24).attr('filter', null)
    .transition().duration(180).attr('r', 32).attr('filter', 'url(#glow)')
    .transition().duration(180).attr('r', 24).attr('filter', null)
}

/** 外部主动触发重新布局，不依赖 :key 销毁重建，避免 Vue patch 冲突崩溃 */
function relayout() {
  cleanup?.()
  try { renderGraph() } catch (e) { console.error('[GraphCanvasD3] relayout error', e) }
}

defineExpose({ locateNode, relayout })
</script>

<style scoped>
.graph-canvas-wrapper {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  background: #f8f9fc;
  border: 1px solid #e4e8f0;
}

.graph-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 14px;
  pointer-events: none;
}
</style>
