import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getPostcard, PostcardNotFoundError, type PostcardDetail } from "../api/postcards";

type Status = "loading" | "not-found" | "error" | "ready";

// Rows shown beside the image, in display order; empty values are omitted.
function detailRows(postcard: PostcardDetail): { label: string; value: string }[] {
  const rows = [
    { label: "Publisher", value: postcard.author },
    { label: "Date", value: postcard.year != null ? String(postcard.year) : null },
    { label: "Location", value: postcard.location },
    { label: "Color", value: postcard.color === "COLOR" ? "Color" : "Black and white" },
    { label: "Description", value: postcard.description },
  ];
  return rows.filter((row): row is { label: string; value: string } => !!row.value?.trim());
}

export function PostcardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [postcard, setPostcard] = useState<PostcardDetail | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setStatus("not-found");
      return;
    }

    let cancelled = false;
    setStatus("loading");

    getPostcard(id)
      .then((data) => {
        if (cancelled) return;
        setPostcard(data);
        setStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof PostcardNotFoundError) {
          setStatus("not-found");
        } else {
          setError(err instanceof Error ? err.message : "Something went wrong");
          setStatus("error");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  return (
    <div className="detail-page">
      <Link to="/" className="back-link">
        ← Back to browse
      </Link>

      {status === "loading" && <p className="status-message">Loading postcard…</p>}

      {status === "not-found" && (
        <p className="status-message">Postcard not found. It may have been removed.</p>
      )}

      {status === "error" && (
        <p className="status-message status-error">Couldn't load postcard: {error}</p>
      )}

      {status === "ready" && postcard && (
        <div className="detail-layout">
          <div className="detail-image-wrap">
            <img src={postcard.frontImageUrl} alt={postcard.title} className="detail-image" />
          </div>

          <div className="detail-info">
            <h1>{postcard.title}</h1>

            <dl className="detail-meta">
              {detailRows(postcard).map(({ label, value }) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      )}
    </div>
  );
}
