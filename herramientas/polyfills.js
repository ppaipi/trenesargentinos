/* Polyfills mínimos para navegadores viejos (solo lo que usa la app). */
if (!String.prototype.replaceAll) String.prototype.replaceAll = function (a, b) { return this.split(a).join(b); };
if (!Array.prototype.at) Array.prototype.at = function (i) { i = Math.trunc(i) || 0; if (i < 0) i += this.length; return this[i]; };
if (!Array.prototype.flatMap) Array.prototype.flatMap = function (f, t) { return [].concat.apply([], this.map(f, t)); };
if (typeof AbortController === "undefined") window.AbortController = function () { this.signal = {}; this.abort = function () {}; };
