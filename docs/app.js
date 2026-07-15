(() => {
  "use strict";

  const OWNER = "andresaavelasquez-ctrl";
  const REPOSITORY = "ZenGecko-Mobile";
  const API_URL = `https://api.github.com/repos/${OWNER}/${REPOSITORY}/releases/latest`;
  const RELEASES_URL = `https://github.com/${OWNER}/${REPOSITORY}/releases`;
  const CACHE_KEY = "zen-browser-latest-release-v1";
  const CACHE_TTL_MS = 10 * 60 * 1000;

  const elements = {
    download: document.querySelector("#download-apk"),
    subtitle: document.querySelector("#download-subtitle"),
    releasePage: document.querySelector("#release-page"),
    sourceCode: document.querySelector("#source-code"),
    status: document.querySelector("#release-status"),
    badge: document.querySelector("#version-badge"),
    date: document.querySelector("#release-date"),
    size: document.querySelector("#release-size"),
    file: document.querySelector("#release-file"),
    notes: document.querySelector("#release-notes-list")
  };

  function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return "No disponible";
    const units = ["B", "KB", "MB", "GB"];
    const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    const value = bytes / Math.pow(1024, unitIndex);
    return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  function formatDate(dateValue) {
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return "No disponible";
    return new Intl.DateTimeFormat("es", {
      day: "2-digit",
      month: "long",
      year: "numeric"
    }).format(date);
  }

  function readCache() {
    try {
      const cached = JSON.parse(localStorage.getItem(CACHE_KEY));
      if (!cached || Date.now() - cached.savedAt > CACHE_TTL_MS) return null;
      return cached.release;
    } catch {
      return null;
    }
  }

  function writeCache(release) {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify({
        savedAt: Date.now(),
        release
      }));
    } catch {
      // La página puede funcionar aunque el navegador bloquee localStorage.
    }
  }

  function getReleaseNotes(body) {
    if (!body) return ["Consulta la página de la Release para ver todos los cambios."];

    const lines = body
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => /^[-*]\s+/.test(line))
      .map((line) => line.replace(/^[-*]\s+/, "").replace(/[`*_#]/g, ""))
      .filter(Boolean)
      .slice(0, 6);

    return lines.length ? lines : ["Consulta la página de la Release para ver todos los cambios."];
  }

  function renderRelease(release) {
    const apk = Array.isArray(release.assets)
      ? release.assets.find((asset) => /\.apk$/i.test(asset.name))
      : null;

    elements.badge.textContent = release.tag_name || release.name || "Última";
    elements.date.textContent = formatDate(release.published_at || release.created_at);
    elements.releasePage.href = release.html_url || RELEASES_URL;
    elements.sourceCode.href = release.zipball_url ||
      `https://github.com/${OWNER}/${REPOSITORY}/archive/refs/heads/main.zip`;

    elements.notes.replaceChildren();
    for (const note of getReleaseNotes(release.body)) {
      const item = document.createElement("li");
      item.textContent = note;
      elements.notes.appendChild(item);
    }

    if (!apk) {
      elements.file.textContent = "APK no encontrada";
      elements.size.textContent = "—";
      elements.subtitle.textContent = "Abre la lista de versiones";
      elements.download.href = release.html_url || RELEASES_URL;
      elements.download.classList.remove("is-disabled");
      elements.download.removeAttribute("aria-disabled");
      elements.status.textContent = "La última Release existe, pero no contiene un archivo .apk.";
      return;
    }

    elements.file.textContent = apk.name;
    elements.file.title = apk.name;
    elements.size.textContent = formatBytes(apk.size);
    elements.subtitle.textContent = `${release.tag_name || "Última versión"} · ${formatBytes(apk.size)}`;
    elements.download.href = apk.browser_download_url;
    elements.download.setAttribute("download", apk.name);
    elements.download.classList.remove("is-disabled");
    elements.download.removeAttribute("aria-disabled");
    elements.status.textContent = "APK verificada en la última GitHub Release.";
  }

  async function loadLatestRelease() {
    const cached = readCache();
    if (cached) {
      renderRelease(cached);
      elements.status.textContent = "Comprobando si existe una versión más reciente…";
    }

    try {
      const response = await fetch(`${API_URL}?t=${Date.now()}`, {
        cache: "no-store",
        headers: { Accept: "application/vnd.github+json" }
      });

      if (!response.ok) {
        throw new Error(`GitHub respondió con ${response.status}`);
      }

      const release = await response.json();
      writeCache(release);
      renderRelease(release);
    } catch (error) {
      console.error("No se pudo consultar la última Release:", error);

      if (cached) {
        elements.status.textContent =
          "Se muestran los últimos datos guardados porque GitHub no respondió.";
        return;
      }

      elements.download.href = RELEASES_URL;
      elements.download.classList.remove("is-disabled");
      elements.download.removeAttribute("aria-disabled");
      elements.subtitle.textContent = "Abrir GitHub Releases";
      elements.status.textContent =
        "No fue posible consultar la API. Puedes abrir GitHub Releases manualmente.";
    }
  }

  loadLatestRelease();
})();
