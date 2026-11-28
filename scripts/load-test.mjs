// 轻量并发冒烟：node scripts/load-test.mjs <url> [concurrency] [seconds]
const url = process.argv[2] || 'http://127.0.0.1:18080/api/actuator/health'
const concurrency = Number(process.argv[3] || 200)
const seconds = Number(process.argv[4] || 60)
const stopAt = Date.now() + seconds * 1000
const latencies = []
const statusCounts = {}
let ok = 0
let failed = 0

async function worker() {
  while (Date.now() < stopAt) {
    const start = performance.now()
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(10000) })
      statusCounts[res.status] = (statusCounts[res.status] || 0) + 1
      ok += 1
    } catch {
      failed += 1
    }
    latencies.push(performance.now() - start)
  }
}

const started = Date.now()
await Promise.all(Array.from({ length: concurrency }, () => worker()))
latencies.sort((a, b) => a - b)
const pct = (p) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * p))] || 0
const total = ok + failed
console.log(JSON.stringify({
  url,
  concurrency,
  seconds: Math.round((Date.now() - started) / 1000),
  total,
  ok,
  failed,
  statusCounts,
  qps: (total / ((Date.now() - started) / 1000)).toFixed(1),
  p50: pct(0.5).toFixed(1),
  p95: pct(0.95).toFixed(1),
  p99: pct(0.99).toFixed(1),
}))
process.exit(failed > 0 ? 1 : 0)
