import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * 合并 class：clsx 处理条件类名，tailwind-merge 消除同属性冲突
 * 例：cn('px-2', 'px-4') → 'px-4'
 */
export function cn(...inputs) {
  return twMerge(clsx(inputs))
}
