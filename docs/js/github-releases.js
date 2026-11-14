(() => {
  const repositoryApi = "https://api.github.com/repos/d0x-dev/iTune";
  const releasesUrl = `${repositoryApi}/releases?per_page=8`;

  function safeExternalLink(url, label, className) {
    const anchor = document.createElement("a");
    anchor.className = className;
    anchor.textContent = label;
    anchor.href = url;
    anchor.target = "_blank";
    anchor.rel = "noopener noreferrer";
    return anchor;
  }

  function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Date unavailable";
    return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(date);
  }

  function cleanMarkdown(source) {
    return String(source || "")
      .replace(/<img\b[^>]*>/gi, "")
      .replace(/<[^>]*>/g, "")
      .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
      .replace(/\*\*([^*]+)\*\*/g, "$1")
      .replace(/__([^_]+)__/g, "$1")
      .replace(/`([^`]+)`/g, "$1")
      .trim();
  }

  function renderNotes(container, source) {
    const text = cleanMarkdown(source);
    if (!text) {
      container.textContent = "No release notes were added for this version.";
      return;
    }
    for (const rawLine of text.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line) continue;
      const paragraph = document.createElement(line.startsWith("- ") || line.startsWith("* ") ? "div" : "p");
      paragraph.className = line.startsWith("- ") || line.startsWith("* ") ? "release-note-item" : "release-note-paragraph";
      const content = line.replace(/^[-*]\s+/, "");
      const links = /\[([^\]]+)\]\((https:\/\/[^)\s]+)\)/g;
      let cursor = 0;
      for (const match of content.matchAll(links)) {
        paragraph.append(document.createTextNode(content.slice(cursor, match.index)));
        paragraph.append(safeExternalLink(match[2], match[1], "release-note-link"));
        cursor = match.index + match[0].length;
      }
      paragraph.append(document.createTextNode(content.slice(cursor)));
      container.append(paragraph);
    }
  }

  function createReleaseCard(release, index) {
    const article = document.createElement("article");
    article.className = `release-card${index === 0 ? " latest" : ""}`;

    if (index === 0) {
      const label = document.createElement("span");
      label.className = "release-label";
      label.textContent = release.prerelease ? "Latest pre-release" : "Latest release";
      article.append(label);
    }

    const top = document.createElement("div");
    top.className = "release-top";
    const identity = document.createElement("div");
    const version = document.createElement("h3");
    version.className = "release-version";
    version.textContent = release.tag_name || "Version";
    const name = document.createElement("p");
    name.className = "release-name";
    name.textContent = release.name || "iTune release";
    identity.append(version, name);
    const date = document.createElement("time");
    date.className = "release-date";
    date.dateTime = release.published_at || release.created_at || "";
    date.textContent = formatDate(date.dateTime);
    top.append(identity, date);
    article.append(top);

    const notes = document.createElement("div");
    notes.className = "release-notes";
    renderNotes(notes, release.body);
    article.append(notes);

    const actions = document.createElement("div");
    actions.className = "release-actions";
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const apkAssets = assets.filter((asset) => /\.apk$/i.test(asset.name || ""));
    for (const asset of apkAssets) {
      actions.append(safeExternalLink(asset.browser_download_url, `Download APK · ${asset.name}`, "release-download"));
    }
    if (!apkAssets.length) {
      const missing = document.createElement("span");
      missing.className = "release-empty";
      missing.textContent = "No APK attached to this release.";
      actions.append(missing);
    }
    actions.append(safeExternalLink(release.html_url, "Release details", "release-secondary"));
    article.append(actions);

    if (assets.length) {
      const metadata = document.createElement("p");
      metadata.className = "release-asset-meta";
      const downloads = assets.reduce((sum, asset) => sum + (Number(asset.download_count) || 0), 0);
      metadata.textContent = `${assets.length} file${assets.length === 1 ? "" : "s"} · ${downloads.toLocaleString()} downloads`;
      article.append(metadata);
    }
    return article;
  }

  async function loadReleases() {
    const feeds = [...document.querySelectorAll("[data-release-mode]")];
    const facts = document.getElementById("repo-facts");
    if (!feeds.length && !facts) return;

    const headers = { Accept: "application/vnd.github+json" };
    const releaseTask = fetch(releasesUrl, { headers }).then((response) => {
      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);
      return response.json();
    });
    const repositoryTask = facts
      ? fetch(repositoryApi, { headers }).then((response) => {
        if (!response.ok) throw new Error(`GitHub returned ${response.status}`);
        return response.json();
      }).catch(() => null)
      : Promise.resolve(null);

    try {
      const [releases, repository] = await Promise.all([releaseTask, repositoryTask]);
      for (const feed of feeds) {
        feed.replaceChildren();
        if (!Array.isArray(releases) || releases.length === 0) {
          const empty = document.createElement("p");
          empty.className = "release-empty";
          empty.textContent = "No published releases are available yet.";
          feed.append(empty, safeExternalLink("https://github.com/d0x-dev/iTune/releases", "Open GitHub Releases", "release-secondary"));
          continue;
        }
        const visible = feed.dataset.releaseMode === "all" ? releases : releases.slice(0, 1);
        visible.forEach((release, index) => feed.append(createReleaseCard(release, index)));
      }

      if (facts && repository) {
        const values = [
          [repository.stargazers_count, "GitHub stars"],
          [repository.forks_count, "Public forks"],
          [repository.license?.spdx_id || "Open source", "License"],
          [repository.language || "Android", "Primary language"],
        ];
        facts.replaceChildren();
        for (const [value, label] of values) {
          const item = document.createElement("div");
          item.className = "fact";
          const strong = document.createElement("strong");
          strong.textContent = typeof value === "number" ? value.toLocaleString() : value;
          const caption = document.createElement("span");
          caption.textContent = label;
          item.append(strong, caption);
          facts.append(item);
        }
      }
    } catch (error) {
      for (const feed of feeds) {
        const message = document.createElement("p");
        message.className = "release-error";
        message.textContent = "Could not load the live release feed. Open GitHub Releases to view available downloads.";
        feed.replaceChildren(message, safeExternalLink("https://github.com/d0x-dev/iTune/releases", "Open GitHub Releases", "release-secondary"));
      }
      if (facts) {
        const message = document.createElement("div");
        message.className = "fact";
        message.textContent = error instanceof Error ? "GitHub project details are temporarily unavailable." : "Project details unavailable.";
        facts.replaceChildren(message);
      }
    }
  }

  loadReleases();
})();
