import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { applyYingqiRuntimeSignature } from "./shared/identity/yingqiSignature";
import { I18nProvider } from "./shared/i18n";
import "./styles.css";

applyYingqiRuntimeSignature();

const routerBasename = import.meta.env.BASE_URL.replace(/\/$/, "");

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nProvider>
      <BrowserRouter basename={routerBasename}>
        <App />
      </BrowserRouter>
    </I18nProvider>
  </React.StrictMode>
);
