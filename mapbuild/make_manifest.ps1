param(
    [string]$Rel = "dist\release",
    [string]$Tag = "maps"
)
# Build manifest.json listing the release files and their sizes (read by the app).
$mb  = Get-Item (Join-Path $Rel 'basemap.mbtiles')
$zip = Get-Item (Join-Path $Rel 'openaip.zip')
$m = [ordered]@{
    tag         = $Tag
    generatedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    files       = @(
        [ordered]@{ name = 'basemap.mbtiles'; kind = 'basemap';       bytes = $mb.Length  },
        [ordered]@{ name = 'openaip.zip';     kind = 'airspace-data'; bytes = $zip.Length }
    )
}
$json = $m | ConvertTo-Json -Depth 4
# Write UTF-8 WITHOUT BOM (a BOM breaks strict JSON parsers on the client side).
[System.IO.File]::WriteAllText((Join-Path $Rel 'manifest.json'), $json, (New-Object System.Text.UTF8Encoding($false)))
