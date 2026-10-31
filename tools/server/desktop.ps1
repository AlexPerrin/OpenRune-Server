# desktop.ps1 start -Title <slug> -JavConfig <url> -Modulus <hex> -GamePort <n> | stop -Instance <name>
# The Windows side of a desktop test client. Instances are the staged dirs C:\osrs-test, C:\osrs-test2 .. C:\osrs-test5
# (JDK, RSProx launcher, RuneLite with the devtools plugin), devtools port 7780 for the first, 7779+N for the Nth.
# start picks the first instance whose ports are free, writes its proxy target, launches it, and prints "<instance> <devtools port>".
param([Parameter(Position = 0)][string]$Verb, [string]$Title, [string]$JavConfig, [string]$Modulus, [int]$GamePort, [string]$Instance)
$ErrorActionPreference = "Stop"

function Dir($i) { if ($i -eq 1) { "C:\osrs-test" } else { "C:\osrs-test$i" } }
function Port($i) { if ($i -eq 1) { 7780 } else { 7779 + $i } }
function Busy($i) { (Get-NetTCPConnection -LocalPort (Port $i) -State Listen -ErrorAction SilentlyContinue) -ne $null -or (Test-Path "$(Dir $i)\rsprox.pid") }

if ($Verb -eq "start") {
  $i = 1..5 | Where-Object { -not (Busy $_) } | Select-Object -First 1
  if (-not $i) { Write-Error "no free desktop instance"; exit 1 }
  $d = Dir $i
  @"
- name: OpenRune Test - $Title
  jav_config_url: $JavConfig
  modulus: $Modulus
  revision: 241.1
  game_server_port: $GamePort
  runelite_bootstrap_commithash: e617bef1a7f5c39fbe8768994d27d638941efd5d
  binary_folder: openrune-qa
"@ | Set-Content -Encoding ASCII "$d\home\.rsprox\proxy-targets.yaml"
  $env:JAVA_TOOL_OPTIONS = "-Duser.home=$d\home -Dosrstest.marker=osrs-test-$i"
  $p = Start-Process -PassThru -FilePath "$d\jdk\bin\java.exe" -WorkingDirectory $d -ArgumentList @("-Dosrstest.marker=osrs-test-$i", "-Dosrstest.portskip=$(20 * ($i - 1))", "-Dosrstest.target=OpenRune Test - $Title", "-cp", "$d\rsprox\launcher;$d\rsprox\lib\*", "OsrsTestLauncher")
  Set-Content "$d\rsprox.pid" $p.Id
  while (-not (Get-NetTCPConnection -LocalPort (Port $i) -State Listen -ErrorAction SilentlyContinue)) {   # the devtools endpoint answers, or the client died
    if ($p.HasExited) { Write-Error "client $i exited"; exit 1 }; Start-Sleep -Seconds 2
  }
  "desktop$i $(Port $i)"
} elseif ($Verb -eq "stop") {
  $i = [int]($Instance -replace "desktop", ""); $d = Dir $i
  Get-Process | Where-Object { $_.Path -and $_.Path.StartsWith("$d\") } | Stop-Process -Force -ErrorAction SilentlyContinue
  Remove-Item "$d\rsprox.pid" -ErrorAction SilentlyContinue
} else { Write-Error "verbs: start, stop"; exit 2 }
