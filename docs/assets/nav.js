// Shared left navigation for the AirDetente help "posters".
// Each page includes this script and calls nothing — it self-injects the sidebar
// and wraps the existing .poster content into the .layout/.main shell.
(function () {
  var GROUPS = [
    { title: "Général", items: [
      ["index.html", "Accueil"],
    ]},
    { title: "Instruments", items: [
      ["instrument-conservateur.html", "Conservateur"],
      ["instrument-anemometre.html", "Anémomètre"],
      ["instrument-altimetre.html", "Altimètre"],
      ["instrument-variometre.html", "Variomètre"],
      ["instrument-horizon.html", "Horizon"],
      ["instrument-bille.html", "Bille"],
      ["instrument-chronometre.html", "Chronomètre"],
      ["instrument-rebours.html", "Compte à rebours"],
      ["instrument-horametre.html", "Horamètre"],
      ["instrument-terrains.html", "Terrains proches"],
      ["instrument-meteo.html", "Météo (radar + vent)"],
      ["instrument-montre.html", "Montre"],
      ["instrument-flightrecorder.html", "Enregistreur de vol"],
      ["instrument-trafic.html", "Trafic Safesky"],
      ["instrument-proximite.html", "Proximité sol (TAWS)"],
      ["instrument-efis.html", "EFIS"],
      ["instrument-movingmap.html", "Moving Map"],
      ["instrument-approche.html", "Approche finale"],
    ]},
    { title: "Réglages", items: [
      ["reglages-apparence.html", "Affichage"],
      ["reglages-cockpits.html", "Cockpits"],
      ["reglages-appareils.html", "Appareils"],
      ["reglages-checklists.html", "Checklists"],
      ["reglages-vac.html", "Terrains"],
    ]},
  ];

  var here = location.pathname.split("/").pop() || "index.html";

  var side = document.createElement("aside");
  side.className = "sidebar";
  var html = '<div class="side-brand"><span class="logo">✈</span><b>AirDetente</b></div>';
  GROUPS.forEach(function (g) {
    html += '<div class="nav-group">' + g.title + "</div><nav>";
    g.items.forEach(function (it) {
      var cls = it[0] === here ? ' class="active"' : "";
      html += '<a href="' + it[0] + '"' + cls + ">" + it[1] + "</a>";
    });
    html += "</nav>";
  });
  side.innerHTML = html;

  // Wrap the body's existing content into the layout shell.
  var main = document.createElement("div");
  main.className = "main";
  while (document.body.firstChild) main.appendChild(document.body.firstChild);

  var layout = document.createElement("div");
  layout.className = "layout";
  layout.appendChild(side);
  layout.appendChild(main);
  document.body.appendChild(layout);
})();
