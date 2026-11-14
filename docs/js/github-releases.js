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

  function trustedHttps(value) {
    try {
      const url = new URL(value);
      return url.protocol === "https:" ? url.href : null;
    } catch { return null; }
  }

  function appendMarkdownInline(parent, text) {
    const pattern = /!\[([^\]]*)\]\((https:\/\/[^)\s]+)(?:\s+"[^"]*")?\)|\[([^\]]+)\]\((https:\/\/[^)\s]+)\)|\*\*([^*]+)\*\*|__([^_]+)__|~~([^~]+)~~|`([^`]+)`|\*([^*]+)\*/g;
    let cursor = 0;
    for (const match of text.matchAll(pattern)) {
      parent.append(document.createTextNode(text.slice(cursor, match.index)));
      if (match[1] !== undefined) {
        const src = trustedHttps(match[2]);
        if (src) {
          const frame = document.createElement("div");
          frame.className = "release-image-wrap";
          const image = document.createElement("img");
          image.src = src;
          image.alt = match[1] || "Image included in release notes";
          image.loading = "lazy";
          image.addEventListener("error", () => frame.remove(), { once: true });
          frame.append(image);
          parent.append(frame);
        } else parent.append(document.createTextNode(match[1]));
      } else if (match[3] !== undefined) {
        const href = trustedHttps(match[4]);
        if (href) parent.append(safeExternalLink(href, match[3], ""));
        else parent.append(document.createTextNode(match[3]));
      } else {
        const element = document.createElement(match[5] !== undefined || match[6] !== undefined ? "strong" : match[7] !== undefined ? "del" : match[8] !== undefined ? "code" : "em");
        element.textContent = match[5] ?? match[6] ?? match[7] ?? match[8] ?? match[9] ?? "";
        parent.append(element);
      }
      cursor = match.index + match[0].length;
    }
    parent.append(document.createTextNode(text.slice(cursor)));
  }

  function releaseMarkdownSource(source) {
    return String(source || "")
      .replace(/<br\s*\/?\s*>/gi, "\n")
      .replace(/<img\b([^>]*)\/?\s*>/gi, (_tag, attributes) => {
        const src = attributes.match(/\bsrc\s*=\s*["']([^"']+)["']/i)?.[1];
        const alt = attributes.match(/\balt\s*=\s*["']([^"']*)["']/i)?.[1] || "Release image";
        return src ? `\n![${alt}](${src})\n` : "";
      })
      .replace(/<[^>]*>/g, "");
  }

  function renderFullMarkdown(container, source) {
    const lines = releaseMarkdownSource(source).split(/\r?\n/);
    let paragraph = [];
    let list = null;
    let codeLines = null;
    const flushParagraph = () => {
      if (!paragraph.length) return;
      const element = document.createElement("p");
      appendMarkdownInline(element, paragraph.join(" "));
      container.append(element);
      paragraph = [];
    };
    const closeList = () => { list = null; };
    for (const raw of lines) {
      const line = raw.trim();
      if (line.startsWith("```")) {
        flushParagraph(); closeList();
        if (codeLines) {
          const pre = document.createElement("pre");
          const code = document.createElement("code");
          code.textContent = codeLines.join("\n");
          pre.append(code); container.append(pre); codeLines = null;
        } else codeLines = [];
        continue;
      }
      if (codeLines) { codeLines.push(raw); continue; }
      if (!line) { flushParagraph(); closeList(); continue; }
      const heading = line.match(/^(#{1,4})\s+(.+)$/);
      if (heading) {
        flushParagraph(); closeList();
        const element = document.createElement(`h${heading[1].length}`);
        appendMarkdownInline(element, heading[2]); container.append(element); continue;
      }
      if (/^(---+|\*\*\*+)$/.test(line)) { flushParagraph(); closeList(); container.append(document.createElement("hr")); continue; }
      const bullet = line.match(/^([-*+] |\d+\. )(.*)$/);
      if (bullet) {
        flushParagraph();
        const ordered = /^\d/.test(bullet[1]);
        const type = ordered ? "ol" : "ul";
        if (!list || list.tagName.toLowerCase() !== type) {
          closeList(); list = document.createElement(type); container.append(list);
        }
        const item = document.createElement("li"); appendMarkdownInline(item, bullet[2]); list.append(item); continue;
      }
      if (line.startsWith("> ")) {
        flushParagraph(); closeList();
        const quote = document.createElement("blockquote");
        const content = document.createElement("p"); appendMarkdownInline(content, line.slice(2));
        quote.append(content); container.append(quote); continue;
      }
      closeList();
      paragraph.push(line);
    }
    flushParagraph();
    if (codeLines) {
      const pre = document.createElement("pre"); const code = document.createElement("code");
      code.textContent = codeLines.join("\n"); pre.append(code); container.append(pre);
    }
    if (!container.childElementCount) container.textContent = "No release notes were added for this version.";
  }

  let releaseDialog;
  function getReleaseDialog() {
    if (releaseDialog) return releaseDialog;
    const dialog = document.createElement("dialog");
    dialog.className = "release-dialog";
    const shell = document.createElement("div"); shell.className = "release-dialog-shell";
    const header = document.createElement("div"); header.className = "release-dialog-header";
    const icon = document.createElement("div"); icon.className = "release-dialog-icon";
    const logo = document.createElement("img"); logo.src = "./images/ic-itune-waves.svg"; logo.alt = "iTune"; icon.append(logo);
    const heading = document.createElement("div"); heading.className = "release-dialog-heading";
    const kicker = document.createElement("p"); kicker.className = "release-dialog-kicker"; kicker.textContent = "iTune · Release notes";
    const title = document.createElement("p"); title.className = "release-dialog-title"; title.dataset.dialogTitle = "";
    heading.append(kicker, title);
    const close = document.createElement("button"); close.className = "release-dialog-close"; close.type = "button"; close.setAttribute("aria-label", "Close release notes"); close.textContent = "×";
    close.addEventListener("click", () => dialog.close());
    header.append(icon, heading, close);
    const content = document.createElement("div"); content.className = "release-dialog-content";
    const versionRow = document.createElement("div"); versionRow.className = "release-dialog-version-row";
    const version = document.createElement("h2"); version.className = "release-dialog-version"; version.dataset.dialogVersion = "";
    const date = document.createElement("time"); date.className = "release-dialog-date"; date.dataset.dialogDate = "";
    versionRow.append(version, date);
    const markdown = document.createElement("div"); markdown.className = "release-markdown"; markdown.dataset.dialogMarkdown = "";
    content.append(versionRow, markdown);
    const footer = document.createElement("div"); footer.className = "release-dialog-footer"; footer.dataset.dialogActions = "";
    const closeFooter = document.createElement("button"); closeFooter.className = "release-dialog-action"; closeFooter.type = "button"; closeFooter.textContent = "Done";
    closeFooter.addEventListener("click", () => dialog.close()); footer.append(closeFooter);
    shell.append(header, content, footer); dialog.append(shell); document.body.append(dialog);
    dialog.addEventListener("click", (event) => { if (event.target === dialog) dialog.close(); });
    dialog._releaseRefs = { title, version, date, markdown, footer, closeFooter };
    releaseDialog = dialog;
    return dialog;
  }

  function showReleaseDetails(release) {
    const dialog = getReleaseDialog();
    const refs = dialog._releaseRefs;
    refs.title.textContent = release.name || "Release details";
    refs.version.textContent = release.tag_name || "Version";
    refs.date.dateTime = release.published_at || release.created_at || "";
    refs.date.textContent = formatDate(refs.date.dateTime);
    refs.markdown.replaceChildren();
    renderFullMarkdown(refs.markdown, release.body);
    refs.footer.replaceChildren();
    for (const asset of (release.assets || []).filter((item) => /\.apk$/i.test(item.name || ""))) {
      refs.footer.append(safeExternalLink(asset.browser_download_url, `Download ${asset.name}`, "release-dialog-action primary"));
    }
    refs.footer.append(safeExternalLink(release.html_url, "View on GitHub", "release-dialog-action"));
    refs.footer.append(refs.closeFooter);
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
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
    const details = document.createElement("button");
    details.className = "release-secondary";
    details.type = "button";
    details.textContent = "Release details";
    details.addEventListener("click", () => showReleaseDetails(release));
    actions.append(details);
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
