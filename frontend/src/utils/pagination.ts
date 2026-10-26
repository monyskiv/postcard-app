export const ELLIPSIS = "…";

export type PageItem = number | typeof ELLIPSIS;

// Above this many pages, collapse the middle into "…" gaps.
const SHOW_ALL_THRESHOLD = 7;

/**
 * Page numbers (1-indexed) to render in the pagination bar. Always includes
 * the first page, the last page, the current page, and one neighbor on each
 * side of it. A gap of exactly one page shows that page instead of "…".
 */
export function getPageItems(currentPage: number, totalPages: number): PageItem[] {
  if (totalPages < 1) return [];

  const current = Math.min(Math.max(currentPage, 1), totalPages);

  if (totalPages <= SHOW_ALL_THRESHOLD) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }

  const pages = [...new Set([1, current - 1, current, current + 1, totalPages])]
    .filter((page) => page >= 1 && page <= totalPages)
    .sort((a, b) => a - b);

  const items: PageItem[] = [];
  pages.forEach((page, i) => {
    const previous = pages[i - 1];
    if (previous !== undefined) {
      if (page - previous === 2) items.push(previous + 1);
      else if (page - previous > 2) items.push(ELLIPSIS);
    }
    items.push(page);
  });
  return items;
}

/**
 * Parses the ?page= URL param into a 1-indexed page number. Anything that
 * isn't a whole number >= 1 (missing, "abc", "0", "-2", "2.5") becomes 1.
 * Upper-bound clamping happens once the total page count is known.
 */
export function parsePageParam(raw: string | null): number {
  if (raw === null || !/^\d+$/.test(raw)) return 1;
  const page = Number(raw);
  return page >= 1 && Number.isSafeInteger(page) ? page : 1;
}
