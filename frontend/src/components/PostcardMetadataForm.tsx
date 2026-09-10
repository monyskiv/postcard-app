import { useState, type FormEvent } from "react";
import type { PostcardDetail } from "../api/postcards";
import type { PostcardMetadataInput } from "../api/admin";

export type PostcardMetadataFormValues = PostcardMetadataInput;

interface Props {
  initial: PostcardDetail | null;
  onSubmit: (values: PostcardMetadataFormValues) => void;
  submitting: boolean;
}

export function PostcardMetadataForm({ initial, onSubmit, submitting }: Props) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [year, setYear] = useState(initial?.year != null ? String(initial.year) : "");
  const [author, setAuthor] = useState(initial?.author ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [color, setColor] = useState<"COLOR" | "BLACK_AND_WHITE">(initial?.color ?? "COLOR");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!title.trim()) {
      setError("Title is required.");
      return;
    }
    if (year.trim() && !/^-?\d+$/.test(year.trim())) {
      setError("Year must be a whole number.");
      return;
    }

    setError(null);
    onSubmit({
      title: title.trim(),
      year: year.trim() ? Number(year.trim()) : null,
      author: author.trim() || null,
      description: description.trim() || null,
      color,
      location: location.trim() || null,
    });
  }

  return (
    <form className="admin-form" onSubmit={handleSubmit}>
      <label>
        Title
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </label>
      <label>
        Year
        <input value={year} onChange={(e) => setYear(e.target.value)} placeholder="e.g. 1958" />
      </label>
      <label>
        Author
        <input value={author} onChange={(e) => setAuthor(e.target.value)} placeholder="Unknown" />
      </label>
      <label>
        Description
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
      </label>
      <label>
        Color
        <select value={color} onChange={(e) => setColor(e.target.value as "COLOR" | "BLACK_AND_WHITE")}>
          <option value="COLOR">Color</option>
          <option value="BLACK_AND_WHITE">Black &amp; white</option>
        </select>
      </label>
      <label>
        Location
        <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Place depicted" />
      </label>

      {error && <p className="status-message status-error">{error}</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? "Saving…" : initial ? "Save changes" : "Create postcard"}
      </button>
    </form>
  );
}
