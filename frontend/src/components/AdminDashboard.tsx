import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import {
  getPostcard,
  getPostcards,
  PostcardNotFoundError,
  type PostcardDetail,
  type PostcardSummary,
} from "../api/postcards";
import { createPostcard, deletePostcard, updatePostcard, UnauthorizedError } from "../api/admin";
import { PostcardMetadataForm, type PostcardMetadataFormValues } from "./PostcardMetadataForm";
import { ManageImagesPanel } from "./ManageImagesPanel";

const PAGE_SIZE = 20;

type PanelMode = "closed" | "create" | "edit";
type ListStatus = "loading" | "error" | "ready";

export function AdminDashboard({ token }: { token: string }) {
  const { logout } = useAuth();

  const [postcards, setPostcards] = useState<PostcardSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [listStatus, setListStatus] = useState<ListStatus>("loading");
  const [listError, setListError] = useState<string | null>(null);

  const [panelMode, setPanelMode] = useState<PanelMode>("closed");
  const [activePostcard, setActivePostcard] = useState<PostcardDetail | null>(null);
  const [panelError, setPanelError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const loadPostcards = useCallback(() => {
    setListStatus("loading");
    getPostcards(page, PAGE_SIZE)
      .then((data) => {
        setPostcards(data.content);
        setTotalPages(data.page.totalPages);
        setListStatus("ready");
      })
      .catch((err) => {
        setListError(err instanceof Error ? err.message : "Something went wrong");
        setListStatus("error");
      });
  }, [page]);

  useEffect(() => {
    loadPostcards();
  }, [loadPostcards]);

  function handleAddClick() {
    setActivePostcard(null);
    setPanelError(null);
    setPanelMode("create");
  }

  async function handleEditClick(id: string) {
    setPanelError(null);
    try {
      const detail = await getPostcard(id);
      setActivePostcard(detail);
      setPanelMode("edit");
    } catch (err) {
      if (err instanceof PostcardNotFoundError) {
        setListError("That postcard no longer exists.");
        loadPostcards();
      } else {
        setListError(err instanceof Error ? err.message : "Failed to load postcard");
      }
    }
  }

  async function handleDeleteClick(id: string, title: string) {
    if (!window.confirm(`Delete "${title}"? This cannot be undone.`)) return;

    try {
      await deletePostcard(token, id);
      if (activePostcard?.id === id) {
        setPanelMode("closed");
        setActivePostcard(null);
      }
      loadPostcards();
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        logout();
      } else {
        setListError(err instanceof Error ? err.message : "Failed to delete postcard");
      }
    }
  }

  async function handleMetadataSubmit(values: PostcardMetadataFormValues) {
    setPanelError(null);
    setSaving(true);
    try {
      if (panelMode === "create") {
        const created = await createPostcard(token, values);
        setActivePostcard(created);
        setPanelMode("edit");
      } else if (panelMode === "edit" && activePostcard) {
        const updated = await updatePostcard(
          token,
          activePostcard.id,
          values,
          activePostcard.frontImageUrl,
          activePostcard.backImageUrl,
        );
        setActivePostcard(updated);
      }
      loadPostcards();
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        logout();
      } else {
        setPanelError(err instanceof Error ? err.message : "Failed to save postcard");
      }
    } finally {
      setSaving(false);
    }
  }

  function handleImagesUpdated(updated: PostcardDetail) {
    setActivePostcard(updated);
    loadPostcards();
  }

  function handleClosePanel() {
    setPanelMode("closed");
    setActivePostcard(null);
    setPanelError(null);
  }

  return (
    <div className="admin-dashboard">
      <div className="admin-header">
        <h1>Admin Dashboard</h1>
        <div className="admin-header-actions">
          <button onClick={handleAddClick}>Add postcard</button>
          <button className="secondary" onClick={logout}>
            Log out
          </button>
        </div>
      </div>

      {listStatus === "loading" && <p className="status-message">Loading postcards…</p>}
      {listStatus === "error" && (
        <p className="status-message status-error">Couldn't load postcards: {listError}</p>
      )}

      {listStatus === "ready" && (
        <>
          {listError && <p className="status-message status-error">{listError}</p>}

          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Thumbnail</th>
                  <th>Title</th>
                  <th>Year</th>
                  <th>Color</th>
                  <th>Location</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {postcards.map((postcard) => (
                  <tr key={postcard.id}>
                    <td>
                      <img
                        src={postcard.frontImageUrl}
                        alt=""
                        className="admin-thumb"
                        onError={(e) => {
                          e.currentTarget.style.visibility = "hidden";
                        }}
                      />
                    </td>
                    <td>{postcard.title}</td>
                    <td>{postcard.year ?? "—"}</td>
                    <td>{postcard.color === "COLOR" ? "Color" : "B&W"}</td>
                    <td>{postcard.location ?? "—"}</td>
                    <td className="admin-row-actions">
                      <button onClick={() => handleEditClick(postcard.id)}>Edit</button>
                      <button className="danger" onClick={() => handleDeleteClick(postcard.id, postcard.title)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
                {postcards.length === 0 && (
                  <tr>
                    <td colSpan={6} className="status-message">
                      No postcards yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </button>
            <span>
              Page {page + 1} of {Math.max(totalPages, 1)}
            </span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </button>
          </div>
        </>
      )}

      {panelMode !== "closed" && (
        <div className="admin-panel">
          <div className="admin-panel-header">
            <h2>{panelMode === "create" ? "Add postcard" : `Edit: ${activePostcard?.title ?? ""}`}</h2>
            <button className="secondary" onClick={handleClosePanel}>
              Close
            </button>
          </div>

          <PostcardMetadataForm
            key={activePostcard?.id ?? "new"}
            initial={activePostcard}
            onSubmit={handleMetadataSubmit}
            submitting={saving}
          />
          {panelError && <p className="status-message status-error">{panelError}</p>}

          {activePostcard && (
            <ManageImagesPanel
              token={token}
              postcard={activePostcard}
              onUploaded={handleImagesUpdated}
              onUnauthorized={logout}
            />
          )}
        </div>
      )}
    </div>
  );
}
