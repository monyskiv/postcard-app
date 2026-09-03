import { BrowserRouter, Route, Routes } from "react-router-dom";
import { HomePage } from "./pages/HomePage";
import { PostcardDetailPage } from "./pages/PostcardDetailPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/postcards/:id" element={<PostcardDetailPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
