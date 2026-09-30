/** Decorative shelf-label barcode, derived from the product so every label prints its own. */
export function Barcode({ seed }: { seed: string }) {
  let hash = 2166136261
  for (let i = 0; i < seed.length; i++) {
    hash = Math.imul(hash ^ seed.charCodeAt(i), 16777619)
  }

  const bars: { x: number; w: number }[] = []
  let x = 0
  for (let i = 0; i < 30; i++) {
    hash = Math.imul(hash ^ (hash >>> 13), 1274126177)
    const width = 1 + ((hash >>> 3) & 1) + ((hash >>> 7) & 1)
    const gap = 1 + ((hash >>> 11) & 1)
    bars.push({ x, w: width })
    x += width + gap
  }

  return (
    <svg className="barcode" viewBox={`0 0 ${x} 20`} preserveAspectRatio="none" aria-hidden="true" focusable="false">
      {bars.map((bar) => (
        <rect key={bar.x} x={bar.x} y="0" width={bar.w} height="20" />
      ))}
    </svg>
  )
}
