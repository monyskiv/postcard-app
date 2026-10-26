import { ELLIPSIS, getPageItems } from "../utils/pagination";

interface PaginationProps {
  /** 1-indexed. */
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const items = getPageItems(currentPage, totalPages);

  return (
    <nav className="page-nav" aria-label="Pagination">
      <button
        type="button"
        className="page-nav-step"
        aria-label="Previous page"
        disabled={currentPage <= 1}
        onClick={() => onPageChange(currentPage - 1)}
      >
        Prev
      </button>

      <ul className="page-nav-pages">
        {items.map((item, i) =>
          item === ELLIPSIS ? (
            <li key={`gap-${i}`} className="page-nav-ellipsis" aria-hidden="true">
              {ELLIPSIS}
            </li>
          ) : (
            <li key={item} className={item === currentPage ? "is-current" : undefined}>
              <button
                type="button"
                className="page-nav-page"
                aria-label={`Page ${item}`}
                aria-current={item === currentPage ? "page" : undefined}
                onClick={() => {
                  if (item !== currentPage) onPageChange(item);
                }}
              >
                {item}
              </button>
            </li>
          ),
        )}
      </ul>

      <button
        type="button"
        className="page-nav-step"
        aria-label="Next page"
        disabled={currentPage >= totalPages}
        onClick={() => onPageChange(currentPage + 1)}
      >
        Next
      </button>
    </nav>
  );
}
