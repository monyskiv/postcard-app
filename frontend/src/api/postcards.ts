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
