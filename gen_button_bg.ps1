Add-Type -AssemblyName System.Drawing
$imgPath = "d:\code12\video_art\PhotoArt_Video_990\app\src\main\res\drawable-nodpi\intro_1.png"
$bmp = [System.Drawing.Bitmap]::FromFile($imgPath)

# Let's crop the exact button bounding box with its outer glow
# The button in intro_1.png:
# Left edge of glow is around x = 80, right edge is around x = 773 (width ~ 693)
# Top edge is around y = 1530, bottom edge of glow is around y = 1680 (height ~ 150)
$rect = New-Object System.Drawing.Rectangle(70, 1530, 713, 160)
$btnCrop = $bmp.Clone($rect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

# In the text area (x from 220 to 520, y from 30 to 90 relative to rect),
# Let's see if we interpolate vertically between y=20 (above text) and y=100 (below text):
for ($x = 200; $x -le 540; $x++) {
    $cTop = $btnCrop.GetPixel($x, 18)
    $cBot = $btnCrop.GetPixel($x, 102)
    for ($y = 19; $y -lt 102; $y++) {
        $t = ($y - 18) / (102.0 - 18.0)
        $r = [int]($cTop.R * (1 - $t) + $cBot.R * $t)
        $g = [int]($cTop.G * (1 - $t) + $cBot.G * $t)
        $b = [int]($cTop.B * (1 - $t) + $cBot.B * $t)
        $btnCrop.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

$btnCrop.Save("d:\code12\video_art\PhotoArt_Video_990\test_button_bg.png", [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output "Saved test_button_bg.png"

$btnCrop.Dispose()
$bmp.Dispose()
