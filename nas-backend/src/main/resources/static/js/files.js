
async function fetchWithCsrf(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();

    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        const response = await fetch("/api/csrf", { cache: "no-store" });

        if (!response.ok) {
            throw new Error("Please sign in again and retry.");
        }

        const csrf = await response.json();
        const headers = new Headers(options.headers);
        headers.set(csrf.headerName, csrf.token);
        options = { ...options, headers };
    }

    return fetch(url, options);
}

// Page elements
const fileList = document.getElementById("fileList");
const uploadForm = document.getElementById("uploadForm");
const fileInput = document.getElementById("fileInput");
const uploadZone = document.getElementById("uploadZone");
const selectedFile = document.getElementById("selectedFile");
const toast = document.getElementById("toast");
const deleteModal = document.getElementById("deleteModal");
const selectAllFiles = document.getElementById("selectAllFiles");
const selectedCount = document.getElementById("selectedCount");
const deleteSelectedButton = document.getElementById("deleteSelectedButton");

let pendingDelete = null;
let chosenFile = null;
let toastTimeout;

// Show notifications
function showToast(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("show");

    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => {
        toast.classList.remove("show");
    }, 3500);
}

// Show selected file
function updateSelectedFile(file) {
    chosenFile = file;
    selectedFile.textContent = file ? file.name : "";
}

// Select a file
fileInput.addEventListener("change", () => {
    updateSelectedFile(fileInput.files[0] || null);
});

// Drag and drop files
uploadZone.addEventListener("dragover", event => {
    event.preventDefault();
    uploadZone.classList.add("drag-over");
});

uploadZone.addEventListener("dragleave", () => {
    uploadZone.classList.remove("drag-over");
});

uploadZone.addEventListener("drop", event => {
    event.preventDefault();
    uploadZone.classList.remove("drag-over");

    if (event.dataTransfer.files.length) {
        updateSelectedFile(event.dataTransfer.files[0]);
    }
});

// Upload file
uploadForm.addEventListener("submit", async event => {
    event.preventDefault();

    if (!chosenFile) {
        showToast("Please select a file first.", true);
        return;
    }

    if (chosenFile.size > 50 * 1024 * 1024) {
        showToast("File must be 50 MB or smaller.", true);
        return;
    }

    const data = new FormData();
    data.append("file", chosenFile);

    try {
        const response = await fetchWithCsrf("/api/files/upload", {
            method: "POST",
            body: data
        });

        if (!response.ok) {
            throw new Error(await response.text());
        }

        fileInput.value = "";
        updateSelectedFile(null);

        await loadFiles();
        showToast("File uploaded successfully!");
    } catch (error) {
        showToast(error.message, true);
    }
});

// Get selected filenames
function getSelectedFiles() {
    return Array.from(
        fileList.querySelectorAll(".file-checkbox:checked")
    ).map(checkbox => checkbox.value);
}

// Update checkbox selection
function updateSelection() {
    const checkboxes = Array.from(
        fileList.querySelectorAll(".file-checkbox")
    );

    const selected = getSelectedFiles();
    const count = selected.length;

    selectedCount.textContent =
        count + (count === 1 ? " file selected" : " files selected");

    deleteSelectedButton.disabled = count === 0;

    selectAllFiles.checked =
        checkboxes.length > 0 && count === checkboxes.length;

    selectAllFiles.indeterminate =
        count > 0 && count < checkboxes.length;
}

// Select or deselect all files
selectAllFiles.addEventListener("change", () => {
    const checkboxes = fileList.querySelectorAll(".file-checkbox");

    checkboxes.forEach(checkbox => {
        checkbox.checked = selectAllFiles.checked;
    });

    updateSelection();
});

