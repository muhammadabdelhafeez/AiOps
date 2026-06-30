/**
 * KFH AIOps Command Center - Utility Functions
 * Helper functions for formatting, generation, and common operations
 */
window.KFHUtils = (function() {
  'use strict';

  // ========== ID GENERATION ==========
  function generateId(prefix = '') {
    const id = Math.random().toString(36).substr(2, 9);
    return prefix ? `${prefix}-${id}` : id;
  }

  function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  // ========== RANDOM HELPERS ==========
  function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  function randomFloat(min, max, decimals = 1) {
    return parseFloat((Math.random() * (max - min) + min).toFixed(decimals));
  }

  function randomChoice(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
  }

  function randomChoices(arr, count) {
    const shuffled = [...arr].sort(() => 0.5 - Math.random());
    return shuffled.slice(0, count);
  }

  // ========== DATE/TIME FORMATTING ==========
  function formatDateTime(timestamp) {
    if (!timestamp) return 'Never';
    const date = new Date(timestamp);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function formatDate(timestamp) {
    if (!timestamp) return 'Never';
    const date = new Date(timestamp);
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  function formatTime(timestamp) {
    if (!timestamp) return '--:--';
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function formatRelativeTime(timestamp) {
    if (!timestamp) return 'Never';
    const now = Date.now();
    const diff = now - timestamp;

    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}d ago`;
    return formatDate(timestamp);
  }

  function formatDuration(ms) {
    if (!ms || ms < 0) return '0s';
    if (ms < 60000) return `${Math.floor(ms / 1000)}s`;
    if (ms < 3600000) return `${Math.floor(ms / 60000)}m`;
    if (ms < 86400000) return `${Math.floor(ms / 3600000)}h ${Math.floor((ms % 3600000) / 60000)}m`;
    return `${Math.floor(ms / 86400000)}d`;
  }

  // ========== NUMBER FORMATTING ==========
  function formatNumber(num) {
    if (num === null || num === undefined) return '0';
    return num.toLocaleString('en-US');
  }

  function formatCompact(num) {
    if (num === null || num === undefined) return '0';
    if (num < 1000) return num.toString();
    if (num < 1000000) return (num / 1000).toFixed(1) + 'K';
    return (num / 1000000).toFixed(1) + 'M';
  }

  function formatPercent(value, decimals = 1) {
    if (value === null || value === undefined) return '0%';
    return value.toFixed(decimals) + '%';
  }

  function formatBytes(bytes) {
    if (!bytes) return '0 B';
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return parseFloat((bytes / Math.pow(1024, i)).toFixed(2)) + ' ' + sizes[i];
  }

  // ========== STRING UTILITIES ==========
  function truncate(str, maxLength = 50) {
    if (!str) return '';
    if (str.length <= maxLength) return str;
    return str.substring(0, maxLength - 3) + '...';
  }

  function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
  }

  function toTitleCase(str) {
    if (!str) return '';
    return str.replace(/\w\S*/g, txt =>
      txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase()
    );
  }

  function slugify(str) {
    if (!str) return '';
    return str
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
  }

  function getInitials(name, maxLength = 2) {
    if (!name) return '';
    return name
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .substring(0, maxLength);
  }

  // ========== HTML SANITIZATION ==========
  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  function sanitizeHtml(html) {
    if (!html) return '';
    // Use DOMPurify if available
    if (window.DOMPurify) {
      return DOMPurify.sanitize(html);
    }
    // Basic fallback
    return escapeHtml(html);
  }

  // ========== DOM UTILITIES ==========
  function createElement(tag, attributes = {}, children = []) {
    const el = document.createElement(tag);

    Object.entries(attributes).forEach(([key, value]) => {
      if (key === 'className') {
        el.className = value;
      } else if (key === 'style' && typeof value === 'object') {
        Object.assign(el.style, value);
      } else if (key.startsWith('on') && typeof value === 'function') {
        el.addEventListener(key.substring(2).toLowerCase(), value);
      } else if (key === 'dataset' && typeof value === 'object') {
        Object.entries(value).forEach(([k, v]) => el.dataset[k] = v);
      } else {
        el.setAttribute(key, value);
      }
    });

    children.forEach(child => {
      if (typeof child === 'string') {
        el.appendChild(document.createTextNode(child));
      } else if (child instanceof Node) {
        el.appendChild(child);
      }
    });

    return el;
  }

  function clearElement(el) {
    while (el.firstChild) {
      el.removeChild(el.firstChild);
    }
  }

  function showElement(el, display = 'block') {
    if (el) el.style.display = display;
  }

  function hideElement(el) {
    if (el) el.style.display = 'none';
  }

  function toggleElement(el, show, display = 'block') {
    if (el) el.style.display = show ? display : 'none';
  }

  // ========== DEBOUNCE / THROTTLE ==========
  function debounce(func, wait = 300) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  function throttle(func, limit = 100) {
    let inThrottle;
    return function executedFunction(...args) {
      if (!inThrottle) {
        func(...args);
        inThrottle = true;
        setTimeout(() => inThrottle = false, limit);
      }
    };
  }

  /**
   * Wire a text input so that typing does NOT re-render on every keystroke.
   * The input handler is debounced (~250ms by default) and, after the
   * downstream `onChange(value)` call fires, focus and caret position are
   * restored on the rebuilt input element (some pages re-render the entire
   * region which destroys and recreates the <input>).
   *
   * Use this for every search/filter box in vanilla-JS pages so that:
   *  - The screen does not flicker on each letter typed.
   *  - The input never loses focus mid-typing.
   *  - Filtering only runs after the user pauses typing.
   *
   * @param {string} inputId        - id of the <input> element.
   * @param {Function} onChange     - called with the latest value once typing settles.
   * @param {Object} [opts]
   * @param {number} [opts.wait=250] - debounce delay in milliseconds.
   * @returns {Function|null}        - cleanup function (removes listener) or null
   *                                   if the element wasn't found.
   */
  function bindLiveSearch(inputId, onChange, opts = {}) {
    const wait = typeof opts.wait === 'number' ? opts.wait : 250;
    const input = document.getElementById(inputId);
    if (!input) return null;

    let pendingSelStart = null;
    let pendingSelEnd = null;

    const apply = debounce(function(value) {
      try {
        onChange(value);
      } catch (err) {
        console.error('[bindLiveSearch] onChange threw:', err);
      }
      // After the page re-renders, refocus the (possibly recreated) input
      // and restore the caret to where the user left it.
      requestAnimationFrame(function() {
        const refreshed = document.getElementById(inputId);
        if (!refreshed) return;
        if (document.activeElement !== refreshed) {
          try { refreshed.focus({ preventScroll: true }); }
          catch (e) { refreshed.focus(); }
        }
        if (pendingSelStart !== null) {
          try { refreshed.setSelectionRange(pendingSelStart, pendingSelEnd); }
          catch (e) { /* unsupported on some input types */ }
        }
      });
    }, wait);

    const handler = function(event) {
      pendingSelStart = event.target.selectionStart;
      pendingSelEnd = event.target.selectionEnd;
      apply(event.target.value);
    };

    input.addEventListener('input', handler);
    return function unbind() {
      input.removeEventListener('input', handler);
    };
  }

  // ========== ARRAY UTILITIES ==========
  function groupBy(array, key) {
    return array.reduce((groups, item) => {
      const group = typeof key === 'function' ? key(item) : item[key];
      groups[group] = groups[group] || [];
      groups[group].push(item);
      return groups;
    }, {});
  }

  function sortBy(array, key, order = 'asc') {
    return [...array].sort((a, b) => {
      const aVal = typeof key === 'function' ? key(a) : a[key];
      const bVal = typeof key === 'function' ? key(b) : b[key];
      const comparison = aVal > bVal ? 1 : aVal < bVal ? -1 : 0;
      return order === 'asc' ? comparison : -comparison;
    });
  }

  function unique(array, key) {
    if (!key) return [...new Set(array)];
    const seen = new Set();
    return array.filter(item => {
      const k = typeof key === 'function' ? key(item) : item[key];
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
  }

  // ========== COLOR UTILITIES ==========
  function hexToRgba(hex, alpha = 1) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    if (!result) return `rgba(0, 0, 0, ${alpha})`;
    return `rgba(${parseInt(result[1], 16)}, ${parseInt(result[2], 16)}, ${parseInt(result[3], 16)}, ${alpha})`;
  }

  // ========== STORAGE UTILITIES ==========
  function setStorage(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.warn('Storage not available:', e);
    }
  }

  function getStorage(key, defaultValue = null) {
    try {
      const item = localStorage.getItem(key);
      return item ? JSON.parse(item) : defaultValue;
    } catch (e) {
      return defaultValue;
    }
  }

  function removeStorage(key) {
    try {
      localStorage.removeItem(key);
    } catch (e) {
      console.warn('Storage not available:', e);
    }
  }

  // ========== CLIPBOARD ==========
  async function copyToClipboard(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (e) {
      // Fallback for older browsers
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      const success = document.execCommand('copy');
      document.body.removeChild(textarea);
      return success;
    }
  }

  // ========== TOAST NOTIFICATIONS ==========
  function showToast(message, type = 'info', duration = 3500) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `kfh-toast kfh-toast-${type}`;
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
    toast.setAttribute('aria-live', type === 'error' ? 'assertive' : 'polite');

    const text = document.createElement('span');
    text.className = 'kfh-toast-message';
    text.textContent = message;
    toast.appendChild(text);

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'kfh-toast-close';
    close.setAttribute('aria-label', 'Dismiss notification');
    close.innerHTML = '&times;';
    toast.appendChild(close);

    const dismiss = () => {
      if (toast.classList.contains('is-leaving')) return;
      toast.classList.add('is-leaving');
      setTimeout(() => toast.remove(), 240);
    };
    close.addEventListener('click', dismiss);

    container.appendChild(toast);
    setTimeout(dismiss, duration);
  }

  // ========== PUBLIC API ==========
  return {
    // ID Generation
    generateId,
    generateUUID,

    // Random
    randomInt,
    randomFloat,
    randomChoice,
    randomChoices,

    // Date/Time
    formatDateTime,
    formatDate,
    formatTime,
    formatRelativeTime,
    formatDuration,

    // Numbers
    formatNumber,
    formatCompact,
    formatPercent,
    formatBytes,

    // Strings
    truncate,
    capitalize,
    toTitleCase,
    slugify,
    getInitials,
    escapeHtml,
    sanitizeHtml,

    // DOM
    createElement,
    clearElement,
    showElement,
    hideElement,
    toggleElement,

    // Functions
    debounce,
    throttle,
    bindLiveSearch,

    // Arrays
    groupBy,
    sortBy,
    unique,

    // Colors
    hexToRgba,

    // Storage
    setStorage,
    getStorage,
    removeStorage,

    // Clipboard
    copyToClipboard,

    // Toast
    showToast
  };
})();
