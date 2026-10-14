// Shared left navigation for the AirDetente help "posters".
// Each page includes this script and calls nothing — it self-injects the sidebar
// and wraps the existing .poster content into the .layout/.main shell.
(function () {
  var GROUPS = [
    { title: "Général", items: [
      ["index.html", "Accueil"],
    ]},
    { title: "Instruments", items: [
      ["instrument-action.html", "Action"],
      ["instrument-altimetre.html", "Altimètre"],
      ["instrument-anemometre.html", "Anémomètre"],
      ["instrument-approche.html", "Approche finale"],
      ["instrument-bille.html", "Bille"],
      ["instrument-carburant.html", "Carburant"],
      ["instrument-checklist.html", "Checklist"],
      ["instrument-chronometre.html", "Chronomètre"],
      ["instrument-circuit.html", "Circuit de piste"],
      ["instrument-rebours.html", "Compte à rebours"],
      ["instrument-conservateur.html", "Conservateur"],
      ["instrument-efis.html", "EFIS"],
      ["instrument-flightrecorder.html", "Enregistreur de vol"],
      ["instrument-horametre.html", "Horamètre"],
      ["instrument-horizon.html", "Horizon"],
      ["instrument-meteo.html", "Météo (radar + vent)"],
      ["instrument-montre.html", "Montre"],
      ["instrument-movingmap.html", "Moving Map"],
      ["instrument-navigation.html", "Prépa Navigation"],
      ["instrument-proximite.html", "Proximité sol (TAWS)"],
      ["instrument-raccourci-dash.html", "Raccourci (ANLSCT)"],
      ["instrument-raccourcis.html", "Raccourcis (CMNSCT)"],
      ["instrument-session.html", "Session de vol"],
      ["instrument-tableau-blanc.html", "Tableau blanc (CMNWHB)"],
      ["instrument-terrains.html", "Terrains proches"],
      ["instrument-toucher.html", "Toucher de piste"],
      ["instrument-trafic.html", "Trafic Safesky"],
      ["instrument-variometre.html", "Variomètre"],
    ]},
    { title: "Outils", items: [
      ["cockpit-builder.html", "Créateur de cockpit"],
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
