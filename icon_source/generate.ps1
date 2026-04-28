Add-Type -AssemblyName System.Drawing

$src = "c:\Users\kiril\AndroidStudioProjects\carvix\icon_source\logo.png"
$resRoot = "c:\Users\kiril\AndroidStudioProjects\carvix\app\src\main\res"

Write-Host "Loading source $src..."
$source = [System.Drawing.Image]::FromFile($src)
$bmp = New-Object System.Drawing.Bitmap($source)
$source.Dispose()

# 1) Auto-trim transparent edges
function Get-OpaqueBounds($bitmap) {
    $w = $bitmap.Width; $h = $bitmap.Height
    $minX = $w; $minY = $h; $maxX = 0; $maxY = 0
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $bitmap.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $bitmap.UnlockBits($data)
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $alpha = $bytes[$y * $stride + $x * 4 + 3]
            if ($alpha -gt 8) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    return @{ X = $minX; Y = $minY; W = ($maxX - $minX + 1); H = ($maxY - $minY + 1) }
}

Write-Host "Trimming transparent borders..."
$bounds = Get-OpaqueBounds $bmp
Write-Host "Logo bounds: $($bounds.W) x $($bounds.H) at ($($bounds.X),$($bounds.Y))"
$cropRect = New-Object System.Drawing.Rectangle $bounds.X, $bounds.Y, $bounds.W, $bounds.H
$cropped = $bmp.Clone($cropRect, $bmp.PixelFormat)
$bmp.Dispose()

# 2) Helper: create square PNG with logo centered + optional gradient bg
function New-IconSquare {
    param($img, $size, $padPct, [bool]$drawBg)
    $canvas = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    if ($drawBg) {
        $rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
        $cStart = [System.Drawing.Color]::FromArgb(255, 237, 230, 218) # #EDE6DA
        $cEnd   = [System.Drawing.Color]::FromArgb(255, 220, 207, 184) # #DCCFB8
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $cStart, $cEnd, 45.0
        $g.FillRectangle($brush, 0, 0, $size, $size)
        $brush.Dispose()
    }

    $contentSize = $size * (1 - $padPct * 2)
    $ratio = [Math]::Min($contentSize / $img.Width, $contentSize / $img.Height)
    $newW = $img.Width * $ratio
    $newH = $img.Height * $ratio
    $x = ($size - $newW) / 2
    $y = ($size - $newH) / 2
    $g.DrawImage($img, $x, $y, $newW, $newH)
    $g.Dispose()
    return $canvas
}

# 3) Generate density-specific icons
$legacySizes = @{
    "mdpi"    = 48
    "hdpi"    = 72
    "xhdpi"   = 96
    "xxhdpi"  = 144
    "xxxhdpi" = 192
}

foreach ($d in $legacySizes.Keys) {
    $sz = $legacySizes[$d]
    $folder = "$resRoot\mipmap-$d"
    if (!(Test-Path $folder)) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }

    # Legacy: full self-contained icon with gradient bg, content ~76%
    $legacy = New-IconSquare $cropped $sz 0.10 $true
    $legacy.Save("$folder\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $legacy.Save("$folder\ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $legacy.Dispose()

    # Adaptive foreground: 108dp viewport (sz*108/48). Content in inner ~66% (safe zone).
    $fgSize = [int][Math]::Round($sz * 108 / 48)
    $fg = New-IconSquare $cropped $fgSize 0.22 $false
    $fg.Save("$folder\ic_launcher_foreground.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()

    # Delete WebP defaults if present
    Get-ChildItem "$folder\ic_launcher*.webp" -ErrorAction SilentlyContinue | Remove-Item -Force
    Write-Host "  $d : ic_launcher.png ($sz), ic_launcher_foreground.png ($fgSize)"
}

# 4) Splash icon — wide logo must fit in INSCRIBED CIRCLE,
#    because Android 12+ splash API masks the icon to a circle.
#    Логика: диагональ логотипа должна быть <= диаметр круга (= размер канвы).
$splashSize = 432
$srcDiag = [Math]::Sqrt($cropped.Width * $cropped.Width + $cropped.Height * $cropped.Height)
$splashRatio = ($splashSize * 0.92) / $srcDiag  # 8% margin от края круга
$splashLogoW = $cropped.Width * $splashRatio
$splashLogoH = $cropped.Height * $splashRatio

$splash = New-Object System.Drawing.Bitmap($splashSize, $splashSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($splash)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$splashX = ($splashSize - $splashLogoW) / 2
$splashY = ($splashSize - $splashLogoH) / 2
$g.DrawImage($cropped, $splashX, $splashY, $splashLogoW, $splashLogoH)
$g.Dispose()

$splashPath = "$resRoot\drawable\splash_icon.png"
if (Test-Path "$resRoot\drawable\splash_icon.xml") {
    Remove-Item "$resRoot\drawable\splash_icon.xml" -Force
}
$splash.Save($splashPath, [System.Drawing.Imaging.ImageFormat]::Png)
$splash.Dispose()
Write-Host "Splash: $splashPath ($splashSize x $splashSize, logo fits inscribed circle)"

# 4b) Brand logo for auth screen — tight crop, no padding
$brandSize = 256
$brand = New-IconSquare $cropped $brandSize 0.04 $false
$brandPath = "$resRoot\drawable\brand_logo.png"
$brand.Save($brandPath, [System.Drawing.Imaging.ImageFormat]::Png)
$brand.Dispose()
Write-Host "Brand logo for auth screen: $brandPath"

# 5) Cleanup vector foreground (replaced by PNG)
$fgVector = "$resRoot\drawable\ic_launcher_foreground.xml"
if (Test-Path $fgVector) {
    Remove-Item $fgVector -Force
    Write-Host "Removed old vector: $fgVector"
}

$cropped.Dispose()
Write-Host "`nDONE."
