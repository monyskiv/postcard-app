import { useState, type FormEvent } from "react";
import type { PostcardDetail } from "../api/postcards";
import { uploadPostcardImages, UnauthorizedError } from "../api/admin";

interface Props {
  token: string;
  postcard: PostcardDetail;
  onUploaded: (updated: PostcardDetail) => void;
  onUnauthorized: () => void;
}

export function ManageImagesPanel({ token, postcard, onUploaded, onUnauthorized }: Props) {
  const [front, setFront] = useState<File | null>(null);
  const [back, setBack] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!front && !back) {
      setError("Choose a front and/or back image to upload.");
      return;
    }

    setError(null);
    setUploading(true);
    try {
      const updated = await uploadPostcardImages(token, postcard.id, front, back);
      onUploaded(updated);
      setFront(null);
      setBack(null);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        onUnauthorized();
      } else {
        setError(err instanceof Error ? err.message : "Failed to upload images");
      }
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="manage-images">
      <h3>Manage images</h3>

      <div className="manage-images-current">
        <figure>
          <img
            src={postcard.frontImageUrl}
            alt="Current front"
            className="admin-thumb-lg"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
          <figcaption>Front</figcaption>
        </figure>
        <figure>
          <img
            src={postcard.backImageUrl}
            alt="Current back"
            className="admin-thumb-lg"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
          <figcaption>Back</figcaption>
        </figure>
      </div>

      <form className="admin-form" onSubmit={handleSubmit}>
        <label>
          Front image
          <input type="file" accept="image/*" onChange={(e) => setFront(e.target.files?.[0] ?? null)} />
        </label>
        <label>
          Back image
          <input type="file" accept="image/*" onChange={(e) => setBack(e.target.files?.[0] ?? null)} />
        </label>

        {error && <p className="status-message status-error">{error}</p>}

        <button type="submit" disabled={uploading}>
          {uploading ? "Uploading…" : "Upload images"}
        </button>
      </form>
    </div>
  );
}
