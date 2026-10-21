param(
    [string]$Voice = 'Microsoft Irina Desktop'
)

$ErrorActionPreference = 'Stop'

$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$topic = Join-Path $project 'docs\marketing\channel\2026-10\videos\export-and-share'
$silent = Join-Path $topic 'v1\publish\05-export-and-share.mp4'
$version = Join-Path $topic 'v2'
$source = Join-Path $version 'source'
$audio = Join-Path $version 'voiceover'
$segments = Join-Path $audio 'segments'
$publish = Join-Path $version 'publish'
foreach ($folder in @($source, $audio, $segments, $publish)) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}

$sourceCopy = Join-Path $source '05-export-and-share-silent.mp4'
if (-not (Test-Path -LiteralPath $sourceCopy)) {
    Copy-Item -LiteralPath $silent -Destination $sourceCopy
}

$lines = @(
    @{ Number = 1; Start = 0.25; Text = 'Откройте Ещё.' },
    @{ Number = 2; Start = 2.65; Text = 'Затем Файлы и экспорт.' },
    @{ Number = 3; Start = 5.30; Text = 'Нажмите Экспорт.' },
    @{ Number = 4; Start = 7.50; Text = 'Выберите формат Фэмили Три Ди Эс.' },
    @{ Number = 5; Start = 11.20; Text = 'Окно сохранения.' },
    @{ Number = 6; Start = 13.15; Text = 'Укажите папку. Копия сохранит фото и вложения.' },
    @{ Number = 7; Start = 19.55; Text = 'Нажмите Сэйв.' },
    @{ Number = 8; Start = 22.60; Text = 'Затем Поделиться деревом.' },
    @{ Number = 9; Start = 25.55; Text = 'Выберите мессенджер или облако для отправки.' },
    @{ Number = 10; Start = 30.75; Text = 'Готово! Дерево сохранено.' }
)

$synth = New-Object -ComObject SAPI.SpVoice
try {
    $selectedVoice = @($synth.GetVoices() | Where-Object { $_.GetDescription() -like "$Voice*" })[0]
    if ($null -eq $selectedVoice) { throw "Installed SAPI voice not found: $Voice" }
    $synth.Voice = $selectedVoice
    $synth.Rate = 2
    $synth.Volume = 100
    foreach ($line in $lines) {
        $wav = Join-Path $segments ('{0:D2}.wav' -f $line.Number)
        $stream = New-Object -ComObject SAPI.SpFileStream
        try {
            $stream.Open($wav, 3, $false)
            $synth.AudioOutputStream = $stream
            [void]$synth.Speak($line.Text, 0)
        } finally {
            $stream.Close()
            [System.Runtime.InteropServices.Marshal]::ReleaseComObject($stream) | Out-Null
        }
    }
} finally {
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($synth) | Out-Null
}

$scriptPath = Join-Path $audio 'script.txt'
$scriptText = @(
    'Экспорт и отправка дерева — озвучка, версия 2'
    "Голос: $Voice (встроенный голос Windows)"
    ''
) + @($lines | ForEach-Object { ('{0:00.00}  {1}' -f $_.Start, $_.Text) })
[System.IO.File]::WriteAllLines($scriptPath, $scriptText, [System.Text.UTF8Encoding]::new($false))

$ffmpeg = 'C:\Program Files\Wondershare\Recoverit - Data Recovery\ffmpeg.exe'
$ffprobe = 'C:\Program Files\Wondershare\Recoverit - Data Recovery\ffprobe.exe'
$inputs = @()
$filters = @()
$labels = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $wav = Join-Path $segments ('{0:D2}.wav' -f $line.Number)
    $inputs += @('-i', $wav)
    $duration = [double]::Parse((& $ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $wav).Trim(), [System.Globalization.CultureInfo]::InvariantCulture)
    $next = if ($i -lt $lines.Count - 1) { [double]$lines[$i + 1].Start } else { 33.95 }
    $available = $next - [double]$line.Start - 0.10
    if ($duration -gt $available) {
        throw ('Segment {0} lasts {1:N2}s, slot is {2:N2}s' -f $line.Number, $duration, $available)
    }
    $delay = [int][math]::Round(1000 * [double]$line.Start)
    $label = "a$i"
    $filters += "[$($i):a]aresample=48000,highpass=f=95,volume=1.25,afade=t=in:st=0:d=0.04,afade=t=out:st=$([math]::Max(0, $duration - 0.12).ToString('0.000', [System.Globalization.CultureInfo]::InvariantCulture)):d=0.12,adelay=$($delay):all=1[$label]"
    $labels += "[$label]"
}
$filters += ($labels -join '') + "amix=inputs=$($lines.Count):duration=longest:normalize=0,alimiter=limit=0.94,apad=whole_dur=34.32,atrim=duration=34.32,afade=t=out:st=33.95:d=0.37[aout]"
$track = Join-Path $audio '05-export-and-share-voiceover.m4a'
& $ffmpeg -hide_banner -loglevel error -y @inputs -filter_complex ($filters -join ';') -map '[aout]' -c:a aac -b:a 192k $track
if ($LASTEXITCODE -ne 0) { throw 'Audio mix failed.' }

$final = Join-Path $publish '05-export-and-share-voiced.mp4'
& $ffmpeg -hide_banner -loglevel error -y -i $sourceCopy -i $track -map '0:v:0' -map '1:a:0' -c:v copy -c:a copy -shortest -movflags +faststart $final
if ($LASTEXITCODE -ne 0) { throw 'Video mux failed.' }

Write-Output $final
