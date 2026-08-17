Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

# Same geometry as res/drawable/pulse_alert_mark.xml, 24-unit viewport.
$body = @(
  @('M', 12.10, 1.90),
  @('Q', 12.60, 1.90, 12.95, 2.40),
  @('L', 19.30, 8.20),
  @('Q', 19.75, 8.65, 19.60, 9.25),
  @('L', 17.20, 15.10),
  @('L', 13.10, 21.90),
  @('Q', 12.75, 22.40, 12.10, 22.35),
  @('Q', 11.55, 22.25, 11.30, 21.75),
  @('L', 7.00, 14.90),
  @('L', 4.50, 8.90),
  @('Q', 4.30, 8.35, 4.70, 8.00),
  @('L', 11.30, 2.30),
  @('Q', 11.60, 1.90, 12.10, 1.90)
)
$facet = @(@(12.00,6.00), @(15.00,10.20), @(12.70,17.20), @(9.50,10.50))
$chip  = @(@(3.20,17.60), @(6.60,19.00), @(4.40,21.90))

function New-Preview([int]$size, [string]$out) {
  $k = $size / 24.0
  $bmp = New-Object System.Drawing.Bitmap($size, $size)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.Clear([System.Drawing.Color]::FromArgb(255, 20, 22, 34))

  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate

  $cx = 0.0; $cy = 0.0
  foreach ($seg in $body) {
    switch ($seg[0]) {
      'M' { $cx = $seg[1]; $cy = $seg[2] }
      'L' {
        $path.AddLine(($cx*$k), ($cy*$k), ($seg[1]*$k), ($seg[2]*$k))
        $cx = $seg[1]; $cy = $seg[2]
      }
      'Q' {
        # quadratic (P0, C, P1) expressed as the equivalent cubic
        $qx = $seg[1]; $qy = $seg[2]; $ex = $seg[3]; $ey = $seg[4]
        $c1x = $cx + 2.0/3.0 * ($qx - $cx); $c1y = $cy + 2.0/3.0 * ($qy - $cy)
        $c2x = $ex + 2.0/3.0 * ($qx - $ex); $c2y = $ey + 2.0/3.0 * ($qy - $ey)
        $path.AddBezier(($cx*$k), ($cy*$k), ($c1x*$k), ($c1y*$k), ($c2x*$k), ($c2y*$k), ($ex*$k), ($ey*$k))
        $cx = $ex; $cy = $ey
      }
    }
  }
  $path.CloseFigure()

  $path.StartFigure()
  $path.AddPolygon(($facet | ForEach-Object { New-Object System.Drawing.PointF(($_[0]*$k), ($_[1]*$k)) }))
  $path.StartFigure()
  $path.AddPolygon(($chip  | ForEach-Object { New-Object System.Drawing.PointF(($_[0]*$k), ($_[1]*$k)) }))

  $g.FillPath([System.Drawing.Brushes]::White, $path)
  $g.Dispose()
  $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

$dir = 'c:\1\1\3\Coin_Pulse\build'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# One sheet with the three sizes the status bar and the shade actually use.
$sheet = New-Object System.Drawing.Bitmap(288, 208)
$gs = [System.Drawing.Graphics]::FromImage($sheet)
$gs.Clear([System.Drawing.Color]::FromArgb(255, 20, 22, 34))
foreach ($s in @(192, 48, 24)) {
  $tmp = "$dir\_mark_$s.png"
  New-Preview -size $s -out $tmp
  $img = [System.Drawing.Image]::FromFile($tmp)
  switch ($s) {
    192 { $gs.DrawImage($img, 8, 8) }
    48  { $gs.DrawImage($img, 210, 8) }
    24  { $gs.DrawImage($img, 210, 70) }
  }
  $img.Dispose()
  Remove-Item $tmp -Force
}
$gs.Dispose()
$sheet.Save("$dir\notif_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()
"wrote $dir\notif_preview.png"
