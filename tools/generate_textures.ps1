Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$textureRoot = Join-Path $root 'src/main/resources/assets/examplemod/textures'

function Color([string] $hex, [int] $alpha = 255) {
    $rgb = [System.Drawing.ColorTranslator]::FromHtml($hex)
    return [System.Drawing.Color]::FromArgb($alpha, $rgb.R, $rgb.G, $rgb.B)
}

function New-Canvas([int] $width = 32, [int] $height = 32) {
    return [System.Drawing.Bitmap]::new($width, $height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Fill-Rect($bitmap, [int] $x, [int] $y, [int] $width, [int] $height, $color) {
    for ($py = $y; $py -lt $y + $height; $py++) {
        for ($px = $x; $px -lt $x + $width; $px++) {
            if ($px -ge 0 -and $py -ge 0 -and $px -lt $bitmap.Width -and $py -lt $bitmap.Height) {
                $bitmap.SetPixel($px, $py, $color)
            }
        }
    }
}

function Stroke-Rect($bitmap, [int] $x, [int] $y, [int] $width, [int] $height, $color) {
    Fill-Rect $bitmap $x $y $width 1 $color
    Fill-Rect $bitmap $x ($y + $height - 1) $width 1 $color
    Fill-Rect $bitmap $x $y 1 $height $color
    Fill-Rect $bitmap ($x + $width - 1) $y 1 $height $color
}

function Set-Pixel($bitmap, [int] $x, [int] $y, $color) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $bitmap.Width -and $y -lt $bitmap.Height) {
        $bitmap.SetPixel($x, $y, $color)
    }
}

function Save-Texture($bitmap, [string] $relativePath) {
    $path = Join-Path $textureRoot $relativePath
    $directory = Split-Path -Parent $path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$black = Color '#111516'
$deep = Color '#1b2224'
$steel = Color '#30393a'
$steelLight = Color '#465153'
$steelEdge = Color '#687173'
$brassDark = Color '#5e451d'
$brass = Color '#9b742d'
$brassLight = Color '#d3a84c'
$copperDark = Color '#643a29'
$copper = Color '#a65d3b'
$copperLight = Color '#d18455'
$verdigris = Color '#3f8177'
$verdigrisLight = Color '#69a69a'
$amber = Color '#f1b94b'
$cream = Color '#ead9a3'

# Construction block: a compact riveted vault with a central rotary housing.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 $deep
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        if ((($x * 3 + $y * 5) % 11) -eq 0) { Set-Pixel $bmp $x $y $steel }
    }
}
Stroke-Rect $bmp 0 0 32 32 $black
Stroke-Rect $bmp 1 1 30 30 $steelEdge
Stroke-Rect $bmp 2 2 28 28 $brassDark
Fill-Rect $bmp 3 3 26 2 $brass
Fill-Rect $bmp 3 27 26 2 $brassDark
Fill-Rect $bmp 3 5 2 22 $brass
Fill-Rect $bmp 27 5 2 22 $brassDark
Fill-Rect $bmp 5 5 22 22 $steel
Stroke-Rect $bmp 6 6 20 20 $black
Stroke-Rect $bmp 7 7 18 18 $steelLight
Fill-Rect $bmp 9 9 14 14 $deep
Stroke-Rect $bmp 9 9 14 14 $brassDark
Fill-Rect $bmp 13 8 6 2 $brass
Fill-Rect $bmp 13 22 6 2 $brassDark
Fill-Rect $bmp 8 13 2 6 $brass
Fill-Rect $bmp 22 13 2 6 $brassDark
Stroke-Rect $bmp 12 12 8 8 $brass
Fill-Rect $bmp 14 14 4 4 $black
Fill-Rect $bmp 15 13 2 6 $copper
Fill-Rect $bmp 13 15 6 2 $copperLight
foreach ($p in @(@(3,3),@(28,3),@(3,28),@(28,28),@(7,7),@(24,7),@(7,24),@(24,24))) {
    Set-Pixel $bmp $p[0] $p[1] $brassLight
}
Save-Texture $bmp 'block/construction_block.png'

# Interior interface: a brass socket plate with copper conduits.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 $steel
for ($i = 0; $i -lt 32; $i += 4) {
    Set-Pixel $bmp $i (($i * 3) % 31) $steelLight
}
Stroke-Rect $bmp 0 0 32 32 $black
Stroke-Rect $bmp 1 1 30 30 $brassDark
Fill-Rect $bmp 3 3 26 3 $brass
Fill-Rect $bmp 3 26 26 3 $brassDark
Fill-Rect $bmp 3 6 3 20 $copperDark
Fill-Rect $bmp 26 6 3 20 $copper
Fill-Rect $bmp 6 6 20 20 $deep
Fill-Rect $bmp 14 6 4 5 $copper
Fill-Rect $bmp 14 21 4 5 $copperDark
Fill-Rect $bmp 6 14 5 4 $copper
Fill-Rect $bmp 21 14 5 4 $copperDark
Stroke-Rect $bmp 10 10 12 12 $brass
Stroke-Rect $bmp 11 11 10 10 $brassLight
Fill-Rect $bmp 13 13 6 6 $black
Fill-Rect $bmp 14 14 4 4 $steelEdge
foreach ($p in @(@(3,3),@(28,3),@(3,28),@(28,28),@(8,8),@(23,8),@(8,23),@(23,23))) {
    Set-Pixel $bmp $p[0] $p[1] $brassLight
}
Save-Texture $bmp 'block/construction_interface.png'

