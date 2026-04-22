/**
 * HTML Sanitizer - XSS Protection
 *
 * A lightweight HTML sanitizer to prevent Stored XSS attacks.
 * This sanitizer removes dangerous tags, attributes, and scripts
 * while preserving safe formatting tags typically used in rich text editors.
 *
 * @version 1.0.0
 * @license MIT
 */

(function(global) {
    'use strict';

    /**
     * Configuration for allowed HTML elements and attributes
     */
    const CONFIG = {
        // Allowed HTML tags (safe for rich text content)
        ALLOWED_TAGS: [
            'p', 'br', 'hr',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'strong', 'b', 'em', 'i', 'u', 's', 'strike', 'del', 'ins', 'sub', 'sup',
            'span', 'div',
            'ul', 'ol', 'li',
            'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'colgroup', 'col',
            'a',
            'blockquote', 'pre', 'code',
            'img'
        ],

        // Allowed attributes per tag (empty array means no attributes allowed)
        ALLOWED_ATTRS: {
            '*': ['class', 'style', 'id', 'title', 'dir', 'lang'],
            'a': ['href', 'target', 'rel', 'title'],
            'img': ['src', 'alt', 'width', 'height', 'title'],
            'table': ['border', 'cellpadding', 'cellspacing', 'width'],
            'td': ['colspan', 'rowspan', 'width', 'height', 'valign', 'align'],
            'th': ['colspan', 'rowspan', 'width', 'height', 'valign', 'align', 'scope'],
            'col': ['span', 'width'],
            'colgroup': ['span']
        },

        // Allowed URL schemes for href and src attributes
        ALLOWED_URI_SCHEMES: ['http', 'https', 'mailto', 'tel', 'ftp'],

        // Dangerous CSS properties to remove
        FORBIDDEN_CSS_PROPERTIES: [
            'behavior', 'expression', 'binding', '-moz-binding',
            'javascript', 'vbscript'
        ],

        // Forbidden attribute patterns (will be removed regardless of tag)
        FORBIDDEN_ATTR_PATTERNS: [
            /^on/i,  // All event handlers (onclick, onload, onerror, etc.)
            /^data-/i  // Remove data attributes by default (can be adjusted if needed)
        ]
    };

    /**
     * HTMLSanitizer class
     */
    class HTMLSanitizer {
        constructor(config = {}) {
            this.config = {
                ALLOWED_TAGS: config.ALLOWED_TAGS || CONFIG.ALLOWED_TAGS,
                ALLOWED_ATTRS: config.ALLOWED_ATTRS || CONFIG.ALLOWED_ATTRS,
                ALLOWED_URI_SCHEMES: config.ALLOWED_URI_SCHEMES || CONFIG.ALLOWED_URI_SCHEMES,
                FORBIDDEN_CSS_PROPERTIES: config.FORBIDDEN_CSS_PROPERTIES || CONFIG.FORBIDDEN_CSS_PROPERTIES,
                FORBIDDEN_ATTR_PATTERNS: config.FORBIDDEN_ATTR_PATTERNS || CONFIG.FORBIDDEN_ATTR_PATTERNS
            };
        }

        /**
         * Sanitize HTML string to prevent XSS attacks
         * @param {string} html - The HTML string to sanitize
         * @returns {string} - Sanitized HTML string
         */
        sanitize(html) {
            if (!html || typeof html !== 'string') {
                return '';
            }

            // Create a temporary DOM element to parse the HTML
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = html;

            // Recursively sanitize all nodes
            this._sanitizeNode(tempDiv);

            return tempDiv.innerHTML;
        }

        /**
         * Recursively sanitize a DOM node
         * @param {Node} node - The DOM node to sanitize
         * @private
         */
        _sanitizeNode(node) {
            const nodesToRemove = [];

            // Process child nodes
            for (let i = 0; i < node.childNodes.length; i++) {
                const child = node.childNodes[i];

                if (child.nodeType === Node.ELEMENT_NODE) {
                    const tagName = child.tagName.toLowerCase();

                    // Check if tag is allowed
                    if (!this.config.ALLOWED_TAGS.includes(tagName)) {
                        // Special handling: completely remove script, style, iframe, etc.
                        if (['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'textarea', 'button', 'select', 'meta', 'link', 'base', 'frame', 'frameset', 'applet', 'noscript', 'svg', 'math'].includes(tagName)) {
                            nodesToRemove.push(child);
                            continue;
                        }

                        // For other disallowed tags, replace with their text content
                        // But first process children
                        this._sanitizeNode(child);

                        // Move children to parent
                        while (child.firstChild) {
                            node.insertBefore(child.firstChild, child);
                        }
                        nodesToRemove.push(child);
                        continue;
                    }

                    // Sanitize attributes
                    this._sanitizeAttributes(child);

                    // Recursively sanitize children
                    this._sanitizeNode(child);

                } else if (child.nodeType === Node.COMMENT_NODE) {
                    // Remove HTML comments (could contain conditional comments with scripts)
                    nodesToRemove.push(child);
                }
                // Text nodes are safe, keep them
            }

            // Remove marked nodes
            nodesToRemove.forEach(n => n.remove());
        }

        /**
         * Sanitize attributes of an element
         * @param {Element} element - The element whose attributes to sanitize
         * @private
         */
        _sanitizeAttributes(element) {
            const tagName = element.tagName.toLowerCase();
            const attrsToRemove = [];

            // Get allowed attrs for this tag and global attrs
            const tagAllowedAttrs = this.config.ALLOWED_ATTRS[tagName] || [];
            const globalAllowedAttrs = this.config.ALLOWED_ATTRS['*'] || [];
            const allowedAttrs = [...new Set([...tagAllowedAttrs, ...globalAllowedAttrs])];

            // Check each attribute
            for (let i = 0; i < element.attributes.length; i++) {
                const attr = element.attributes[i];
                const attrName = attr.name.toLowerCase();
                const attrValue = attr.value;

                // Check forbidden patterns (like onclick, onload, etc.)
                let isForbidden = false;
                for (const pattern of this.config.FORBIDDEN_ATTR_PATTERNS) {
                    if (pattern.test(attrName)) {
                        isForbidden = true;
                        break;
                    }
                }

                if (isForbidden) {
                    attrsToRemove.push(attrName);
                    continue;
                }

                // Check if attribute is allowed
                if (!allowedAttrs.includes(attrName)) {
                    attrsToRemove.push(attrName);
                    continue;
                }

                // Special handling for URL attributes (href, src)
                if (attrName === 'href' || attrName === 'src') {
                    if (!this._isValidUrl(attrValue)) {
                        attrsToRemove.push(attrName);
                        continue;
                    }
                }

                // Special handling for style attribute
                if (attrName === 'style') {
                    const sanitizedStyle = this._sanitizeStyle(attrValue);
                    if (sanitizedStyle) {
                        element.setAttribute('style', sanitizedStyle);
                    } else {
                        attrsToRemove.push(attrName);
                    }
                }
            }

            // Remove forbidden attributes
            attrsToRemove.forEach(attr => element.removeAttribute(attr));

            // For <a> tags, add rel="noopener noreferrer" if target="_blank"
            if (tagName === 'a' && element.getAttribute('target') === '_blank') {
                element.setAttribute('rel', 'noopener noreferrer');
            }
        }

        /**
         * Validate URL for safe schemes
         * @param {string} url - The URL to validate
         * @returns {boolean} - True if URL is safe
         * @private
         */
        _isValidUrl(url) {
            if (!url) return false;

            // Trim and decode
            const trimmedUrl = url.trim();

            // Check for javascript: and other dangerous schemes
            const lowerUrl = trimmedUrl.toLowerCase().replace(/[\x00-\x20]/g, '');

            // Block javascript:, vbscript:, data: URIs
            if (/^(javascript|vbscript|data):/i.test(lowerUrl)) {
                return false;
            }

            // For relative URLs, allow them
            if (trimmedUrl.startsWith('/') || trimmedUrl.startsWith('.') || trimmedUrl.startsWith('#')) {
                return true;
            }

            // Check scheme
            try {
                const urlObj = new URL(trimmedUrl, window.location.origin);
                const scheme = urlObj.protocol.replace(':', '').toLowerCase();
                return this.config.ALLOWED_URI_SCHEMES.includes(scheme);
            } catch (e) {
                // If URL parsing fails, it might be a relative URL
                return !/^[a-z]+:/i.test(trimmedUrl);
            }
        }

        /**
         * Sanitize CSS style string
         * @param {string} style - The style string to sanitize
         * @returns {string|null} - Sanitized style or null if unsafe
         * @private
         */
        _sanitizeStyle(style) {
            if (!style) return null;

            // Remove comments
            style = style.replace(/\/\*[\s\S]*?\*\//g, '');

            // Check for dangerous patterns
            const lowerStyle = style.toLowerCase();

            for (const forbidden of this.config.FORBIDDEN_CSS_PROPERTIES) {
                if (lowerStyle.includes(forbidden)) {
                    return null;
                }
            }

            // Check for url() with javascript
            if (/url\s*\(\s*["']?\s*(javascript|vbscript|data):/i.test(lowerStyle)) {
                return null;
            }

            // Check for expression()
            if (/expression\s*\(/i.test(lowerStyle)) {
                return null;
            }

            return style;
        }
    }

    /**
     * Create a default instance
     */
    const defaultSanitizer = new HTMLSanitizer();

    /**
     * Global sanitize function
     * @param {string} html - HTML string to sanitize
     * @returns {string} - Sanitized HTML
     */
    function sanitizeHtml(html) {
        return defaultSanitizer.sanitize(html);
    }

    /**
     * Create a new sanitizer with custom config
     * @param {Object} config - Custom configuration
     * @returns {HTMLSanitizer} - New sanitizer instance
     */
    function createSanitizer(config) {
        return new HTMLSanitizer(config);
    }

    // Export to global scope
    global.HTMLSanitizer = HTMLSanitizer;
    global.sanitizeHtml = sanitizeHtml;
    global.createSanitizer = createSanitizer;

})(typeof window !== 'undefined' ? window : this);

