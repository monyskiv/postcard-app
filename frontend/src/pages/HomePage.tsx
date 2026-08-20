import { useEffect, useState } from "react";
import { getHealth } from "../api/health";

export function HomePage() {
  const [status, setStatus] = useState<string>("checking...");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getHealth()
      .then((data) => setStatus(data.status))
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <h1>Postcard App</h1>
      <p>Backend health check:</p>
      {error ? (
        <p style={{ color: "red" }}>Error: {error}</p>
      ) : (
        <p>status: {status}</p>
      )}
    </div>
  );
}