# Shell: repeating dark andesite plates with restrained brass ribs.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 $steel
Fill-Rect $bmp 1 1 14 14 $steelLight
Fill-Rect $bmp 17 1 14 14 $steel
Fill-Rect $bmp 1 17 14 14 $steel
Fill-Rect $bmp 17 17 14 14 $steelLight
Fill-Rect $bmp 15 0 2 32 $black
Fill-Rect $bmp 0 15 32 2 $black
Fill-Rect $bmp 15 2 1 11 $brassDark
Fill-Rect $bmp 16 19 1 11 $brass
Fill-Rect $bmp 2 15 11 1 $brass
Fill-Rect $bmp 19 16 11 1 $brassDark
Stroke-Rect $bmp 0 0 32 32 $deep
foreach ($p in @(@(2,2),@(13,2),@(18,2),@(29,2),@(2,13),@(13,13),@(18,13),@(29,13),
                   @(2,18),@(13,18),@(18,18),@(29,18),@(2,29),@(13,29),@(18,29),@(29,29))) {
    Set-Pixel $bmp $p[0] $p[1] $brassLight
}
for ($i = 4; $i -lt 13; $i += 3) {
    Set-Pixel $bmp $i 6 $steelEdge
    Set-Pixel $bmp ($i + 16) 22 $steelEdge
}
Save-Texture $bmp 'block/construction_shell.png'

# Exit console: oxidized copper controls mounted in a brass machine frame.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 $deep
Stroke-Rect $bmp 0 0 32 32 $black
Stroke-Rect $bmp 1 1 30 30 $brassDark
Fill-Rect $bmp 3 3 26 26 $steel
Fill-Rect $bmp 5 5 22 20 $copperDark
Stroke-Rect $bmp 5 5 22 20 $brass
Fill-Rect $bmp 7 7 18 12 $verdigris
Stroke-Rect $bmp 7 7 18 12 $black
Fill-Rect $bmp 9 9 14 8 $deep
Fill-Rect $bmp 11 11 10 4 $verdigrisLight
Fill-Rect $bmp 13 12 6 2 $cream
Fill-Rect $bmp 8 21 5 2 $brassLight
Fill-Rect $bmp 14 20 4 4 $black
Fill-Rect $bmp 15 20 2 3 $amber
Fill-Rect $bmp 20 21 4 2 $copperLight
foreach ($p in @(@(3,3),@(28,3),@(3,28),@(28,28),@(6,6),@(25,6),@(6,24),@(25,24))) {
    Set-Pixel $bmp $p[0] $p[1] $brassLight
}
Save-Texture $bmp 'block/exit_console.png'

# Construction key: transparent brass gear bow, copper shaft and dark outline.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 ([System.Drawing.Color]::Transparent)
Fill-Rect $bmp 3 5 11 2 $black
Fill-Rect $bmp 1 7 15 7 $black
Fill-Rect $bmp 3 14 11 2 $black
Fill-Rect $bmp 3 7 11 7 $brassDark
Fill-Rect $bmp 5 5 7 11 $brass
Fill-Rect $bmp 6 7 5 7 $brassLight
Fill-Rect $bmp 7 8 3 5 $deep
Fill-Rect $bmp 13 10 15 5 $black
Fill-Rect $bmp 13 11 14 3 $brass
Fill-Rect $bmp 14 11 12 1 $brassLight
Fill-Rect $bmp 23 14 5 5 $black
Fill-Rect $bmp 24 13 3 5 $brassDark
Fill-Rect $bmp 19 14 3 4 $black
Fill-Rect $bmp 20 13 2 4 $brass
Set-Pixel $bmp 2 9 $brassLight
Set-Pixel $bmp 13 7 $copper
Set-Pixel $bmp 13 15 $copperDark
Save-Texture $bmp 'item/construction_key.png'

# Tintable emissive port overlay: transparent mechanical frame and connector nodes.
$bmp = New-Canvas
Fill-Rect $bmp 0 0 32 32 ([System.Drawing.Color]::Transparent)
$glowStrong = Color '#ffffff' 235
$glow = Color '#ffffff' 180
$glowSoft = Color '#ffffff' 90
Stroke-Rect $bmp 1 1 30 30 $glowSoft
Fill-Rect $bmp 4 3 24 1 $glow
Fill-Rect $bmp 4 28 24 1 $glow
Fill-Rect $bmp 3 4 1 24 $glow
Fill-Rect $bmp 28 4 1 24 $glow
Stroke-Rect $bmp 8 8 16 16 $glow
Fill-Rect $bmp 14 4 4 5 $glowSoft
Fill-Rect $bmp 14 23 4 5 $glowSoft
Fill-Rect $bmp 4 14 5 4 $glowSoft
Fill-Rect $bmp 23 14 5 4 $glowSoft
Fill-Rect $bmp 13 13 6 6 $glowSoft
Stroke-Rect $bmp 12 12 8 8 $glowStrong
foreach ($p in @(@(3,3),@(28,3),@(3,28),@(28,28),@(6,6),@(25,6),@(6,25),@(25,25))) {
    Fill-Rect $bmp ($p[0] - 1) ($p[1] - 1) 3 3 $glowStrong
}
Save-Texture $bmp 'misc/port_panel.png'

Write-Host 'Generated construction textures in' $textureRoot
