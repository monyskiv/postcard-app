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
  const [back, setBack] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  // Selecting a file previews it immediately (client-side, unwatermarked) so
  // the user can confirm their pick before it's actually uploaded — the
  // watermarked version only exists once the upload round-trip completes.
  const frontPreviewUrl = useMemo(() => (front ? URL.createObjectURL(front) : null), [front]);
  const backPreviewUrl = useMemo(() => (back ? URL.createObjectURL(back) : null), [back]);

  useEffect(() => {
    return () => {
      if (frontPreviewUrl) URL.revokeObjectURL(frontPreviewUrl);
    };
  }, [frontPreviewUrl]);

  useEffect(() => {
    return () => {
      if (backPreviewUrl) URL.revokeObjectURL(backPreviewUrl);
    };
  }, [backPreviewUrl]);

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

  function handleBackChange(file: File | null) {
    setBack(file);
    setError(null);
    setSuccessMessage(null);
  }

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
      setSuccessMessage("Images updated successfully.");
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
            alt="Front"
            className="admin-thumb-lg"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
          <figcaption>Front{frontPreviewUrl && " (pending)"}</figcaption>
        </figure>
        <figure>
          <img
            key={backPreviewUrl ?? postcard.backImageUrl}
            src={backPreviewUrl ?? postcard.backImageUrl}
            alt="Back"
            className="admin-thumb-lg"
            onError={(e) => {
              e.currentTarget.style.visibility = "hidden";
            }}
          />
          <figcaption>Back{backPreviewUrl && " (pending)"}</figcaption>
        </figure>
      </div>

      <form className="admin-form" onSubmit={handleSubmit}>
        <label>
          Front image
          <input type="file" accept="image/*" onChange={(e) => handleFrontChange(e.target.files?.[0] ?? null)} />
        </label>
        <label>
          Back image
          <input type="file" accept="image/*" onChange={(e) => handleBackChange(e.target.files?.[0] ?? null)} />
        </label>

        {error && <p className="status-message status-error">{error}</p>}
        {successMessage && <p className="status-message status-success">{successMessage}</p>}

        <button type="submit" disabled={uploading}>
          {uploading ? "Uploading…" : "Upload images"}
        </button>
      </form>
    </div>
  );
}
