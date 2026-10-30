const CACHE_NAME = 'pkqb-cache-v1'
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json'
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS)
    })
  )
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => caches.delete(name))
      )
    })
  )
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return

  if (event.request.url.includes('/api/')) {
    return
  }

  // 页面导航（HTML 入口）改为网络优先：先用最新 HTML，失败再回退缓存，
  // 避免发版后仍长期命中旧的 index.html
  if (event.request.mode === 'navigate') {
    event.respondWith(networkFirst(event.request))
    return
  }

  event.respondWith(cacheFirst(event.request))
})

async function networkFirst(request) {
  try {
    const response = await fetch(request)
    if (response && response.status === 200) {
      const cache = await caches.open(CACHE_NAME)
      cache.put(request, response.clone())
    }
    return response
  } catch (e) {
    const cached = await caches.match(request)
    return cached || caches.match('/index.html')
  }
}

async function cacheFirst(request) {
  const cachedResponse = await caches.match(request)
  if (cachedResponse) {
    return cachedResponse
  }

  try {
    const response = await fetch(request)
    if (!response || response.status !== 200 || response.type !== 'basic') {
      return response
    }

    const responseToCache = response.clone()
    const cache = await caches.open(CACHE_NAME)
    cache.put(request, responseToCache)

    return response
  } catch (e) {
    return Response.error()
  }
}
