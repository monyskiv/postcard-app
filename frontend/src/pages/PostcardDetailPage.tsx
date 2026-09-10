import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getPostcard, PostcardNotFoundError, type PostcardDetail } from "../api/postcards";

type Status = "loading" | "not-found" | "error" | "ready";

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
        <>
          <h1>{postcard.title}</h1>

          <div className="detail-images">
            <figure>
              <img
                src={postcard.frontImageUrl}
                alt={`${postcard.title} — front`}
                className="detail-image"
              />
              <figcaption>Front</figcaption>
            </figure>
            <figure>
              <img
                src={postcard.backImageUrl}
                alt={`${postcard.title} — back`}
                className="detail-image"
              />
              <figcaption>Back</figcaption>
            </figure>
          </div>

          <dl className="detail-meta">
            <div>
              <dt>Year</dt>
              <dd>{postcard.year ?? "Unknown"}</dd>
            </div>
            <div>
              <dt>Author</dt>
              <dd>{postcard.author ?? "Unknown"}</dd>
            </div>
            <div>
              <dt>Color</dt>
              <dd>{postcard.color === "COLOR" ? "Color" : "Black & white"}</dd>
            </div>
            <div>
              <dt>Location</dt>
              <dd>{postcard.location ?? "Unknown"}</dd>
            </div>
          </dl>

          {postcard.description && <p className="detail-description">{postcard.description}</p>}
        </>
      )}
    </div>
  );
}
