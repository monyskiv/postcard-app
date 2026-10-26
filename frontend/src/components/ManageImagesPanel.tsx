import { useEffect, useMemo, useState, type FormEvent } from "react";
import type { PostcardDetail } from "../api/postcards";
import { uploadPostcardImages, UnauthorizedError } from "../api/admin";

interface Props {
  token: string;
  postcard: PostcardDetail;
  onUploaded: (updated: PostcardDetail) => void;
  onDone: () => void;
  onUnauthorized: () => void;
}

const SUCCESS_CLOSE_DELAY_MS = 1200;

export function ManageImagesPanel({ token, postcard, onUploaded, onDone, onUnauthorized }: Props) {
  const [front, setFront] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  // Selecting a file previews it immediately (client-side, unwatermarked) so
  // the user can confirm their pick before it's actually uploaded — the
  // watermarked version only exists once the upload round-trip completes.
  const frontPreviewUrl = useMemo(() => (front ? URL.createObjectURL(front) : null), [front]);

  useEffect(() => {
    return () => {
      if (frontPreviewUrl) URL.revokeObjectURL(frontPreviewUrl);
    };
  }, [frontPreviewUrl]);

  useEffect(() => {
    if (!successMessage) return;
    const timer = setTimeout(onDone, SUCCESS_CLOSE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [successMessage, onDone]);

  function handleFrontChange(file: File | null) {
    setFront(file);
    setError(null);
    setSuccessMessage(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!front) {
      setError("Choose an image to upload.");
      return;
    }

    setError(null);
    setUploading(true);
    try {
      const updated = await uploadPostcardImages(token, postcard.id, front);
      onUploaded(updated);
      setFront(null);
      setSuccessMessage("Image updated successfully.");
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
      <div className="manage-images-current">
        <figure>
          <img
            key={frontPreviewUrl ?? postcard.frontImageUrl}
            src={frontPreviewUrl ?? postcard.frontImageUrl}
            alt="Postcard"
            className="admin-thumb-lg"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
          <figcaption>Current image{frontPreviewUrl && " (pending)"}</figcaption>
        </figure>
      </div>

      <form className="admin-form" onSubmit={handleSubmit}>
        <label>
          Postcard image
          <input type="file" accept="image/*" onChange={(e) => handleFrontChange(e.target.files?.[0] ?? null)} />
        </label>

        {error && <p className="status-message status-error">{error}</p>}
        {successMessage && <p className="status-message status-success">{successMessage}</p>}

        <button type="submit" disabled={uploading}>
          {uploading ? "Uploading…" : "Upload image"}
        </button>
      </form>
    </div>
  );
}
