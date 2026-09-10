const API_URL = import.meta.env.VITE_API_URL;

export class InvalidCredentialsError extends Error {}

export async function login(email: string, password: string): Promise<string> {
  const response = await fetch(`${API_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (response.status === 401) {
    throw new InvalidCredentialsError("Invalid email or password");
  }
  if (!response.ok) {
    throw new Error(`Login failed (status ${response.status})`);
  }
  const data: { token: string } = await response.json();
  return data.token;
}
