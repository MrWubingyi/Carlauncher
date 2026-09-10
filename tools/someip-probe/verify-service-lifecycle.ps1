param([Parameter(Mandatory=$true)][string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$lines = @(Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'android-logcat.txt'))
$packets = @(Import-Csv -LiteralPath (Join-Path $EvidenceDirectory 'packets.csv'))
$server = @(Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'service.log') | Where-Object { $_ -match '^PROBE_RX ' })
function Require([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}
function IndexOfMarker([string]$marker) {
    $indices = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].Contains($marker)) { $i } })
    Require ($indices.Count -eq 1) "Expected one marker: $marker; found $($indices.Count)"
    return $indices[0]
}
$ids = @()
for ($cycle = 0; $cycle -lt 2; $cycle++) {
    $start = IndexOfMarker "cycle=$cycle phase=foreground id="
    Require ($lines[$start] -match 'id=(\d+)$') 'Missing Service identity'
    $serviceId = $Matches[1]
    $ids += $serviceId
    $background = IndexOfMarker "cycle=$cycle phase=background id=$serviceId"
    $rebound = IndexOfMarker "cycle=$cycle phase=rebound id=$serviceId"
    $end = IndexOfMarker "cycle=$cycle phase=stopped passed id=$serviceId"
    $created = IndexOfMarker "Service onCreate id=$serviceId"
    $unbound = IndexOfMarker "Service onUnbind id=$serviceId"
    $destroyed = IndexOfMarker "Service onDestroy id=$serviceId"
    $released = IndexOfMarker "SOMEIP_RELEASED id=$serviceId"
    Require ($created -lt $start -and $start -lt $unbound -and $unbound -lt $background -and
            $background -lt $rebound -and $rebound -lt $destroyed -and $destroyed -lt $released -and $released -lt $end) 'Lifecycle ordering violated'
    $backgroundReplies = @($lines[($background + 1)..($rebound - 1)] | Where-Object { $_ -match 'SOMEIP_RX service=.*rc=00$' }).Count
    Require ($backgroundReplies -ge 5) 'Fewer than five successful Method replies while unbound'
    [pscustomobject]@{ Cycle=$cycle; ServiceId=$serviceId; BackgroundReplies=$backgroundReplies; Releases=1 }
}
Require ($ids[0] -ne $ids[1]) 'Service was not recreated'
Require (@($lines | Where-Object { $_ -match 'vsomeip app stopped$' }).Count -eq 2) 'Expected two native releases'
$tx = @($lines | Where-Object { $_ -match 'SOMEIP_TX submitted ' })
$rx = @($lines | Where-Object { $_ -match 'SOMEIP_RX service=.*rc=00$' })
Require ($tx.Count -gt 0 -and $rx.Count -eq $tx.Count) 'Accepted response count differs from submissions'
$requests = @($packets | Where-Object { $_.Type -eq '00' })
$responses = @($packets | Where-Object { $_.Type -eq '80' -and $_.ReturnCode -eq '00' })
Require ($responses.Count -eq $requests.Count) 'PCAP request/response counts differ'
foreach ($line in $tx) {
    Require ($line -match 'client=(\w+) session=(\w+) seq=\d+ hex=([0-9a-f]+)$') 'Cannot parse native submission'
    $client = $Matches[1]; $session = $Matches[2]; $payload = $Matches[3]
    $wire = @($requests | Where-Object { $_.Client -eq $client -and $_.Session -eq $session -and $_.PayloadHex -eq $payload })
    $applied = @($server | Where-Object { $_.EndsWith("hex=$payload") -and $_ -match 'duplicate=0 ' })
    $replayed = @($server | Where-Object { $_.EndsWith("hex=$payload") -and $_ -match 'duplicate=1 ' })
    Require ($wire.Count -eq 2 -and $applied.Count -eq 1 -and $replayed.Count -eq 1) 'Expected two wire copies, one application and one replay for each native submission'
}
Require (@($requests | Group-Object Client,Session,PayloadHex).Count -eq $tx.Count) 'Unaccounted wire request identity'
Require ($server.Count -eq $requests.Count) 'Server did not log every captured request'
"PASS: cycles=2 nativeRequests=$($tx.Count) acceptedResponses=$($rx.Count) wireRequests=$($requests.Count) wireResponses=$($responses.Count) serverApplications=$($tx.Count)"
