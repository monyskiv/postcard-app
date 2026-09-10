import { useAuth } from "../auth/AuthContext";
import { AdminLoginForm } from "../components/AdminLoginForm";
import { AdminDashboard } from "../components/AdminDashboard";

export function AdminPage() {
  const { token } = useAuth();

  return <div className="admin-page">{token ? <AdminDashboard token={token} /> : <AdminLoginForm />}</div>;
}
