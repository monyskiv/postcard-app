import type { PostcardDetail } from "./postcards";

const API_URL = import.meta.env.VITE_API_URL;

export class UnauthorizedError extends Error {}

export interface PostcardMetadataInput {
  title: string;
  year: number | null;
  author: string | null;
  description: string | null;
  color: "COLOR" | "BLACK_AND_WHITE";
  location: string | null;
}

async function adminFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      ...(init.headers ?? {}),
      Authorization: `Bearer ${token}`,
    },
  });
  if (response.status === 401) {
    throw new UnauthorizedError("Session expired — please log in again");
  }
  return response;
}

async function errorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const data = await response.json();
    if (data && typeof data.message === "string") return data.message;
  } catch {
    // response body wasn't JSON (or was empty) — fall back below
  }
  return fallback;
}

export async function createPostcard(
  token: string,
  input: PostcardMetadataInput,
): Promise<PostcardDetail> {
  const response = await adminFetch(token, `/api/postcards`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    // Backend requires non-blank front/back image URLs on create; the real
    // images are attached afterward via the separate "manage images" step.
    body: JSON.stringify({ ...input, frontImageUrl: "placeholder", backImageUrl: "placeholder" }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `Failed to create postcard (status ${response.status})`));
  }
  return response.json();
}

export async function updatePostcard(
  token: string,
  id: string,
  input: PostcardMetadataInput,
  frontImageUrl: string,
  backImageUrl: string,
): Promise<PostcardDetail> {
  const response = await adminFetch(token, `/api/postcards/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...input, frontImageUrl, backImageUrl }),
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `Failed to update postcard (status ${response.status})`));
  }
  return response.json();
}

export async function deletePostcard(token: string, id: string): Promise<void> {
  const response = await adminFetch(token, `/api/postcards/${id}`, { method: "DELETE" });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `Failed to delete postcard (status ${response.status})`));
  }
}

export async function uploadPostcardImages(
  token: string,
  id: string,
  front: File | null,
  back: File | null,
): Promise<PostcardDetail> {
  const formData = new FormData();
  if (front) formData.append("front", front);
  if (back) formData.append("back", back);

  const response = await adminFetch(token, `/api/postcards/${id}/images`, {
    method: "POST",
    body: formData,
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `Failed to upload images (status ${response.status})`));
  }
  return response.json();
}
