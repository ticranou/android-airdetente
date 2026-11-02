// Swap the theme stylesheet before it is fetched, avoiding FOUC.
// The <link> for the theme must have id="theme-css".
(function () {
  var t = new URLSearchParams(location.search).get('theme');
  if (t && t !== 'dark') {
    var l = document.getElementById('theme-css');
    if (l) {
      l.href = l.href
        .replace('/dark/', '/' + t + '/')
        .replace('assets/docs.css', 'themes/' + t + '/style.css');
    }
  }
})();
