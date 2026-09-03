const API_URL = import.meta.env.VITE_API_URL;

export interface PostcardSummary {
  id: string;
  title: string;
  year: number | null;
  color: "COLOR" | "BLACK_AND_WHITE";
  location: string | null;
  frontImageUrl: string;
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: PageMetadata;
}

export interface PostcardDetail {
  id: string;
  title: string;
  year: number | null;
  author: string | null;
  description: string | null;
  color: "COLOR" | "BLACK_AND_WHITE";
  location: string | null;
  frontImageUrl: string;
  backImageUrl: string;
  createdAt: string;
  updatedAt: string;
}

export class PostcardNotFoundError extends Error {}

export async function getPostcards(
  page: number,
  size: number,
): Promise<PagedResponse<PostcardSummary>> {
  const response = await fetch(`${API_URL}/api/postcards?page=${page}&size=${size}`);
  if (!response.ok) {
    throw new Error(`Failed to load postcards (status ${response.status})`);
  }
  return response.json();
}

export async function getPostcard(id: string): Promise<PostcardDetail> {
  const response = await fetch(`${API_URL}/api/postcards/${id}`);
  if (response.status === 404 || response.status === 400) {
    // 404 = well-formed id that doesn't exist; 400 = malformed id (not a
    // UUID) rejected before lookup. Both mean "no such postcard" to a user.
    throw new PostcardNotFoundError(`Postcard not found: ${id}`);
  }
  if (!response.ok) {
    throw new Error(`Failed to load postcard (status ${response.status})`);
  }
  return response.json();
}

export async function searchPostcards(
  q: string,
  page: number,
  size: number,
): Promise<PagedResponse<PostcardSummary>> {
  const params = new URLSearchParams({ q, page: String(page), size: String(size) });
  const response = await fetch(`${API_URL}/api/postcards/search?${params}`);
  if (!response.ok) {
    throw new Error(`Failed to search postcards (status ${response.status})`);
  }
  return response.json();
}
