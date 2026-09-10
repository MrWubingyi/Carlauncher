param([Parameter(Mandatory=$true)][string]$Pcap, [Parameter(Mandatory=$true)][string]$OutputCsv, [switch]$IncludeSd)
$ErrorActionPreference = 'Stop'
function Read-Be16([byte[]]$Data, [int]$Offset) {
    return ([int]$Data[$Offset] * 256 + [int]$Data[$Offset + 1])
}
function Read-Be32([byte[]]$Data, [int]$Offset) {
    return ([long]$Data[$Offset] * 16777216 + [long]$Data[$Offset+1] * 65536 + [long]$Data[$Offset+2] * 256 + $Data[$Offset+3])
}
$stream = [IO.File]::OpenRead((Resolve-Path -LiteralPath $Pcap))
$reader = [IO.BinaryReader]::new($stream)
$rows = [Collections.Generic.List[object]]::new()
try {
    if ($reader.ReadUInt32() -ne 0xa1b2c3d4L) { throw 'Expected little-endian classic PCAP.' }
    $stream.Position = 20
    if ($reader.ReadUInt32() -ne 1) { throw 'Expected Ethernet link type.' }
    while ($stream.Position -lt $stream.Length) {
        $seconds = $reader.ReadUInt32()
        $micros = $reader.ReadUInt32()
        $included = $reader.ReadUInt32()
        $original = $reader.ReadUInt32()
        if ($included -ne $original -or $included -gt ($stream.Length - $stream.Position)) { throw 'Truncated capture.' }
        $packet = $reader.ReadBytes($included)
        if ($packet.Length -lt 54 -or (Read-Be16 $packet 12) -ne 0x0800 -or $packet[23] -ne 17) { throw 'Expected IPv4 UDP.' }
        $ipLength = ($packet[14] -band 15) * 4
        if (((Read-Be16 $packet 20) -band 0x3fff) -ne 0) { throw 'Fragmented IP is outside this probe.' }
        $udp = 14 + $ipLength
        $someip = $udp + 8
        $sourcePort = Read-Be16 $packet $udp
        $destinationPort = Read-Be16 $packet ($udp + 2)
        $udpLength = Read-Be16 $packet ($udp + 4)
        $length = Read-Be32 $packet ($someip + 4)
        if ($udpLength -ne $length + 16 -or $someip + 8 + $length -gt $packet.Length) { throw 'Invalid SOME/IP length.' }
        $payloadLength = $length - 8
        $service = Read-Be16 $packet $someip
        $method = Read-Be16 $packet ($someip+2)
        $isSd = $IncludeSd -and $service -eq 0xffff -and $method -eq 0x8100
        if (-not $isSd -and ($service -ne 0x1111 -or $method -ne 0x1001)) { throw 'Unexpected service or method.' }
        $payload = if ($payloadLength -gt 0) { [BitConverter]::ToString($packet, $someip + 16, $payloadLength).Replace('-', '').ToLowerInvariant() } else { '' }
        $type = $packet[$someip + 14]
        $entries = @()
        $endpoints = @()
        if ($isSd) {
            $base = $someip + 16
            if ($payloadLength -lt 12) { throw 'Short SD header.' }
            $entriesLength = Read-Be32 $packet ($base + 4)
            if ($entriesLength % 16 -ne 0 -or $entriesLength + 12 -gt $payloadLength) { throw 'Invalid SD entries.' }
            for ($entry = $base + 8; $entry -lt $base + 8 + $entriesLength; $entry += 16) {
                $kind = if ($packet[$entry] -eq 0) { 'Find' } elseif ($packet[$entry] -eq 1) { 'Offer' } else { 'Other' }
                $ttl = [long]$packet[$entry+9]*65536 + [long]$packet[$entry+10]*256 + $packet[$entry+11]
                $entries += ('{0}:{1:x4}/{2:x4}:ttl={3}' -f $kind, (Read-Be16 $packet ($entry+4)), (Read-Be16 $packet ($entry+6)), $ttl)
            }
            $optionsLength = Read-Be32 $packet ($base + 8 + $entriesLength)
            if ($entriesLength + $optionsLength + 12 -ne $payloadLength) { throw 'Invalid SD options length.' }
            $optionsEnd = $base + $payloadLength
            for ($option = $base + 12 + $entriesLength; $option -lt $optionsEnd;) {
                $optionLength = (Read-Be16 $packet $option) + 3
                if ($optionLength -lt 3 -or $option + $optionLength -gt $optionsEnd) { throw 'Invalid SD option.' }
                if ($packet[$option+2] -eq 4 -and $optionLength -eq 12) {
                    $address = $packet[($option+4)..($option+7)] -join '.'
                    $endpoints += ('{0}:{1}/proto={2}' -f $address, (Read-Be16 $packet ($option+10)), $packet[$option+9])
                }
                $option += $optionLength
            }
        }
        $rows.Add([pscustomobject]@{
            Time = ('{0}.{1:d6}' -f $seconds, $micros)
            Source = ($packet[26..29] -join '.')
            SourcePort = $sourcePort
            Destination = ($packet[30..33] -join '.')
            DestinationPort = $destinationPort
            Client = ('{0:x4}' -f (Read-Be16 $packet ($someip+8)))
            Session = ('{0:x4}' -f (Read-Be16 $packet ($someip+10)))
            Type = ('{0:x2}' -f $type)
            ReturnCode = ('{0:x2}' -f $packet[$someip+15])
            Seq = $(if ($type -eq 0 -and $payloadLength -eq 16) { Read-Be32 $packet ($someip+17) } else { '' })
            PayloadHex = $payload
            Service = ('{0:x4}' -f $service)
            Method = ('{0:x4}' -f $method)
            SdEntries = ($entries -join ';')
            SdEndpoints = ($endpoints -join ';')
        })
    }
} finally { $reader.Dispose(); $stream.Dispose() }
$rows | Export-Csv -LiteralPath $OutputCsv -NoTypeInformation -Encoding utf8
$requests = @($rows | Where-Object Type -eq '00')
$responses = @($rows | Where-Object Type -eq '80')
$unique = @($requests | Group-Object Source,SourcePort,Destination,DestinationPort,Client,Session,Seq)
foreach ($group in $unique) {
    $request = $group.Group[0]
    $matching = @($responses | Where-Object {
        $_.Source -eq $request.Destination -and $_.SourcePort -eq $request.DestinationPort -and
        $_.Destination -eq $request.Source -and $_.DestinationPort -eq $request.SourcePort -and
        $_.Client -eq $request.Client -and $_.Session -eq $request.Session
    })
    if ($matching.Count -eq 0) { throw "No response to seq $($request.Seq)" }
    $codes = @($matching.ReturnCode | Select-Object -Unique)
    if ($codes.Count -ne 1) { throw 'Conflicting responses for one request.' }
    [pscustomobject]@{ Seq=$request.Seq; Client=$request.Client; Session=$request.Session; Requests=$group.Count; Responses=$matching.Count; ReturnCode=$codes[0] }
}
Write-Output "Packets=$($rows.Count) Requests=$($requests.Count) Responses=$($responses.Count) UniqueRequests=$($unique.Count)"
if ($IncludeSd) {
    $rows | Where-Object Service -eq 'ffff' | Group-Object Source,Destination,SdEntries,SdEndpoints | Select-Object Count,Name
}
