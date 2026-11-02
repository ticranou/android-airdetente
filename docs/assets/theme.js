// Swap the theme stylesheet before it is fetched, avoiding FOUC.
// Must be loaded as the element immediately before the <link rel="stylesheet">.
(function () {
  var t = new URLSearchParams(location.search).get('theme');
  if (t && t !== 'dark') {
    var l = document.currentScript.nextElementSibling;
    if (l && l.rel === 'stylesheet') {
      l.href = l.href
        .replace('/dark/', '/' + t + '/')
        .replace('assets/docs.css', 'themes/' + t + '/style.css');
    }
  }
})();