// Load stored files
async function loadFiles() {
    fileList.replaceChildren();

    selectAllFiles.checked = false;
    selectAllFiles.indeterminate = false;
    updateSelection();

    try {
        const response = await fetchWithCsrf("/api/files");

        if (response.status === 403) {
            const cell = fileList.insertRow().insertCell();
            cell.colSpan = 3;
            cell.textContent = "Your account cannot list personal files. Shared files, if permitted, are listed below.";
            return;
        }
        if (!response.ok) {
            throw new Error("Unable to load personal files (HTTP " + response.status + ").");
        }

        const files = await response.json();

        if (files.length === 0) {
            const row = fileList.insertRow();
            const cell = row.insertCell();

            cell.colSpan = 3;
            cell.textContent = "No files uploaded yet.";
            return;
        }

        files.forEach(filename => {
            const row = fileList.insertRow();

            // First column: checkbox
            const checkboxCell = row.insertCell();
            checkboxCell.className = "checkbox-column";

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.className = "file-checkbox";
            checkbox.value = filename;
            checkbox.setAttribute("aria-label", "Select " + filename);

            checkbox.addEventListener("change", updateSelection);
            checkboxCell.append(checkbox);

            // Second column: filename
            const nameCell = row.insertCell();

            const nameWrapper = document.createElement("div");
            nameWrapper.className = "file-name";

            const icon = document.createElement("span");
            icon.className = "file-icon";
            icon.textContent = "📄";

            const name = document.createElement("span");
            name.textContent = filename;

            nameWrapper.append(icon, name);
            nameCell.append(nameWrapper);

            // Third column: actions
            const actionCell = row.insertCell();

            const actions = document.createElement("div");
            actions.className = "action-buttons";

            // Download button
            const download = document.createElement("button");
            download.type = "button";
            download.className = "secondary-btn";
            download.textContent = "Download";

            download.addEventListener("click", () => {
                window.location.href =
                    "/api/files/download/" +
                    encodeURIComponent(filename);
            });

            // Delete button
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "secondary-btn";
            remove.textContent = "Delete";

            remove.addEventListener("click", () => {
                openDeleteModal(filename);
            });

            actions.append(download, remove);
            actionCell.append(actions);
        });

        updateSelection();

    } catch (error) {
        showToast(error.message, true);
    }
}

// Open delete confirmation modal
function openDeleteModal(filenames) {
    pendingDelete = Array.isArray(filenames)
        ? [...filenames]
        : [filenames];

    const message = document.getElementById("deleteMessage");
    const title = document.getElementById("modalTitle");

    if (pendingDelete.length === 1) {
        title.textContent = "Delete File?";
        message.textContent =
            'Are you sure you want to delete "' +
            pendingDelete[0] + '"?';
    } else {
        title.textContent = "Delete Files?";
        message.textContent =
            "Are you sure you want to delete " +
            pendingDelete.length + " selected files?";
    }

    deleteModal.hidden = false;
    document.getElementById("cancelDelete").focus();
}

// Close delete confirmation modal
function closeDeleteModal() {
    deleteModal.hidden = true;
    pendingDelete = null;
}

// Cancel deletion
document.getElementById("cancelDelete")
    .addEventListener("click", closeDeleteModal);

// Confirm deletion
document.getElementById("confirmDelete")
    .addEventListener("click", async () => {
        if (!pendingDelete || pendingDelete.length === 0) {
            return;
        }

        const filenames = [...pendingDelete];

        try {
            // Delete selected files using existing DELETE endpoint
            for (const filename of filenames) {
                const response = await fetchWithCsrf(
                    "/api/files/" + encodeURIComponent(filename),
                    { method: "DELETE" }
                );

                if (!response.ok) {
                    throw new Error(
                        'Unable to delete "' + filename + '": ' +
                        await response.text()
                    );
                }
            }

            closeDeleteModal();
            await loadFiles();

            showToast(
                filenames.length === 1
                    ? "File deleted successfully!"
                    : "Selected files deleted successfully!"
            );

        } catch (error) {
            closeDeleteModal();
            await loadFiles();
            showToast(error.message, true);
        }
    });

// Delete selected files
deleteSelectedButton.addEventListener("click", () => {
    const selected = getSelectedFiles();

    if (selected.length > 0) {
        openDeleteModal(selected);
    }
});

// Close modal with Escape
document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !deleteModal.hidden) {
        closeDeleteModal();
    }
});

// Refresh file list
document.getElementById("refreshButton")
    .addEventListener("click", () => {
        loadFiles();
        document.dispatchEvent(new Event("nas:refresh-sharing"));
    });

// Load files when page opens
loadFiles();
