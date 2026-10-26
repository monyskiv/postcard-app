import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getPostcards, searchPostcards, type PostcardSummary } from "../api/postcards";
import { Pagination } from "../components/Pagination";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { parsePageParam } from "../utils/pagination";

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;

type Status = "loading" | "error" | "ready";

// Page 1 is the default, so it's left out of the URL.
function withPage(params: URLSearchParams, page: number): URLSearchParams {
  const next = new URLSearchParams(params);
  if (page > 1) next.set("page", String(page));
  else next.delete("page");
  return next;
}

export function HomePage() {
  // The URL (?q=...&page=N, page 1-indexed) is the source of truth for what's
  // shown, so refresh and back/forward return to the same results page.
  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get("q") ?? "";
  const rawPage = searchParams.get("page");
  const requestedPage = parsePageParam(rawPage);

  const [query, setQuery] = useState(urlQuery);
  const debouncedQuery = useDebouncedValue(query.trim(), SEARCH_DEBOUNCE_MS);
  const isSearching = urlQuery.length > 0;

  const [postcards, setPostcards] = useState<PostcardSummary[]>([]);
  const [shown, setShown] = useState<{ query: string; page: number; totalPages: number } | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [error, setError] = useState<string | null>(null);

  const gridTopRef = useRef<HTMLDivElement>(null);
  const lastShown = useRef<{ query: string; page: number } | null>(null);
  const committedQuery = useRef(debouncedQuery);

  // Keep the input in sync when the URL changes from outside it (back/forward).
  useEffect(() => {
    setQuery((current) => (current.trim() === urlQuery ? current : urlQuery));
  }, [urlQuery]);

  // Commit the debounced search text to the URL; new text resets to page 1.
  // Starting or clearing a search adds a history entry (so Back returns to the
  // browse page you were on); refining an existing search replaces it, so
  // typing doesn't flood history. Only acts when the debounced value itself
  // changes: re-running on a URL change (e.g. Back, while debouncedQuery still
  // holds the old text) would push the old query straight back.
  useEffect(() => {
    if (committedQuery.current === debouncedQuery) return;
    committedQuery.current = debouncedQuery;
    if (urlQuery === debouncedQuery) return;
    const refining = urlQuery !== "" && debouncedQuery !== "";
    setSearchParams(debouncedQuery ? { q: debouncedQuery } : {}, { replace: refining });
  }, [debouncedQuery, urlQuery, setSearchParams]);

  useEffect(() => {
    // Normalize invalid ?page values (e.g. "abc", "0", "-2") before fetching.
    if (rawPage !== null && rawPage !== String(requestedPage)) {
      setSearchParams((prev) => withPage(prev, requestedPage), { replace: true });
      return;
    }

    let cancelled = false;
    setStatus("loading");

    const pageIndex = requestedPage - 1; // Spring pages are 0-indexed
    const request = urlQuery
      ? searchPostcards(urlQuery, pageIndex, PAGE_SIZE)
      : getPostcards(pageIndex, PAGE_SIZE);

    request
      .then((data) => {
        if (cancelled) return;
        const { totalPages } = data.page;
        // Past the end: jump to the last page (or page 1 if there are no
        // results at all) rather than show an empty grid.
        if (requestedPage > Math.max(totalPages, 1)) {
          setSearchParams((prev) => withPage(prev, Math.max(totalPages, 1)), { replace: true });
          return;
        }
        setPostcards(data.content);
        setShown({ query: urlQuery, page: requestedPage, totalPages });
        setStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Something went wrong");
        setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [urlQuery, rawPage, requestedPage, setSearchParams]);

  // After a page change (but not a new search, or the first load), bring the
  // top of the grid back into view.
  useEffect(() => {
    if (status !== "ready" || !shown) return;
    const previous = lastShown.current;
    if (previous && previous.query === shown.query && previous.page !== shown.page) {
      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      gridTopRef.current?.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
    }
    lastShown.current = { query: shown.query, page: shown.page };
  }, [status, shown]);

  function handlePageChange(page: number) {
    setSearchParams((prev) => withPage(prev, page));
  }

  return (
    <div className="browse-page">
      <h1>Postcard Collection</h1>

      <input
        type="search"
        className="search-input"
        placeholder="Search by title, author, location, or description…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      <div ref={gridTopRef} className="browse-results">
        {status === "loading" && <p className="status-message">Loading postcards…</p>}

        {status === "error" && (
          <p className="status-message status-error">Couldn't load postcards: {error}</p>
        )}

        {status === "ready" && postcards.length === 0 && (
          <p className="status-message">
            {isSearching ? "No postcards match your search." : "No postcards yet. Check back soon."}
          </p>
        )}

        {status === "ready" && shown && postcards.length > 0 && (
          <>
            <div className="postcard-grid">
              {postcards.map((postcard) => (
                <Link key={postcard.id} to={`/postcards/${postcard.id}`} className="postcard-card">
                  <img
                    src={postcard.frontImageUrl}
                    alt={postcard.title}
                    className="postcard-card-image"
                    onError={(e) => {
                      e.currentTarget.style.visibility = "hidden";
                    }}
                  />
                  <div className="postcard-card-body">
                    <h2>{postcard.title}</h2>
                    {postcard.year && <p className="postcard-card-year">{postcard.year}</p>}
                  </div>
                </Link>
              ))}
            </div>

            <Pagination
              currentPage={shown.page}
              totalPages={shown.totalPages}
              onPageChange={handlePageChange}
            />
          </>
        )}
      </div>
    </div>
  );
}
