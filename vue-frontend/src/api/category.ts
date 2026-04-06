import request from '@/utils/request'

export function getCategoryTree(region: string) {
  return request.get('/categories/tree', { params: { region } })
}

export function searchCategory(region: string, nameZh: string) {
  return request.get('/categories/search', { params: { region, nameZh } })
}
