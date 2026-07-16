Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\examplemod\textures'
$blockRoot = Join-Path $root 'block'
$itemRoot = Join-Path $root 'item'
$miscRoot = Join-Path $root 'misc'
New-Item -ItemType Directory -Force -Path $blockRoot,$itemRoot,$miscRoot | Out-Null

function To-Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Write-BlockTexture([string]$name, [string]$baseHex, [string]$darkHex, [string]$accentHex) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16)
    $base = To-Color $baseHex
    $dark = To-Color $darkHex
    $accent = To-Color $accentHex
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) {
            $color = $base
            if ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15) { $color = $dark }
            elseif (($x + $y) % 7 -eq 0) { $color = $dark }
            $bitmap.SetPixel($x, $y, $color)
        }
    }
    foreach ($point in @(@(3,3), @(12,3), @(3,12), @(12,12))) {
        $bitmap.SetPixel($point[0], $point[1], $accent)
        $bitmap.SetPixel($point[0] + 1, $point[1], $accent)
        $bitmap.SetPixel($point[0], $point[1] + 1, $accent)
    }
    $bitmap.Save((Join-Path $blockRoot "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Write-KeyTexture {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16)
    $clear = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
    $brass = To-Color '#D6A444'
    $dark = To-Color '#57411B'
    $glow = To-Color '#5CD4FF'
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) { $bitmap.SetPixel($x, $y, $clear) }
    }
    for ($x = 3; $x -le 8; $x++) {
        for ($y = 2; $y -le 7; $y++) {
            if (($x - 5.5) * ($x - 5.5) + ($y - 4.5) * ($y - 4.5) -le 9) { $bitmap.SetPixel($x, $y, $brass) }
        }
    }
    for ($x = 7; $x -le 13; $x++) { $bitmap.SetPixel($x, 7, $brass); $bitmap.SetPixel($x, 8, $brass) }
    $bitmap.SetPixel(11, 9, $brass); $bitmap.SetPixel(12, 9, $brass); $bitmap.SetPixel(11, 10, $brass)
    $bitmap.SetPixel(5, 4, $glow); $bitmap.SetPixel(6, 4, $glow); $bitmap.SetPixel(5, 5, $glow); $bitmap.SetPixel(6, 5, $glow)
    for ($x = 3; $x -le 13; $x++) { if ($bitmap.GetPixel($x, 7).A -ne 0) { $bitmap.SetPixel($x, 8, $dark) } }
    $bitmap.Save((Join-Path $itemRoot 'construction_key.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Write-PortPanelTexture {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16)
    $white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    for ($x = 0; $x -lt 16; $x++) {
        for ($y = 0; $y -lt 16; $y++) { $bitmap.SetPixel($x, $y, $white) }
    }
    $bitmap.Save((Join-Path $miscRoot 'port_panel.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

Write-BlockTexture 'construction_block' '#7A633E' '#2A2418' '#D7B85B'
Write-BlockTexture 'construction_shell' '#5B6269' '#20242A' '#8F9AA3'
Write-BlockTexture 'construction_interface' '#3D6D7A' '#152C35' '#59D3E7'
Write-BlockTexture 'exit_console' '#31514A' '#15241F' '#74E6A0'
Write-KeyTexture
Write-PortPanelTexture
