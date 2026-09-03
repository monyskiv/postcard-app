import { Link, useParams } from "react-router-dom";

export function PostcardDetailPage() {
  const { id } = useParams();

  return (
    <div className="detail-page">
      <Link to="/" className="back-link">
        ← Back to browse
      </Link>
      <h1>Detail page coming soon</h1>
      <p>Postcard ID: {id}</p>
    </div>
  );
}
