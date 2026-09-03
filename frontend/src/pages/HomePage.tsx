import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getPostcards, searchPostcards, type PostcardSummary } from "../api/postcards";
import { useDebouncedValue } from "../hooks/useDebouncedValue";

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;

type Status = "loading" | "error" | "ready";

export function HomePage() {
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query.trim(), SEARCH_DEBOUNCE_MS);
  const isSearching = debouncedQuery.length > 0;

  const [page, setPage] = useState(0);
  const [postcards, setPostcards] = useState<PostcardSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState<Status>("loading");
  const [error, setError] = useState<string | null>(null);

  function handleQueryChange(value: string) {
    setQuery(value);
    setPage(0);
  }

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");

    const request = isSearching
      ? searchPostcards(debouncedQuery, page, PAGE_SIZE)
      : getPostcards(page, PAGE_SIZE);

    request
      .then((data) => {
        if (cancelled) return;
        setPostcards(data.content);
        setTotalPages(data.page.totalPages);
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
  }, [page, isSearching, debouncedQuery]);

  return (
    <div className="browse-page">
      <h1>Postcard Collection</h1>

      <input
        type="search"
        className="search-input"
        placeholder="Search by title, author, location, or description…"
        value={query}
        onChange={(e) => handleQueryChange(e.target.value)}
      />

      {status === "loading" && <p className="status-message">Loading postcards…</p>}

      {status === "error" && (
        <p className="status-message status-error">Couldn't load postcards: {error}</p>
      )}

      {status === "ready" && postcards.length === 0 && (
        <p className="status-message">
          {isSearching ? "No postcards match your search." : "No postcards yet. Check back soon."}
        </p>
      )}

      {status === "ready" && postcards.length > 0 && (
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

          <div className="pagination">
            <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </button>
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}
