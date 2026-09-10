param([Parameter(Mandatory=$true)][string]$Log)
$ErrorActionPreference = 'Stop'
$records = @(foreach ($line in Get-Content -LiteralPath $Log) {
    if ($line -match 'PROBE_RX client=(\w+) session=(\w+) rc=(\w+) result=(\w+) len=16 duplicate=([01]) applied=(\d+) seq=(\d+).* hex=([0-9a-f]{32})') {
        [pscustomobject]@{ Client=$Matches[1]; Session=$Matches[2]; Code=$Matches[3]; Result=$Matches[4]; Duplicate=[int]$Matches[5]; Applied=[long]$Matches[6]; Seq=$Matches[7]; Payload=$Matches[8] }
    }
})
$groups = @($records | Group-Object Client,Session,Payload)
$replayed = @($records | Where-Object Duplicate -eq 1)
$applied = @($records | Where-Object { $_.Duplicate -eq 0 -and $_.Code -eq '00' })
$maximum = ($records | Measure-Object Applied -Maximum).Maximum
if ($groups.Count -ne 8 -or $applied.Count -ne 4 -or $maximum -ne 4 -or $replayed.Count -lt 1) {
    throw 'Expected eight identities, four accepted state changes and at least one duplicate.'
}
foreach ($group in $groups) {
    $first = @($group.Group | Where-Object Duplicate -eq 0)
    $codes = @($group.Group.Code | Select-Object -Unique)
    if ($first.Count -ne 1 -or $codes.Count -ne 1) { throw 'A replay identity was applied twice or returned conflicting results.' }
}
$expected = @('00','01','01','00','00','01','01','00')
$actual = @($records | Where-Object Duplicate -eq 0 | Sort-Object {[long]$_.Seq} | Select-Object -ExpandProperty Code)
if (($actual -join ',') -ne ($expected -join ',')) { throw 'Unexpected return code sequence.' }
Write-Output "PASS identities=$($groups.Count) received=$($records.Count) duplicate=$($replayed.Count) applied=$maximum codes=$($actual -join ',')"
